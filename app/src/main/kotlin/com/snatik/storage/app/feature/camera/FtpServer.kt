package com.snatik.storage.app.feature.camera

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/** Where received files are written. begin() opens a destination; the caller streams into it. */
interface StoreSink {
    fun begin(dir: String, name: String): StoredItem?
}

interface StoredItem {
    val output: OutputStream
    fun commit(bytes: Long)
    fun abort()
}

/**
 * A small embedded FTP server that receives files a camera pushes (STOR), supporting the command
 * subset Sony bodies use: login, binary type, MKD/CWD for date folders, and passive (PASV/EPSV) or
 * active (PORT/EPRT) data channels. Plain FTP only (Phase 1); it streams uploads straight into the
 * provided [sink] without buffering whole files, so multi-GB RAW transfers are fine.
 */
class FtpServer(
    private val port: Int,
    private val user: String,
    private val pass: String,
    /** The Wi-Fi address to advertise in passive-mode replies (must be reachable by the camera). */
    private val advertiseHost: String,
    private val sink: StoreSink,
    private val onEvent: (String) -> Unit = {},
) {
    @Volatile private var server: ServerSocket? = null
    @Volatile private var running = false
    private val clients = CopyOnWriteArrayList<Socket>()

    fun start() {
        if (running) return
        running = true
        Thread({ acceptLoop() }, "ftp-accept").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        clients.forEach { runCatching { it.close() } }
        clients.clear()
    }

    private fun acceptLoop() {
        runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }.use { ss ->
                server = ss
                while (running) {
                    val socket = runCatching { ss.accept() }.getOrNull() ?: break
                    clients.add(socket)
                    Thread({ handle(socket) }, "ftp-client").apply { isDaemon = true }.start()
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        runCatching {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val out = s.getOutputStream()
                fun reply(line: String) { out.write((line + "\r\n").toByteArray(Charsets.UTF_8)); out.flush() }

                reply("220 Storage Studio FTP")
                var authedUser = false
                var loggedIn = false
                var cwd = "/"
                var passiveServer: ServerSocket? = null
                var activeTarget: InetSocketAddress? = null

                fun openDataSocket(): Socket? {
                    passiveServer?.let { ps ->
                        return runCatching { ps.accept() }.getOrNull().also { passiveServer = null; runCatching { ps.close() } }
                    }
                    activeTarget?.let { t ->
                        return runCatching { Socket().apply { connect(t, 10_000) } }.getOrNull().also { activeTarget = null }
                    }
                    return null
                }

                while (running) {
                    val line = reader.readLine() ?: break
                    val sp = line.indexOf(' ')
                    val cmd = (if (sp == -1) line else line.substring(0, sp)).uppercase()
                    val arg = if (sp == -1) "" else line.substring(sp + 1)

                    when (cmd) {
                        "USER" -> { authedUser = arg == user || user.isEmpty(); reply(if (pass.isEmpty()) "230 OK".also { loggedIn = true } else "331 Password required") }
                        "PASS" -> { loggedIn = authedUser && (pass.isEmpty() || arg == pass); reply(if (loggedIn) "230 Logged in" else "530 Login incorrect") }
                        "SYST" -> reply("215 UNIX Type: L8")
                        "FEAT" -> { reply("211-Features:"); reply(" UTF8"); reply(" PASV"); reply(" EPSV"); reply(" SIZE"); reply("211 End") }
                        "OPTS" -> reply("200 OK")
                        "TYPE" -> reply("200 Type set to $arg")
                        "PWD", "XPWD" -> reply("257 \"$cwd\"")
                        "CWD" -> { cwd = normalize(cwd, arg); reply("250 CWD ok") }
                        "CDUP" -> { cwd = normalize(cwd, ".."); reply("250 CDUP ok") }
                        "MKD", "XMKD" -> { val p = normalize(cwd, arg); reply("257 \"$p\" created") }
                        "NOOP" -> reply("200 OK")
                        "REST" -> reply("350 Restart not supported, ignoring")
                        "PASV" -> {
                            if (!loggedIn) { reply("530 Not logged in") } else {
                                runCatching { passiveServer?.close() }
                                val ps = ServerSocket(0).also { passiveServer = it; it.soTimeout = 30_000 }
                                val octets = advertiseHost.split(".")
                                val p = ps.localPort
                                reply("227 Entering Passive Mode (${octets[0]},${octets[1]},${octets[2]},${octets[3]},${p / 256},${p % 256})")
                            }
                        }
                        "EPSV" -> {
                            if (!loggedIn) { reply("530 Not logged in") } else {
                                runCatching { passiveServer?.close() }
                                val ps = ServerSocket(0).also { passiveServer = it; it.soTimeout = 30_000 }
                                reply("229 Entering Extended Passive Mode (|||${ps.localPort}|)")
                            }
                        }
                        "PORT" -> { activeTarget = parsePort(arg); reply(if (activeTarget != null) "200 PORT ok" else "501 Bad PORT") }
                        "EPRT" -> { activeTarget = parseEprt(arg); reply(if (activeTarget != null) "200 EPRT ok" else "501 Bad EPRT") }
                        "LIST", "NLST" -> {
                            val data = openDataSocket()
                            if (data == null) reply("425 No data connection") else {
                                reply("150 Here comes the listing")
                                runCatching { data.close() } // empty listing is fine for a receive-only endpoint
                                reply("226 Directory send OK")
                            }
                        }
                        "SIZE" -> reply("550 Not available")
                        "STOR", "STOU" -> {
                            if (!loggedIn) { reply("530 Not logged in"); continue }
                            val name = arg.substringAfterLast('/').ifBlank { "upload_${System.currentTimeMillis()}" }
                            val data = openDataSocket()
                            if (data == null) { reply("425 No data connection"); continue }
                            val item = sink.begin(cwd, name)
                            if (item == null) { runCatching { data.close() }; reply("451 Cannot store"); continue }
                            reply("150 Ok to send data")
                            val received = runCatching {
                                var total = 0L
                                data.getInputStream().use { input ->
                                    val buf = ByteArray(64 * 1024)
                                    while (true) {
                                        val n = input.read(buf)
                                        if (n < 0) break
                                        item.output.write(buf, 0, n)
                                        total += n
                                    }
                                }
                                item.output.flush()
                                total
                            }
                            runCatching { data.close() }
                            if (received.isSuccess) { item.commit(received.getOrDefault(0L)); onEvent("received $name"); reply("226 Transfer complete") }
                            else { item.abort(); reply("426 Transfer failed") }
                        }
                        "QUIT" -> { reply("221 Bye"); break }
                        "ABOR" -> reply("226 Abort ok")
                        else -> reply("502 Command not implemented")
                    }
                }
                runCatching { passiveServer?.close() }
            }
        }
        clients.remove(socket)
    }

    private fun normalize(cwd: String, arg: String): String {
        if (arg == "..") return cwd.trimEnd('/').substringBeforeLast('/').ifBlank { "/" }
        val base = if (arg.startsWith("/")) "" else cwd.trimEnd('/')
        return ("$base/${arg.trim('/')}").replace("//", "/").ifBlank { "/" }
    }

    private fun parsePort(arg: String): InetSocketAddress? = runCatching {
        val p = arg.split(",").map { it.trim().toInt() }
        if (p.size != 6) return null
        InetSocketAddress("${p[0]}.${p[1]}.${p[2]}.${p[3]}", p[4] * 256 + p[5])
    }.getOrNull()

    private fun parseEprt(arg: String): InetSocketAddress? = runCatching {
        // |proto|addr|port|
        val parts = arg.split("|")
        InetSocketAddress(parts[2], parts[3].toInt())
    }.getOrNull()
}
