package com.snatik.storage.core.net

import com.snatik.storage.core.fs.FileSystem
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.net.NetworkInterface
import java.security.SecureRandom

@Serializable
data class ServerInfo(val name: String, val app: String, val version: String, val capabilities: List<String>)

@Serializable
data class RemoteEntry(val name: String, val path: String, val isDirectory: Boolean, val size: Long, val lastModified: Long)

@Serializable
data class UploadStatus(val name: String, val size: Long)

data class ReceivedFile(val path: String, val size: Long, val from: String, val time: Long)

data class ServerState(
    val running: Boolean = false,
    val port: Int = DEFAULT_PORT,
    val lanOnly: Boolean = false,
    val addresses: List<String> = emptyList(),
    val code: String = "",
    val received: List<ReceivedFile> = emptyList(),
    val error: String? = null,
) {
    val urls: List<String> get() = addresses.map { "http://$it:$port/?code=$code" }
}

const val DEFAULT_PORT = 8484

/**
 * The device's HTTP server. Serves the browser receive page, resumable uploads, listings and
 * downloads. Everything but `/api/info` needs the pairing [ServerState.code], as a bearer token,
 * an `X-Code` header or a `code` query parameter. Other route groups plug in through [extraRoutes].
 */
class TransferServer(
    private val fs: FileSystem,
    private val inboxDir: File,
    private val deviceName: String,
    private val appVersion: String,
    private val extraRoutes: Route.(server: TransferServer) -> Unit = {},
) {
    private val _state = MutableStateFlow(ServerState(code = newCode()))
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private var engine: EmbeddedServer<*, *>? = null

    val code: String get() = _state.value.code

    fun regenerateCode() = _state.update { it.copy(code = newCode()) }

    suspend fun start(port: Int = DEFAULT_PORT, bindAll: Boolean = true) = withContext(Dispatchers.IO) {
        stop()
        try {
            val server = embeddedServer(CIO, port = port, host = if (bindAll) "0.0.0.0" else "127.0.0.1") { module(this) }
            server.start(wait = false)
            engine = server
            _state.update { it.copy(running = true, port = port, lanOnly = !bindAll, addresses = if (bindAll) lanAddresses() else listOf("127.0.0.1"), error = null) }
        } catch (e: Exception) {
            _state.update { it.copy(running = false, error = e.message ?: e.toString()) }
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        engine?.stop(500, 1000)
        engine = null
        _state.update { it.copy(running = false, addresses = emptyList()) }
    }

    /** Whether [call] presented the pairing code. */
    fun authorized(call: ApplicationCall): Boolean {
        val expected = code
        val bearer = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        val header = call.request.header("X-Code")
        val query = call.request.queryParameters["code"]
        return expected.isNotEmpty() && (bearer == expected || header == expected || query == expected)
    }

    internal fun module(app: Application) {
        app.install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        app.install(PartialContent)
        app.install(AutoHeadResponse)
        app.routing {
            get("/") {
                if (!authorized(call)) return@get call.respondText(ReceivePage.unauthorized, ContentType.Text.Html, HttpStatusCode.Unauthorized)
                call.respondText(ReceivePage.html(deviceName, code), ContentType.Text.Html)
            }
            get("/api/info") {
                call.respond(ServerInfo(deviceName, "android-storage", appVersion, listOf("upload", "resume", "files", "download") + extraCapabilities))
            }
            route("/api") {
                get("/files") {
                    if (!authorized(call)) return@get call.respond(HttpStatusCode.Unauthorized)
                    val path = call.request.queryParameters["path"] ?: inboxDir.absolutePath
                    val entries = runCatching { fs.list(path) }.getOrElse { return@get call.respondText(it.message ?: "error", status = HttpStatusCode.NotFound) }
                    call.respond(entries.map { RemoteEntry(it.name, it.path, it.isDirectory, it.size, it.lastModified) })
                }
                get("/download") {
                    if (!authorized(call)) return@get call.respond(HttpStatusCode.Unauthorized)
                    val path = call.request.queryParameters["path"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val file = File(path)
                    if (!file.isFile) return@get call.respond(HttpStatusCode.NotFound)
                    call.response.header("Content-Disposition", "attachment; filename=\"${file.name}\"")
                    call.respondFile(file)
                }
                get("/upload/{name}") {
                    if (!authorized(call)) return@get call.respond(HttpStatusCode.Unauthorized)
                    val name = safeName(call.parameters["name"]) ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val target = File(inboxDir, name)
                    call.respond(UploadStatus(name, if (target.exists()) target.length() else 0))
                }
                put("/upload/{name}") {
                    if (!authorized(call)) return@put call.respond(HttpStatusCode.Unauthorized)
                    val name = safeName(call.parameters["name"]) ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0
                    val from = call.request.header("X-From") ?: call.request.local.remoteHost
                    val target = File(inboxDir, name)
                    inboxDir.mkdirs()
                    val current = if (target.exists()) target.length() else 0
                    if (offset > current) return@put call.respond(HttpStatusCode.Conflict, UploadStatus(name, current))
                    val written = writeAt(target, offset, call.receiveChannel())
                    val complete = call.request.queryParameters["complete"] != "false"
                    if (complete) recordReceived(target, from)
                    call.respond(UploadStatus(name, written))
                }
                post("/upload") {
                    if (!authorized(call)) return@post call.respond(HttpStatusCode.Unauthorized)
                    val from = call.request.header("X-From") ?: call.request.local.remoteHost
                    inboxDir.mkdirs()
                    val saved = ArrayList<UploadStatus>()
                    call.receiveMultipart(formFieldLimit = 1L shl 20).forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val name = safeName(part.originalFileName) ?: "upload-${System.currentTimeMillis()}"
                            val target = uniqueTarget(File(inboxDir, name))
                            withContext(Dispatchers.IO) {
                                part.provider().toInputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                            }
                            recordReceived(target, from)
                            saved += UploadStatus(target.name, target.length())
                        }
                        part.dispose()
                    }
                    call.respond(saved)
                }
            }
            extraRoutes(this@TransferServer)
        }
    }

    /** Capabilities the extra routes add, shown in `/api/info`. */
    var extraCapabilities: List<String> = emptyList()

    private suspend fun writeAt(target: File, offset: Long, channel: ByteReadChannel): Long = withContext(Dispatchers.IO) {
        RandomAccessFile(target, "rw").use { raf ->
            raf.setLength(offset)
            raf.seek(offset)
            channel.toInputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    raf.write(buffer, 0, n)
                }
            }
            raf.length()
        }
    }

    private fun recordReceived(file: File, from: String) {
        _state.update { s -> s.copy(received = (listOf(ReceivedFile(file.absolutePath, file.length(), from, System.currentTimeMillis())) + s.received.filter { it.path != file.absolutePath }).take(200)) }
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun newCode(): String {
            val random = SecureRandom()
            return (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        }

        fun safeName(name: String?): String? {
            val clean = name?.substringAfterLast('/')?.substringAfterLast('\\')?.trim() ?: return null
            if (clean.isEmpty() || clean == "." || clean == "..") return null
            return clean
        }

        fun uniqueTarget(wanted: File): File {
            if (!wanted.exists()) return wanted
            val base = wanted.nameWithoutExtension
            val ext = wanted.extension.let { if (it.isEmpty()) "" else ".$it" }
            var i = 1
            while (true) {
                val candidate = File(wanted.parentFile, "$base ($i)$ext")
                if (!candidate.exists()) return candidate
                i++
            }
        }

        fun lanAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { iface -> iface.inetAddresses.toList() }
                .filter { it is java.net.Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .map { it.hostAddress ?: "" }
                .filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }
}
