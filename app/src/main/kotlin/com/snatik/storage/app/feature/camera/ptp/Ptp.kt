package com.snatik.storage.app.feature.camera.ptp

import java.io.ByteArrayOutputStream

/**
 * PTP / PTP-IP wire primitives - pure Kotlin, no Android or socket dependencies, so the byte-level
 * framing and parsing can be unit-tested. See [PtpIpClient] for the transport that uses these.
 *
 * All PTP integers are little-endian. Strings are PTP strings: one byte character-count (including
 * the trailing null), then that many UTF-16LE code units.
 */

/** PTP-IP packet types. */
object PtpIpType {
    const val INIT_CMD_REQ = 1
    const val INIT_CMD_ACK = 2
    const val INIT_EVENT_REQ = 3
    const val INIT_EVENT_ACK = 4
    const val INIT_FAIL = 5
    const val OPERATION_REQUEST = 6
    const val OPERATION_RESPONSE = 7
    const val EVENT = 8
    const val START_DATA = 9
    const val DATA = 10
    const val CANCEL = 11
    const val END_DATA = 12
    const val PING = 13
    const val PONG = 14
}

/** PTP operation codes we use. */
object PtpOp {
    const val GET_DEVICE_INFO = 0x1001
    const val OPEN_SESSION = 0x1002
    const val CLOSE_SESSION = 0x1003
    const val GET_STORAGE_IDS = 0x1004
    const val GET_OBJECT_HANDLES = 0x1007
    const val GET_OBJECT_INFO = 0x1008
    const val GET_OBJECT = 0x1009
    const val GET_THUMB = 0x100A
}

object PtpResponse {
    const val OK = 0x2001
}

/** A parsed PTP ObjectInfo dataset (the subset we need for browsing). */
data class PtpObjectInfo(
    val storageId: Long,
    val objectFormat: Int,
    val compressedSize: Long,
    val thumbFormat: Int,
    val thumbSize: Long,
    val imageWidth: Long,
    val imageHeight: Long,
    val parentObject: Long,
    val associationType: Int,
    val filename: String,
    val captureDate: String,
) {
    val isFolder: Boolean get() = associationType != 0 || objectFormat == 0x3001 // Association
}

/** Little-endian writer. */
class LeWriter {
    private val out = ByteArrayOutputStream()
    fun u8(v: Int) = apply { out.write(v and 0xFF) }
    fun u16(v: Int) = apply { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF) }
    fun u32(v: Long) = apply { for (i in 0..3) out.write(((v ushr (8 * i)) and 0xFF).toInt()) }
    fun u32(v: Int) = u32(v.toLong() and 0xFFFFFFFFL)
    fun u64(v: Long) = apply { for (i in 0..7) out.write(((v ushr (8 * i)) and 0xFF).toInt()) }
    fun bytes(b: ByteArray) = apply { out.write(b) }
    /** A null-terminated UTF-16LE string (as used in the PTP-IP init packets). */
    fun utf16z(s: String) = apply {
        for (c in s) { out.write(c.code and 0xFF); out.write((c.code ushr 8) and 0xFF) }
        out.write(0); out.write(0)
    }
    /** A PTP string: u8 char-count (incl. null), then UTF-16LE incl. the null terminator. */
    fun ptpString(s: String) = apply {
        if (s.isEmpty()) { out.write(0); return@apply }
        u8(s.length + 1)
        for (c in s) { out.write(c.code and 0xFF); out.write((c.code ushr 8) and 0xFF) }
        out.write(0); out.write(0)
    }
    fun toByteArray(): ByteArray = out.toByteArray()
}

/** Little-endian reader over a byte array. */
class LeReader(private val data: ByteArray, var pos: Int = 0) {
    fun remaining() = data.size - pos
    fun u8(): Int = data[pos++].toInt() and 0xFF
    fun u16(): Int = u8() or (u8() shl 8)
    fun u32(): Long {
        var v = 0L
        for (i in 0..3) v = v or ((data[pos++].toLong() and 0xFF) shl (8 * i))
        return v
    }
    fun u64(): Long {
        var v = 0L
        for (i in 0..7) v = v or ((data[pos++].toLong() and 0xFF) shl (8 * i))
        return v
    }
    fun bytes(n: Int): ByteArray = data.copyOfRange(pos, pos + n).also { pos += n }
    /** PTP string: u8 length-in-chars (incl. null), then UTF-16LE. */
    fun ptpString(): String {
        val n = u8()
        if (n == 0) return ""
        val sb = StringBuilder()
        for (i in 0 until n) {
            val c = u16()
            if (c != 0) sb.append(c.toChar())
        }
        return sb.toString()
    }
}

object Ptp {
    const val COMMAND_PORT = 15740
    const val PROTOCOL_VERSION = 0x00010000

    /** Wrap a payload as a PTP-IP packet: u32 total-length, u32 type, payload. */
    fun packet(type: Int, payload: ByteArray): ByteArray =
        LeWriter().u32(8 + payload.size).u32(type).bytes(payload).toByteArray()

    /** INIT_CMD_REQUEST payload: 16-byte client GUID, UTF-16LE name, protocol version. */
    fun initCommandPayload(guid: ByteArray, name: String): ByteArray {
        require(guid.size == 16) { "GUID must be 16 bytes" }
        return LeWriter().bytes(guid).utf16z(name).u32(PROTOCOL_VERSION).toByteArray()
    }

    /** OPERATION_REQUEST payload. dataPhase: 1 = data-in/none, 2 = data-out. */
    fun operationPayload(dataPhase: Int, opCode: Int, transactionId: Long, params: LongArray): ByteArray {
        val w = LeWriter().u32(dataPhase.toLong()).u16(opCode).u32(transactionId)
        params.forEach { w.u32(it) }
        return w.toByteArray()
    }

    /** Parse the connection number out of an INIT_CMD_ACK payload (first u32). */
    fun parseInitAckConnectionNumber(payload: ByteArray): Long = LeReader(payload).u32()

    /** Parse a PTP simple array of u32 (storage IDs, object handles): u32 count then that many u32. */
    fun parseUint32Array(data: ByteArray): LongArray {
        val r = LeReader(data)
        val n = r.u32().toInt().coerceAtLeast(0)
        return LongArray(n) { r.u32() }
    }

    /** Parse a PTP ObjectInfo dataset. */
    fun parseObjectInfo(data: ByteArray): PtpObjectInfo {
        val r = LeReader(data)
        val storageId = r.u32()
        val objectFormat = r.u16()
        r.u16() // protection status
        val compressedSize = r.u32()
        val thumbFormat = r.u16()
        val thumbSize = r.u32()
        r.u32(); r.u32() // thumb pix w/h
        val imageWidth = r.u32()
        val imageHeight = r.u32()
        r.u32() // bit depth
        val parentObject = r.u32()
        val associationType = r.u16()
        r.u32() // association desc
        r.u32() // sequence number
        val filename = r.ptpString()
        val captureDate = r.ptpString()
        return PtpObjectInfo(
            storageId = storageId,
            objectFormat = objectFormat,
            compressedSize = compressedSize,
            thumbFormat = thumbFormat,
            thumbSize = thumbSize,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            parentObject = parentObject,
            associationType = associationType,
            filename = filename,
            captureDate = captureDate,
        )
    }
}
