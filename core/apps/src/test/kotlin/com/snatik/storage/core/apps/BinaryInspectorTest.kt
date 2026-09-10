package com.snatik.storage.core.apps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BinaryInspectorTest {

    @Test fun detectsCommonMagics() {
        assertEquals("ELF binary", BinaryInspector.detect(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 0))?.label)
        assertEquals("PNG image", BinaryInspector.detect(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A))?.label)
        assertEquals("ZIP archive", BinaryInspector.detect(byteArrayOf(0x50, 0x4B, 0x03, 0x04))?.label)
        // "SQLite format 3" + NUL + a trailing byte
        val sqlite = "SQLite format 3".toByteArray() + byteArrayOf(0, 'x'.code.toByte())
        assertEquals("SQLite database", BinaryInspector.detect(sqlite)?.label)
        assertEquals("PDF document", BinaryInspector.detect("%PDF-1.7".toByteArray())?.label)
        val mp4 = ByteArray(12); "ftyp".toByteArray().copyInto(mp4, 4)
        assertEquals("MP4 / ISO media", BinaryInspector.detect(mp4)?.label)
    }

    @Test fun extractsAsciiAndWideStrings() {
        val bytes = byteArrayOf(0, 1, 2) + "hello world".toByteArray() + byteArrayOf(0, 0) + "ab".toByteArray()
        val found = BinaryInspector.strings(bytes, min = 4)
        assertTrue(found.any { it.text == "hello world" && !it.wide })
        assertTrue(found.none { it.text == "ab" })
        // UTF-16LE "Test" = 54 00 65 00 73 00 74 00
        val wide = byteArrayOf(0x54, 0, 0x65, 0, 0x73, 0, 0x74, 0)
        assertTrue(BinaryInspector.strings(wide, min = 4).any { it.text == "Test" && it.wide })
    }

    @Test fun decodesProtobuf() {
        val bytes = byteArrayOf(0x08, 0x96.toByte(), 0x01, 0x12, 0x02, 0x68, 0x69)
        assertTrue(BinaryInspector.looksLikeProtobuf(bytes))
        val fields = BinaryInspector.decodeProtobuf(bytes)
        assertEquals(2, fields.size)
        assertEquals(1, fields[0].field)
        assertEquals(150L, (fields[0].value as ProtoValue.VarInt).v)
        assertEquals(2, fields[1].field)
        assertEquals("hi", (fields[1].value as ProtoValue.Text).s)
    }

    @Test fun decodesNestedProtobuf() {
        val bytes = byteArrayOf(0x1A, 0x02, 0x08, 0x05)
        val fields = BinaryInspector.decodeProtobuf(bytes)
        assertEquals(1, fields.size)
        val msg = fields[0].value as ProtoValue.Message
        assertEquals(5L, (msg.fields[0].value as ProtoValue.VarInt).v)
    }

    @Test fun rejectsGarbageAsProtobuf() {
        val junk = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertTrue(!BinaryInspector.looksLikeProtobuf(junk))
    }
}
