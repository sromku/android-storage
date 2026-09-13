package com.snatik.storage.core.fs

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class FontInfoTest {

    @Test fun detectsWoffAndWoff2AsNotPreviewable() {
        assertEquals("WOFF", FontInfo.parse("wOFF".toByteArray() + ByteArray(20)).format)
        assertEquals("WOFF2", FontInfo.parse("wOF2".toByteArray() + ByteArray(20)).format)
        assertFalse(FontInfo.parse("wOF2".toByteArray() + ByteArray(20)).previewable)
    }

    @Test fun unknownBytesReportUnknown() {
        assertEquals("unknown", FontInfo.parse("not a font here".toByteArray()).format)
        assertEquals("unknown", FontInfo.parse(ByteArray(3)).format)
    }

    @Test fun parsesFamilyAndGlyphCountFromMinimalTtf() {
        val info = FontInfo.parse(minimalTtf(family = "TestFont", glyphs = 42))
        assertEquals("TTF", info.format)
        assertTrue(info.previewable)
        assertEquals("TestFont", info.family)
        assertEquals(42, info.glyphCount)
    }

    /** A byte-accurate sfnt with just `maxp` and `name` (one Windows family-name record). */
    private fun minimalTtf(family: String, glyphs: Int): ByteArray {
        val nameBytes = family.toByteArray(Charsets.UTF_16BE)
        val bos = ByteArrayOutputStream()
        val out = DataOutputStream(bos)
        // offset table
        out.writeInt(0x00010000)      // sfnt version
        out.writeShort(2)             // numTables
        out.writeShort(0); out.writeShort(0); out.writeShort(0) // search fields (ignored)
        val maxpOffset = 12 + 32      // header + 2 dir entries
        val maxpLen = 6
        val nameOffset = maxpOffset + maxpLen
        val nameLen = 6 + 12 + nameBytes.size
        // table directory
        out.writeBytes("maxp"); out.writeInt(0); out.writeInt(maxpOffset); out.writeInt(maxpLen)
        out.writeBytes("name"); out.writeInt(0); out.writeInt(nameOffset); out.writeInt(nameLen)
        // maxp
        out.writeInt(0x00010000); out.writeShort(glyphs)
        // name: format, count, stringOffset
        out.writeShort(0); out.writeShort(1); out.writeShort(6 + 12)
        // one record: platform 3, enc 1, lang 0x409, nameID 1 (family), length, offset 0
        out.writeShort(3); out.writeShort(1); out.writeShort(0x409); out.writeShort(1); out.writeShort(nameBytes.size); out.writeShort(0)
        out.write(nameBytes)
        return bos.toByteArray()
    }
}
