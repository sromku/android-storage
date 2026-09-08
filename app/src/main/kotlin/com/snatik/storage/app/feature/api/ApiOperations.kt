package com.snatik.storage.app.feature.api

import com.snatik.storage.StorageException
import com.snatik.storage.core.apps.AppRepository
import com.snatik.storage.core.apps.ManifestDecoder
import com.snatik.storage.core.capture.SnapshotOptions
import com.snatik.storage.core.capture.SnapshotRepository
import com.snatik.storage.core.data.ProviderQuery
import com.snatik.storage.core.data.QueryRequest
import com.snatik.storage.core.data.ProviderRepository
import com.snatik.storage.core.data.SqliteInspector
import com.snatik.storage.core.data.Tabular
import com.snatik.storage.core.fs.DiskScanner
import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.fs.ScanEvent
import com.snatik.storage.core.intents.IntentSender
import com.snatik.storage.core.intents.IntentSpec
import com.snatik.storage.core.intents.SendAs
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.flow.last
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The operations behind both the REST and MCP surfaces, so the two never drift. Each takes a
 * parameter object and returns JSON. Gated operations check [config] and throw [ApiException].
 */
class ApiOperations(
    private val appContext: android.content.Context,
    private val fs: FileSystem,
    private val diskScanner: DiskScanner,
    private val apps: AppRepository,
    private val manifests: ManifestDecoder,
    private val providerRepo: ProviderRepository,
    private val providers: ProviderQuery,
    private val sqlite: SqliteInspector,
    private val intents: IntentSender,
    private val snapshots: SnapshotRepository,
    private val privilege: PrivilegeManager,
    private val config: ApiConfig,
) {
    class Op(val name: String, val description: String, val privileged: Boolean, val destructive: Boolean, val schema: JsonObject, val run: suspend (JsonObject) -> JsonElement)

    private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.content ?: throw ApiException.badRequest("Missing '$key'")
    private fun JsonObject.strOrNull(key: String): String? = this[key]?.jsonPrimitive?.content
    private fun JsonObject.intOr(key: String, default: Int): Int = this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: default
    private fun JsonObject.longOr(key: String, default: Long): Long = this[key]?.jsonPrimitive?.content?.toLongOrNull() ?: default
    private fun JsonObject.boolOr(key: String, default: Boolean): Boolean = this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default
    private fun JsonObject.list(key: String): List<String> = this[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

    private fun gate(op: Op) {
        val s = config.settings.value
        if (op.privileged && !s.allowPrivileged) throw ApiException.forbidden("Privileged operations are off; enable them in the app")
        if (op.privileged && privilege.executor.value == null) throw ApiException.forbidden("No shell access; connect Shizuku or root")
        if (op.destructive && !s.allowDestructive) throw ApiException.forbidden("Changing the device is off; enable it in the app")
    }

    suspend fun call(name: String, params: JsonObject): JsonElement {
        val op = operations[name] ?: throw ApiException.notFound("Unknown operation '$name'")
        gate(op)
        return try {
            op.run(params)
        } catch (e: ApiException) {
            throw e
        } catch (e: StorageException.NotFound) {
            throw ApiException.notFound(e.message ?: "Not found")
        } catch (e: Exception) {
            throw ApiException.failed(e.message ?: e.toString())
        }
    }

    fun opFor(name: String): Op? = operations[name]
    val all: List<Op> get() = operations.values.toList()

    private fun tabularToJson(t: Tabular): JsonObject = buildJsonObject {
        put("columns", buildJsonArray { t.columns.forEach { add(it) } })
        put("rows", buildJsonArray { t.rows.forEach { row -> add(buildJsonArray { row.forEach { add(it ?: "") } }) } })
        put("truncated", t.truncated)
        put("source", t.source.name.lowercase())
    }

    private val operations: Map<String, Op> = listOf(
        Op("list_dir", "List a directory", false, false, schemaOf("path" to "string")) { p ->
            buildJsonArray {
                fs.list(p.str("path")).forEach { e ->
                    add(buildJsonObject { put("name", e.name); put("path", e.path); put("isDirectory", e.isDirectory); put("size", e.size); put("lastModified", e.lastModified); put("mimeType", e.mimeType) })
                }
            }
        },
        Op("stat", "Metadata for one path", false, false, schemaOf("path" to "string")) { p ->
            val e = fs.stat(p.str("path")) ?: throw ApiException.notFound("Not found: ${p.str("path")}")
            buildJsonObject { put("name", e.name); put("path", e.path); put("isDirectory", e.isDirectory); put("size", e.size); put("lastModified", e.lastModified); put("canRead", e.canRead); put("canWrite", e.canWrite); put("mimeType", e.mimeType) }
        },
        Op("read_file", "Read a text file (utf-8), optional byte offset and max length", false, false, schemaOf("path" to "string", "offset" to "integer", "maxLength" to "integer")) { p ->
            val bytes = fs.readBytes(p.str("path"), p.longOr("offset", 0), p.intOr("maxLength", 256 * 1024))
            buildJsonObject { put("path", p.str("path")); put("bytes", bytes.size); put("text", String(bytes, Charsets.UTF_8)) }
        },
        Op("write_file", "Write a text file, replacing it", false, true, schemaOf("path" to "string", "content" to "string")) { p ->
            fs.writeText(p.str("path"), p.str("content"))
            buildJsonObject { put("path", p.str("path")); put("written", true) }
        },
        Op("disk_usage", "Scan a folder for its size and biggest children and files", false, false, schemaOf("path" to "string")) { p ->
            val done = diskScanner.scan(p.str("path"), largestCount = 20).last()
            val event = done as? ScanEvent.Done ?: throw ApiException.failed("Scan did not finish")
            buildJsonObject {
                put("path", event.root.path); put("totalBytes", event.root.size); put("fileCount", event.root.fileCount)
                put("children", buildJsonArray { event.root.children.take(30).forEach { c -> add(buildJsonObject { put("name", c.name); put("size", c.size) }) } })
                put("largest", buildJsonArray { event.largest.forEach { f -> add(buildJsonObject { put("path", f.path); put("size", f.size) }) } })
            }
        },
        Op("apps", "List installed apps with sizes (needs usage access)", false, false, schemaOf("filter" to "string")) { p ->
            val filter = p.strOrNull("filter")
            buildJsonArray {
                apps.list().filter { s -> when (filter) { "user" -> !s.isSystem; "system" -> s.isSystem; "debuggable" -> s.isDebuggable; else -> true } }.forEach { s ->
                    add(buildJsonObject { put("package", s.packageName); put("label", s.label); put("versionName", s.versionName ?: ""); put("system", s.isSystem); put("debuggable", s.isDebuggable); put("totalBytes", s.storage?.totalBytes ?: -1) })
                }
            }
        },
        Op("app_info", "Details of one package", false, false, schemaOf("package" to "string")) { p ->
            val d = apps.details(p.str("package")) ?: throw ApiException.notFound("Not installed: ${p.str("package")}")
            buildJsonObject {
                put("package", d.summary.packageName); put("label", d.summary.label); put("versionName", d.summary.versionName ?: ""); put("versionCode", d.summary.versionCode)
                put("targetSdk", d.summary.targetSdk); put("minSdk", d.minSdk); put("uid", d.summary.uid); put("apkPath", d.summary.apkPath); put("dataDir", d.dataDir)
                put("permissions", buildJsonArray { d.permissions.forEach { add(buildJsonObject { put("name", it.name); put("granted", it.granted) }) } })
                put("activities", buildJsonArray { d.activities.forEach { add(it.name) } })
                put("providers", buildJsonArray { d.providers.forEach { add(buildJsonObject { put("authority", it.authority); put("exported", it.exported) }) } })
                put("signatures", buildJsonArray { d.signatures.forEach { add(it.sha256) } })
            }
        },
        Op("manifest", "The decoded AndroidManifest.xml of a package", false, false, schemaOf("package" to "string")) { p ->
            val pkg = p.str("package")
            val apk = apps.applicationInfo(pkg)?.publicSourceDir ?: throw ApiException.notFound("Not installed: $pkg")
            buildJsonObject { put("package", pkg); put("xml", manifests.decode(pkg, apk)) }
        },
        Op("providers", "List content providers on the device", false, false, schemaOf()) { _ ->
            buildJsonArray {
                providerRepo.list().forEach { e ->
                    add(buildJsonObject { put("authority", e.authority); put("package", e.packageName); put("exported", e.exported); put("readPermission", e.readPermission ?: ""); put("writePermission", e.writePermission ?: "") })
                }
            }
        },
        Op("query_provider", "Query a content:// uri", false, false, schemaOf("uri" to "string", "projection" to "array", "selection" to "string", "limit" to "integer")) { p ->
            tabularToJson(providers.query(QueryRequest(uri = p.str("uri"), projection = p.list("projection"), selection = p.strOrNull("selection"), limit = p.intOr("limit", 100))))
        },
        Op("sql", "Run SQL against a SQLite file", false, false, schemaOf("path" to "string", "query" to "string")) { p ->
            sqlite.open(p.str("path"), writable = config.settings.value.allowDestructive).use { db -> tabularToJson(db.query(p.str("query"))) }
        },
        Op("send_intent", "Send an intent as activity, broadcast or service", true, true, schemaOf("action" to "string", "data" to "string", "type" to "string", "package" to "string", "sendAs" to "string")) { p ->
            val kind = when (p.strOrNull("sendAs")?.uppercase()) { "BROADCAST" -> SendAs.BROADCAST; "SERVICE" -> SendAs.SERVICE; else -> SendAs.ACTIVITY }
            val result = intents.send(IntentSpec(action = p.strOrNull("action"), data = p.strOrNull("data"), type = p.strOrNull("type"), packageName = p.strOrNull("package"), sendAs = kind))
            buildJsonObject { put("sent", result) }
        },
        Op("logcat", "Recent logcat lines (shell)", true, false, schemaOf("lines" to "integer", "filter" to "string", "package" to "string")) { p ->
            val shell = privilege.executor.value ?: throw ApiException.forbidden("No shell")
            val n = p.intOr("lines", 200).coerceIn(1, 5000)
            val uid = p.strOrNull("package")?.let { runCatching { appContext.packageManager.getApplicationInfo(it, 0).uid }.getOrNull() }
            val command = buildString { append("logcat -d -v threadtime -t $n"); uid?.let { append(" --uid=$it") }; p.strOrNull("filter")?.takeIf { it.isNotBlank() }?.let { append(" $it") } }
            val result = shell.run(command, timeoutMs = 30_000)
            buildJsonObject { put("lines", result.out) }
        },
        Op("snapshot", "Snapshot a folder", false, false, schemaOf("path" to "string", "label" to "string", "hash" to "boolean", "copies" to "boolean")) { p ->
            val event = snapshots.create(p.str("path"), p.strOrNull("label") ?: p.str("path").substringAfterLast('/'), SnapshotOptions(p.boolOr("hash", false), p.boolOr("copies", false))).last()
            val id = (event as? com.snatik.storage.core.capture.SnapshotEvent.Done)?.id ?: throw ApiException.failed("Snapshot did not finish")
            buildJsonObject { put("id", id) }
        },
        Op("diff", "Compare two snapshots by id", false, false, schemaOf("a" to "integer", "b" to "integer")) { p ->
            val diff = snapshots.diff(p.longOr("a", -1), p.longOr("b", -1))
            buildJsonObject {
                put("unchanged", diff.unchanged)
                put("changes", buildJsonArray { diff.changes.forEach { c -> add(buildJsonObject { put("kind", c.kind.name.lowercase()); put("path", c.path); put("sizeDelta", c.sizeDelta) }) } })
            }
        },
        Op("shell", "Run a shell command (shell or root)", true, true, schemaOf("command" to "string", "timeoutMs" to "integer")) { p ->
            val shell = privilege.executor.value ?: throw ApiException.forbidden("No shell")
            val result = shell.run(p.str("command"), timeoutMs = p.longOr("timeoutMs", 60_000))
            buildJsonObject { put("exitCode", result.exitCode); put("stdout", result.out); put("stderr", result.err) }
        },
    ).associateBy { it.name }

    private fun schemaOf(vararg props: Pair<String, String>): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { props.forEach { (name, type) -> put(name, buildJsonObject { put("type", type) }) } })
    }
}
