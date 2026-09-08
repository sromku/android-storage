package com.snatik.storage.core.net

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile

data class SendProgress(val fileIndex: Int, val fileCount: Int, val name: String, val sentBytes: Long, val totalBytes: Long, val done: Boolean = false) {
    val fraction: Float get() = if (totalBytes > 0) (sentBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else 0f
}

class PeerException(message: String) : Exception(message)

/** Pushes files to another device's server with resume. */
class PeerClient(private val deviceName: String) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 24 * 60 * 60_000; socketTimeoutMillis = 60_000; connectTimeoutMillis = 10_000 }
    }

    suspend fun info(baseUrl: String): ServerInfo = client.get("$baseUrl/api/info").body()

    /** Check the code by asking for the inbox listing. */
    suspend fun checkCode(baseUrl: String, code: String): Boolean =
        client.get("$baseUrl/api/files") { header("X-Code", code) }.status == HttpStatusCode.OK

    fun send(baseUrl: String, code: String, paths: List<String>): Flow<SendProgress> = flow {
        val files = paths.map(::File).filter { it.isFile }
        val total = files.sumOf { it.length() }
        var sentBefore = 0L
        files.forEachIndexed { index, file ->
            val name = file.name
            val status: UploadStatus = client.get("$baseUrl/api/upload/${encode(name)}") { header("X-Code", code) }.let {
                if (it.status != HttpStatusCode.OK) throw PeerException("Rejected: ${it.status} ${it.bodyAsText()}")
                it.body()
            }
            val offset = if (status.size in 1 until file.length()) status.size else 0
            emit(SendProgress(index, files.size, name, sentBefore + offset, total))
            val length = file.length()
            val response = client.put("$baseUrl/api/upload/${encode(name)}?offset=$offset") {
                header("X-Code", code)
                header("X-From", deviceName)
                setBody(
                    object : OutgoingContent.WriteChannelContent() {
                        override val contentLength: Long = length - offset
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            RandomAccessFile(file, "r").use { raf ->
                                raf.seek(offset)
                                val buffer = ByteArray(256 * 1024)
                                var done = offset
                                while (true) {
                                    val n = raf.read(buffer)
                                    if (n < 0) break
                                    channel.writeFully(buffer, 0, n)
                                    done += n
                                    progressSink?.invoke(sentBefore + done)
                                }
                            }
                        }
                    },
                )
            }
            if (response.status != HttpStatusCode.OK) throw PeerException("Upload failed: ${response.status} ${response.bodyAsText()}")
            sentBefore += length
            emit(SendProgress(index, files.size, name, sentBefore, total))
        }
        emit(SendProgress(files.size, files.size, "", total, total, done = true))
    }.flowOn(Dispatchers.IO)

    /** Set by the caller that wants byte-level progress while a body streams. */
    @Volatile
    var progressSink: ((Long) -> Unit)? = null

    private fun encode(name: String) = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
}
