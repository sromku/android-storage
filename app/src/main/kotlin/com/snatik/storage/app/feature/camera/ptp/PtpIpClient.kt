package com.snatik.storage.app.feature.camera.ptp

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom

class PtpException(message: String) : Exception(message)

/** Result of a PTP data-in transaction. */
private class TxResult(val responseCode: Int, val data: ByteArray)

/**
 * A PTP/IP client for browsing a camera's card and pulling images over Wi-Fi (the transport Sony's
 * PC Remote / Imaging Edge uses). Opens the command + event connections on port 15740, then runs
 * standard PTP operations. Blocking sockets - call from a background thread.
 *
 * The camera must accept our [guid]; the first connection may require confirming the pairing on the
 * camera. Reuse the same GUID across sessions so it stays paired.
 */
class PtpIpClient(
    private val host: String,
    private val guid: ByteArray,
    private val friendlyName: String = "Storage Studio",
    private val timeoutMs: Int = 15_000,
) {
    // Assigned before connecting so close()/abort() from another thread can interrupt a pending
    // blocking connect (closing the socket makes connect() throw).
    @Volatile private var command: Socket? = null
    @Volatile private var event: Socket? = null
    @Volatile private var aborted = false
    private lateinit var cin: InputStream
    private lateinit var cout: OutputStream
    private var transactionId = 0L

    fun connect() {
        val cmd = Socket().also { command = it }
        cmd.connect(InetSocketAddress(host, Ptp.COMMAND_PORT), timeoutMs)
        cmd.soTimeout = timeoutMs; cmd.tcpNoDelay = true
        cin = cmd.getInputStream()
        cout = cmd.getOutputStream()

        // Command connection handshake.
        writePacket(Ptp.packet(PtpIpType.INIT_CMD_REQ, Ptp.initCommandPayload(guid, friendlyName)))
        val (ackType, ackPayload) = readPacket(cin)
        if (ackType == PtpIpType.INIT_FAIL) throw PtpException("Camera rejected the connection (pairing?)")
        if (ackType != PtpIpType.INIT_CMD_ACK) throw PtpException("Unexpected init reply: $ackType")
        val connectionNumber = Ptp.parseInitAckConnectionNumber(ackPayload)

        // Event connection handshake.
        val ev = Socket().also { event = it }
        ev.connect(InetSocketAddress(host, Ptp.COMMAND_PORT), timeoutMs)
        ev.soTimeout = timeoutMs; ev.tcpNoDelay = true
        val eout = ev.getOutputStream()
        eout.write(Ptp.packet(PtpIpType.INIT_EVENT_REQ, LeWriter().u32(connectionNumber).toByteArray()))
        eout.flush()
        val (evType, _) = readPacket(ev.getInputStream())
        if (evType != PtpIpType.INIT_EVENT_ACK) throw PtpException("Event handshake failed: $evType")
    }

    fun openSession() {
        val r = transact(PtpOp.OPEN_SESSION, longArrayOf(1))
        if (r.responseCode != PtpResponse.OK) throw PtpException("OpenSession failed: 0x%04X".format(r.responseCode))
    }

    fun closeSession() { runCatching { transact(PtpOp.CLOSE_SESSION, longArrayOf()) } }

    fun storageIds(): LongArray = Ptp.parseUint32Array(dataIn(PtpOp.GET_STORAGE_IDS, longArrayOf()))

    /** All object handles on [storageId] (0xFFFFFFFF = every store). */
    fun objectHandles(storageId: Long): LongArray =
        Ptp.parseUint32Array(dataIn(PtpOp.GET_OBJECT_HANDLES, longArrayOf(storageId, 0, 0xFFFFFFFFL)))

    fun objectInfo(handle: Long): PtpObjectInfo = Ptp.parseObjectInfo(dataIn(PtpOp.GET_OBJECT_INFO, longArrayOf(handle)))

    fun thumbnail(handle: Long): ByteArray? = runCatching { dataIn(PtpOp.GET_THUMB, longArrayOf(handle)) }.getOrNull()

    /** Streams the full object to [out], reporting bytes written. */
    fun getObject(handle: Long, out: OutputStream, onProgress: (Long) -> Unit = {}) {
        writePacket(Ptp.packet(PtpIpType.OPERATION_REQUEST, Ptp.operationPayload(1, PtpOp.GET_OBJECT, nextTid(), longArrayOf(handle))))
        var written = 0L
        while (true) {
            val (type, payload) = readPacket(cin)
            when (type) {
                PtpIpType.START_DATA -> { /* u32 tid + u64 total; nothing to write */ }
                PtpIpType.DATA, PtpIpType.END_DATA -> {
                    // payload = u32 transactionId + data
                    if (payload.size > 4) { out.write(payload, 4, payload.size - 4); written += payload.size - 4; onProgress(written) }
                    if (type == PtpIpType.END_DATA) { /* wait for response next */ }
                }
                PtpIpType.OPERATION_RESPONSE -> {
                    val code = LeReader(payload).u16()
                    if (code != PtpResponse.OK) throw PtpException("GetObject failed: 0x%04X".format(code))
                    return
                }
                else -> throw PtpException("Unexpected packet during GetObject: $type")
            }
        }
    }

    /** Closes the sockets. Safe to call from another thread to interrupt a pending connect. */
    fun close() {
        aborted = true
        runCatching { command?.close() }
        runCatching { event?.close() }
    }

    // --- transactions ---------------------------------------------------------------

    private fun nextTid(): Long = transactionId++

    /** An operation with no data phase (or where we ignore data): returns the response. */
    private fun transact(opCode: Int, params: LongArray): TxResult = runTransaction(opCode, params)

    /** An operation that returns a data-in payload. */
    private fun dataIn(opCode: Int, params: LongArray): ByteArray {
        val r = runTransaction(opCode, params)
        if (r.responseCode != PtpResponse.OK) throw PtpException("Op 0x%04X failed: 0x%04X".format(opCode, r.responseCode))
        return r.data
    }

    private fun runTransaction(opCode: Int, params: LongArray): TxResult {
        writePacket(Ptp.packet(PtpIpType.OPERATION_REQUEST, Ptp.operationPayload(1, opCode, nextTid(), params)))
        val buffer = java.io.ByteArrayOutputStream()
        while (true) {
            val (type, payload) = readPacket(cin)
            when (type) {
                PtpIpType.START_DATA -> { /* header only */ }
                PtpIpType.DATA, PtpIpType.END_DATA -> if (payload.size > 4) buffer.write(payload, 4, payload.size - 4)
                PtpIpType.OPERATION_RESPONSE -> return TxResult(LeReader(payload).u16(), buffer.toByteArray())
                PtpIpType.EVENT, PtpIpType.PING -> { /* ignore */ }
                else -> throw PtpException("Unexpected packet: $type")
            }
        }
    }

    // --- framing --------------------------------------------------------------------

    private fun writePacket(bytes: ByteArray) { cout.write(bytes); cout.flush() }

    private fun readPacket(ins: InputStream): Pair<Int, ByteArray> {
        val len = LeReader(readN(ins, 4)).u32().toInt()
        if (len < 8 || len > 64 * 1024 * 1024) throw PtpException("Bad packet length: $len")
        val rest = readN(ins, len - 4)
        val type = LeReader(rest).u32().toInt()
        return type to rest.copyOfRange(4, rest.size)
    }

    private fun readN(ins: InputStream, n: Int): ByteArray {
        val b = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = ins.read(b, off, n - off)
            if (r < 0) throw PtpException("Connection closed")
            off += r
        }
        return b
    }

    companion object {
        /** A stable per-install client GUID; reuse it so the camera keeps us paired. */
        fun randomGuid(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
    }
}
