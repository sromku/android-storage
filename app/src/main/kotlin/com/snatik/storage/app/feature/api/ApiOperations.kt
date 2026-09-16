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
import kotlinx.coroutines.flow.first
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
    private val deviceStats: com.snatik.storage.core.apps.DeviceStatsRepository,
    private val permissionMatrix: com.snatik.storage.core.apps.PermissionMatrixRepository,
    private val appOpsTimeline: com.snatik.storage.core.apps.AppOpsTimeline,
    private val network: com.snatik.storage.core.apps.NetworkInspector,
    private val storageInsights: com.snatik.storage.core.apps.StorageInsights,
    private val appStorage: com.snatik.storage.core.apps.AppStorageAnalyzer,
    private val elf: com.snatik.storage.core.apps.ElfInspector,
    private val system: com.snatik.storage.core.apps.SystemInspector,
    private val telemetry: com.snatik.storage.core.apps.TelemetryRepository,
    private val search: com.snatik.storage.core.apps.FileSearch,
    private val notifStore: com.snatik.storage.core.apps.NotificationRecorderStore,
    private val appOpsStore: com.snatik.storage.core.apps.AppOpsRecorderStore,
    private val providerStore: com.snatik.storage.core.apps.ProviderRecorderStore,
    private val processes: com.snatik.storage.core.apps.ProcessInspector,
    private val appEvents: com.snatik.storage.core.apps.AppEventLog,
    private val appActions: com.snatik.storage.core.apps.AppActions,
    private val broadcastStore: com.snatik.storage.core.intents.BroadcastStore,
    private val intentStore: com.snatik.storage.core.intents.IntentMonitorStore,
) {
    private val clipboardStore by lazy { com.snatik.storage.core.apps.ClipboardStore(appContext) }
    private fun ok(message: String): JsonElement = buildJsonObject { put("ok", true); put("message", message) }
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
        Op("device_stats", "Device health: model, OS, patch, kernel, SELinux, uptime, RAM, battery, ZRAM, wakelocks", false, false, schemaOf()) { _ ->
            val s = deviceStats.stats()
            buildJsonObject {
                put("model", s.model); put("androidRelease", s.androidRelease); put("sdk", s.sdk); put("securityPatch", s.securityPatch)
                put("buildId", s.buildId); put("kernel", s.kernel); put("selinux", s.selinux); put("uptimeMs", s.uptimeMs)
                put("totalRamBytes", s.totalRamBytes); put("availRamBytes", s.availRamBytes)
                put("batteryPercent", s.batteryPercent); put("batteryStatus", s.batteryStatus); put("batteryTempC", s.batteryTempC)
                put("zramTotalBytes", s.zramTotalBytes); put("zramUsedBytes", s.zramUsedBytes)
                put("wakelocks", buildJsonArray { s.wakelocks.take(20).forEach { w -> add(buildJsonObject { put("name", w.name); put("heldMs", w.heldMs); put("count", w.count) }) } })
            }
        },
        Op("permission_matrix", "Every app against the dangerous permissions, granted vs requested", false, false, schemaOf("includeSystem" to "boolean")) { p ->
            val m = permissionMatrix.matrix(p.boolOr("includeSystem", false))
            buildJsonObject {
                put("permissions", buildJsonArray { m.permissions.forEach { add(it) } })
                put("apps", buildJsonArray { m.apps.forEach { a -> add(buildJsonObject { put("package", a.packageName); put("label", a.label); put("system", a.system); put("granted", buildJsonArray { a.granted.forEach { add(it) } }); put("requested", buildJsonArray { a.requested.forEach { add(it) } }) }) } })
            }
        },
        Op("app_ops_timeline", "Recent sensitive app-ops access device-wide (shell)", true, false, schemaOf("limit" to "integer")) { p ->
            buildJsonArray {
                appOpsTimeline.recent(p.intOr("limit", 300)).forEach { e ->
                    add(buildJsonObject { put("package", e.packageName); put("label", e.label); put("op", e.op); put("agoMs", e.agoMs); put("sensitive", e.sensitive) })
                }
            }
        },
        Op("network", "Live network connections per app from /proc/net (shell)", true, false, schemaOf()) { _ ->
            buildJsonArray {
                network.connections().forEach { a ->
                    add(buildJsonObject {
                        put("uid", a.uid); put("package", a.packageName); put("label", a.label)
                        put("rxBytes", a.rxBytes); put("txBytes", a.txBytes)
                        put("remoteHosts", buildJsonArray { a.remoteHosts.take(20).forEach { add(it) } })
                        put("connections", a.connections.size)
                    })
                }
            }
        },
        Op("storage_insights", "Find duplicates, empty dirs, zero-byte files and ghost footprints under a root", false, false, schemaOf("root" to "string")) { p ->
            val done = storageInsights.scan(p.strOrNull("root") ?: "/storage/emulated/0").last()
            val report = (done as? com.snatik.storage.core.apps.InsightsEvent.Done)?.report ?: throw ApiException.failed("Scan did not finish")
            buildJsonObject {
                put("totalFiles", report.totalFiles); put("totalBytes", report.totalBytes); put("wastedByDuplicates", report.wastedByDuplicates)
                put("duplicateSets", buildJsonArray { report.duplicateSets.take(50).forEach { d -> add(buildJsonObject { put("size", d.size); put("wasted", d.wasted); put("paths", buildJsonArray { d.paths.forEach { add(it) } }) }) } })
                put("zeroByteFiles", buildJsonArray { report.zeroByteFiles.take(100).forEach { add(it) } })
                put("emptyDirs", buildJsonArray { report.emptyDirs.take(100).forEach { add(it) } })
                put("categories", buildJsonArray { report.categories.forEach { c -> add(buildJsonObject { put("category", c.category.name); put("bytes", c.bytes); put("count", c.count) }) } })
                put("largest", buildJsonArray { report.largest.take(50).forEach { l -> add(buildJsonObject { put("path", l.path); put("size", l.size) }) } })
            }
        },
        Op("app_storage", "Decompose an app's footprint into apk/splits/oat/lib/data/cache", false, false, schemaOf("package" to "string")) { p ->
            val fp = appStorage.analyze(p.str("package")) ?: throw ApiException.notFound("Not installed: ${p.str("package")}")
            buildJsonObject {
                put("package", fp.packageName); put("totalBytes", fp.total)
                put("slices", buildJsonArray { fp.slices.forEach { s -> add(buildJsonObject { put("label", s.label); put("bytes", s.bytes); put("path", s.path) }) } })
            }
        },
        Op("elf_inspect", "Parse an ELF binary: header, sections+entropy, needed libs, packer signatures", false, false, schemaOf("path" to "string")) { p ->
            val r = elf.inspect(p.str("path")) ?: throw ApiException.badRequest("Not an ELF file")
            buildJsonObject {
                put("is64Bit", r.is64Bit); put("machine", r.machine); put("type", r.typeName); put("stripped", r.stripped)
                put("soname", r.soname ?: ""); put("buildId", r.buildId ?: ""); put("entropy", r.overallEntropy); put("packer", r.packer ?: "")
                put("needed", buildJsonArray { r.needed.forEach { add(it) } })
                put("sections", buildJsonArray { r.sections.take(50).forEach { sec -> add(buildJsonObject { put("name", sec.name); put("size", sec.size); put("entropy", sec.entropy) }) } })
            }
        },
        Op("system_report", "Kernel view: device/kernel, CPU, memory, block devices, swap, ZRAM, mounts", false, false, schemaOf()) { _ ->
            val r = system.report()
            buildJsonObject {
                put("device", buildJsonObject { put("model", r.device.model); put("kernel", r.device.kernel); put("android", r.device.androidVersion); put("selinux", r.device.selinux); put("uptimeSec", r.device.uptimeSec); put("load1", r.device.load1) })
                put("cpu", buildJsonObject { put("model", r.cpu.model); put("cores", r.cpu.cores); put("usagePercent", r.cpu.usagePercent ?: -1.0) })
                put("memory", buildJsonObject { put("totalKb", r.mem.totalKb); put("availableKb", r.mem.availableKb); put("cachedKb", r.mem.cachedKb) })
                put("mounts", buildJsonArray { r.mounts.take(200).forEach { m -> add(buildJsonObject { put("mountPoint", m.mountPoint); put("device", m.device); put("type", m.type); put("totalBytes", m.totalBytes); put("freeBytes", m.freeBytes) }) } })
                put("blocks", buildJsonArray { r.blocks.forEach { b -> add(buildJsonObject { put("name", b.name); put("model", b.model); put("sizeBytes", b.sizeBytes); put("rotational", b.rotational) }) } })
                put("partitions", buildJsonArray { r.partitions.forEach { pt -> add(buildJsonObject { put("name", pt.name); put("bytes", pt.bytes) }) } })
                put("swaps", buildJsonArray { r.swaps.forEach { sw -> add(buildJsonObject { put("name", sw.name); put("sizeKb", sw.sizeKb); put("usedKb", sw.usedKb) }) } })
                r.zram?.let { z -> put("zram", buildJsonObject { put("disksizeBytes", z.disksizeBytes); put("originalBytes", z.originalBytes); put("compressedBytes", z.compressedBytes) }) }
                r.appMem?.let { a -> put("appMemory", buildJsonObject { put("totalPssKb", a.totalPssKb); put("javaHeapKb", a.javaHeapKb); put("nativeHeapKb", a.nativeHeapKb) }) }
                // Prefer the kernel /sys reading; fall back to the framework battery (always available)
                // so this is never null even before the shell is connected.
                val bat = r.battery
                if (bat != null) put("battery", buildJsonObject { put("percent", bat.percent); put("status", bat.status); put("health", bat.health); put("tempC", bat.tempC.toDouble()); put("technology", bat.technology); put("voltageMv", bat.voltageMv); put("cycleCount", bat.cycleCount) })
                else runCatching { deviceStats.stats() }.getOrNull()?.let { ds -> put("battery", buildJsonObject { put("percent", ds.batteryPercent); put("status", ds.batteryStatus); put("tempC", ds.batteryTempC.toDouble()) }) }
            }
        },
        Op("telemetry_forecast", "Storage growth trend and a forecast of when free space runs out", false, false, schemaOf()) { _ ->
            val r = telemetry.report()
            buildJsonObject {
                put("snapshots", r.snapshots); put("cacheBytesPerDay", r.cacheBytesPerDay)
                r.forecast?.let { f -> put("forecast", buildJsonObject { put("bytesPerDay", f.bytesPerDay); put("daysUntilFull", f.daysUntilFull ?: -1.0); put("confident", f.confident) }) }
                put("topGrowers", buildJsonArray { r.topGrowers.forEach { g -> add(buildJsonObject { put("package", g.packageName); put("label", g.label); put("deltaBytes", g.deltaBytes); put("versionChanged", g.versionChanged) }) } })
            }
        },
        Op("capture_telemetry", "Record one storage/memory telemetry snapshot now", false, false, schemaOf()) { _ ->
            telemetry.capture()
            buildJsonObject { put("captured", true) }
        },
        Op("search_files", "Find files by name or content under a root", false, false, schemaOf("root" to "string", "query" to "string", "mode" to "string")) { p ->
            val mode = if (p.strOrNull("mode")?.lowercase() == "content") com.snatik.storage.core.apps.SearchMode.CONTENT else com.snatik.storage.core.apps.SearchMode.NAME
            val hits = ArrayList<com.snatik.storage.core.apps.SearchHit>()
            search.search(p.strOrNull("root") ?: "/storage/emulated/0", p.str("query"), mode, limit = 300).collect { hits.add(it) }
            buildJsonArray { hits.forEach { h -> add(buildJsonObject { put("path", h.path); put("size", h.size); h.line?.let { put("line", it) } }) } }
        },
        Op("recent_notifications", "Notifications the recorder captured (needs the Notification monitor recording)", false, false, schemaOf("limit" to "integer")) { p ->
            buildJsonArray { notifStore.snapshot(p.intOr("limit", 100)).forEach { n -> add(buildJsonObject {
                put("package", n.packageName); put("app", n.label); put("title", n.title); put("text", n.text)
                put("category", n.category ?: ""); put("channel", n.channelId); put("postedAt", n.postedAt); put("ongoing", n.ongoing)
                if (n.subText.isNotBlank()) put("subText", n.subText); if (n.bigText.isNotBlank()) put("bigText", n.bigText)
            }) } }
        },
        Op("recent_appops", "Recorded sensitive app-op accesses (camera/mic/location) with foreground/background state", false, false, schemaOf("limit" to "integer")) { p ->
            buildJsonArray { appOpsStore.snapshot(p.intOr("limit", 200)).forEach { a -> add(buildJsonObject {
                put("package", a.packageName); put("app", a.label); put("op", a.op); put("time", a.absTime); put("agoMs", a.agoMs)
                put("state", a.state.name); put("background", a.background); put("sensitive", a.sensitive); put("denied", a.denied)
                a.durationMs?.let { put("durationMs", it) }
            }) } }
        },
        Op("provider_changes", "Content-provider changes the watcher recorded (INSERT/UPDATE/DELETE with the URI)", false, false, schemaOf("limit" to "integer")) { p ->
            buildJsonArray { providerStore.snapshot(p.intOr("limit", 200)).forEach { c -> add(buildJsonObject {
                put("target", c.target); put("uri", c.uri); put("op", c.op); put("time", c.atMs)
            }) } }
        },
        Op("clipboard_history", "Clips the clipboard monitor captured (text/html/uris, sensitivity)", false, false, schemaOf("limit" to "integer")) { p ->
            buildJsonArray { clipboardStore.snapshot(p.intOr("limit", 100)).forEach { c -> add(buildJsonObject {
                put("label", c.label); put("text", c.text); if (c.html.isNotBlank()) put("html", c.html)
                put("uris", buildJsonArray { c.uris.forEach { add(it) } }); put("mimeTypes", buildJsonArray { c.mimeTypes.forEach { add(it) } })
                put("itemCount", c.itemCount); put("sensitive", c.sensitive); put("copiedAt", c.copiedAt); put("capturedAt", c.capturedAt)
            }) } }
        },
        Op("processes", "Live processes ranked by CPU or memory (top consumers, per-app)", true, false, schemaOf("sort" to "string", "limit" to "integer")) { p ->
            val byMem = p.strOrNull("sort")?.lowercase()?.startsWith("mem") == true
            val list = processes.processes().let { l -> if (byMem) l.sortedByDescending { it.rssKb } else l.sortedByDescending { it.cpuPercent } }.take(p.intOr("limit", 30))
            buildJsonArray { list.forEach { s -> add(buildJsonObject {
                put("pid", s.pid); put("name", s.name); put("user", s.user); put("cpuPercent", s.cpuPercent); put("rssKb", s.rssKb)
            }) } }
        },
        Op("wakelocks", "Top kernel/app wake locks holding the CPU awake (from batterystats)", true, false, schemaOf()) { _ ->
            buildJsonArray { system.wakelocks().forEach { w -> add(buildJsonObject { put("name", w.name); put("heldMs", w.heldMs); put("count", w.count) }) } }
        },
        Op("app_history", "Install / update / uninstall events over time", false, false, schemaOf("limit" to "integer")) { p ->
            buildJsonArray { appEvents.history().sortedByDescending { it.ts }.take(p.intOr("limit", 200)).forEach { e -> add(buildJsonObject {
                put("package", e.packageName); put("app", e.label); put("type", e.type); put("time", e.ts)
                put("version", e.versionName ?: ""); put("versionCode", e.versionCode); put("system", e.system)
                e.fromVersionName?.let { put("fromVersion", it) }
            }) } }
        },
        Op("telemetry_snapshots", "Recorded storage/RAM snapshots over time (from Time Machine)", false, false, schemaOf("limit" to "integer")) { p ->
            buildJsonArray { telemetry.snapshots().take(p.intOr("limit", 100)).forEach { s -> add(buildJsonObject {
                put("time", s.ts); put("freeBytes", s.freeBytes); put("totalBytes", s.totalBytes)
                put("ramFreeBytes", s.ramFreeBytes); put("ramTotalBytes", s.ramTotalBytes)
                put("appCount", s.appCount); put("appTotalBytes", s.appTotalBytes)
                s.freeDeltaBytes?.let { put("freeDeltaBytes", it) }
            }) } }
        },
        /* ---------- file actions ---------- */
        Op("delete_file", "Delete a file or folder", false, true, schemaOf("path" to "string")) { p ->
            fs.delete(listOf(p.str("path"))).last(); ok("deleted ${p.str("path")}")
        },
        Op("copy_file", "Copy a file/folder into a destination directory", false, true, schemaOf("source" to "string", "destinationDir" to "string")) { p ->
            fs.copy(listOf(p.str("source")), p.str("destinationDir")).last(); ok("copied ${p.str("source")} into ${p.str("destinationDir")}")
        },
        Op("move_file", "Move a file/folder into a destination directory", false, true, schemaOf("source" to "string", "destinationDir" to "string")) { p ->
            fs.move(listOf(p.str("source")), p.str("destinationDir")).last(); ok("moved ${p.str("source")} into ${p.str("destinationDir")}")
        },
        Op("rename_file", "Rename a file/folder", false, true, schemaOf("path" to "string", "newName" to "string")) { p ->
            val e = fs.rename(p.str("path"), p.str("newName")); buildJsonObject { put("path", e.path) }
        },
        Op("make_dir", "Create a directory", false, true, schemaOf("path" to "string")) { p ->
            val e = fs.createDirectory(p.str("path")); ok("created ${e.path}")
        },
        /* ---------- app actions (shell + changes) ---------- */
        Op("force_stop", "Force-stop an app", true, true, schemaOf("package" to "string")) { p -> appActions.forceStop(p.str("package")); ok("force-stopped ${p.str("package")}") },
        Op("clear_cache", "Clear an app's cache", true, true, schemaOf("package" to "string")) { p -> appActions.clearCache(p.str("package")); ok("cleared cache of ${p.str("package")}") },
        Op("clear_data", "Clear an app's data (irreversible)", true, true, schemaOf("package" to "string")) { p -> appActions.clearData(p.str("package")); ok("cleared data of ${p.str("package")}") },
        Op("uninstall", "Uninstall an app", true, true, schemaOf("package" to "string")) { p -> appActions.uninstall(p.str("package")); ok("uninstalled ${p.str("package")}") },
        Op("set_app_enabled", "Enable or disable an app", true, true, schemaOf("package" to "string", "enabled" to "boolean")) { p ->
            val en = p.boolOr("enabled", true); appActions.setEnabled(p.str("package"), en); ok("${p.str("package")} enabled=$en")
        },
        Op("dexopt", "Recompile an app (mode: speed-profile | speed | everything | verify | reset)", true, true, schemaOf("package" to "string", "mode" to "string")) { p ->
            val mode = when (p.strOrNull("mode")?.lowercase()?.replace('-', '_')) {
                "speed" -> com.snatik.storage.core.apps.CompileMode.SPEED
                "everything" -> com.snatik.storage.core.apps.CompileMode.EVERYTHING
                "verify" -> com.snatik.storage.core.apps.CompileMode.VERIFY
                "reset" -> com.snatik.storage.core.apps.CompileMode.RESET
                else -> com.snatik.storage.core.apps.CompileMode.SPEED_PROFILE
            }
            buildJsonObject { put("result", appActions.compile(p.str("package"), mode)) }
        },
        /* ---------- permissions ---------- */
        Op("set_permission", "Grant or revoke a runtime permission", true, true, schemaOf("package" to "string", "permission" to "string", "grant" to "boolean")) { p ->
            val grant = p.boolOr("grant", true); appActions.grantPermission(p.str("package"), p.str("permission"), grant)
            ok("${if (grant) "granted" else "revoked"} ${p.str("permission")} for ${p.str("package")}")
        },
        /* ---------- clipboard ---------- */
        Op("set_clipboard", "Put text on the clipboard", false, true, schemaOf("text" to "string")) { p ->
            appContext.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("agent", p.str("text"))); ok("clipboard set")
        },
        Op("clear_clipboard", "Clear the clipboard", false, true, schemaOf()) { _ ->
            appContext.getSystemService(android.content.ClipboardManager::class.java).clearPrimaryClip(); ok("clipboard cleared")
        },
        /* ---------- monitor control ---------- */
        Op("set_monitor", "Start or stop a background recorder (appops, providers, broadcasts, intents, telemetry)", false, true, schemaOf("monitor" to "string", "on" to "boolean")) { p ->
            val on = p.boolOr("on", true); val m = p.str("monitor").lowercase()
            when (m) {
                "appops" -> if (on) com.snatik.storage.app.feature.dashboard.AppOpsRecorderService.start(appContext) else com.snatik.storage.app.feature.dashboard.AppOpsRecorderService.stop(appContext)
                "providers" -> if (on) com.snatik.storage.app.feature.monitor.ProviderRecorderService.start(appContext) else com.snatik.storage.app.feature.monitor.ProviderRecorderService.stop(appContext)
                "broadcasts" -> if (on) com.snatik.storage.app.feature.intents.BroadcastMonitorService.start(appContext) else com.snatik.storage.app.feature.intents.BroadcastMonitorService.stop(appContext)
                "intents" -> if (on) com.snatik.storage.app.feature.intents.IntentMonitorService.start(appContext) else com.snatik.storage.app.feature.intents.IntentMonitorService.stop(appContext)
                "telemetry" -> if (on) com.snatik.storage.app.feature.timemachine.TelemetryRecorderService.start(appContext) else com.snatik.storage.app.feature.timemachine.TelemetryRecorderService.stop(appContext)
                else -> throw ApiException.badRequest("Unknown monitor '$m' (use: appops, providers, broadcasts, intents, telemetry)")
            }
            ok("$m ${if (on) "started" else "stopped"}")
        },
        /* ---------- monitor read feeds ---------- */
        Op("broadcast_log", "Broadcasts the monitor recorded (needs the Broadcast monitor recording)", false, false, schemaOf("limit" to "integer")) { p ->
            buildJsonArray { broadcastStore.recent.first().take(p.intOr("limit", 100)).forEach { b -> add(buildJsonObject { put("time", b.time); put("action", b.spec.action ?: ""); put("data", b.spec.data ?: "") }) } }
        },
        Op("intent_log", "Intents the monitor recorded (needs the Intent monitor recording)", false, false, schemaOf("limit" to "integer")) { p ->
            buildJsonArray { intentStore.recent.first().take(p.intOr("limit", 100)).forEach { i -> add(buildJsonObject { put("time", i.time); put("action", i.action ?: ""); put("data", i.data ?: ""); put("package", i.packageName ?: "") }) } }
        },
    ).associateBy { it.name }

    private fun schemaOf(vararg props: Pair<String, String>): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { props.forEach { (name, type) -> put(name, buildJsonObject { put("type", type) }) } })
    }
}
