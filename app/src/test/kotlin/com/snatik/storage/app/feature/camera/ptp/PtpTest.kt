package com.snatik.storage.app.feature.camera.ptp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PtpTest {

    @Test
    fun `little-endian round trips`() {
        val bytes = LeWriter().u8(0x12).u16(0x3412).u32(0x78563412L).u64(0x1122334455667788L).toByteArray()
        val r = LeReader(bytes)
        assertEquals(0x12, r.u8())
        assertEquals(0x3412, r.u16())
        assertEquals(0x78563412L, r.u32())
        assertEquals(0x1122334455667788L, r.u64())
    }

    @Test
    fun `ptp string round trips`() {
        val bytes = LeWriter().ptpString("DSC09166.ARW").toByteArray()
        assertEquals("DSC09166.ARW", LeReader(bytes).ptpString())
        assertEquals("", LeReader(LeWriter().ptpString("").toByteArray()).ptpString())
    }

    @Test
    fun `packet framing has correct length and type`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val pkt = Ptp.packet(PtpIpType.OPERATION_REQUEST, payload)
        val r = LeReader(pkt)
        assertEquals(8L + payload.size, r.u32())          // total length incl. header
        assertEquals(PtpIpType.OPERATION_REQUEST.toLong(), r.u32())
        assertContentEquals(payload, pkt.copyOfRange(8, pkt.size))
    }

    @Test
    fun `operation payload encodes phase, opcode, tid and params`() {
        val p = Ptp.operationPayload(dataPhase = 1, opCode = PtpOp.GET_OBJECT, transactionId = 7, params = longArrayOf(0x100L))
        val r = LeReader(p)
        assertEquals(1L, r.u32())
        assertEquals(PtpOp.GET_OBJECT, r.u16())
        assertEquals(7L, r.u32())
        assertEquals(0x100L, r.u32())
    }

    @Test
    fun `init command payload carries guid, name and version`() {
        val guid = ByteArray(16) { it.toByte() }
        val payload = Ptp.initCommandPayload(guid, "Cam")
        val r = LeReader(payload)
        assertContentEquals(guid, r.bytes(16))
        // "Cam" as UTF-16LE + null terminator
        assertEquals('C'.code, r.u16()); assertEquals('a'.code, r.u16()); assertEquals('m'.code, r.u16()); assertEquals(0, r.u16())
        assertEquals(Ptp.PROTOCOL_VERSION.toLong(), r.u32())
    }

    @Test
    fun `uint32 array parses count and values`() {
        val data = LeWriter().u32(3).u32(0xAAAA).u32(0xBBBB).u32(0xCCCC).toByteArray()
        assertContentEquals(longArrayOf(0xAAAA, 0xBBBB, 0xCCCC), Ptp.parseUint32Array(data))
    }

    @Test
    fun `object info parses key fields`() {
        val data = LeWriter()
            .u32(0x00010001)      // storageId
            .u16(0x3801)          // objectFormat (EXIF/JPEG)
            .u16(0)               // protection
            .u32(596714)          // compressedSize
            .u16(0x3808)          // thumbFormat
            .u32(12345)           // thumbSize
            .u32(160).u32(120)    // thumb w/h
            .u32(8640).u32(5760)  // image w/h
            .u32(24)              // bit depth
            .u32(0)               // parent
            .u16(0)               // association type
            .u32(0)               // association desc
            .u32(1)               // sequence
            .ptpString("DSC09166.ARW")
            .ptpString("20260616T163011")
            .toByteArray()

        val info = Ptp.parseObjectInfo(data)
        assertEquals(0x00010001L, info.storageId)
        assertEquals(0x3801, info.objectFormat)
        assertEquals(596714L, info.compressedSize)
        assertEquals(8640L, info.imageWidth)
        assertEquals(5760L, info.imageHeight)
        assertEquals("DSC09166.ARW", info.filename)
        assertEquals("20260616T163011", info.captureDate)
        assertTrue(!info.isFolder)
    }
}
