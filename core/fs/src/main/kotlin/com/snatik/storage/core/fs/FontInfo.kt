package com.snatik.storage.core.fs

/**
 * Font metadata read straight from the file's bytes. For sfnt fonts (TTF/OTF/TTC) the OpenType
 * `name` and `maxp` tables give the family, styles, version and glyph count. WOFF/WOFF2 wrap sfnt in
 * a compressed container we don't unpack, so only their format is reported.
 */
data class FontInfo(
    val format: String,
    /** Whether Android's Typeface can render it from file (sfnt only, not WOFF/WOFF2). */
    val previewable: Boolean,
    val family: String? = null,
    val subfamily: String? = null,
    val fullName: String? = null,
    val version: String? = null,
    val postScriptName: String? = null,
    val copyright: String? = null,
    val glyphCount: Int? = null,
) {
    companion object {
        fun parse(bytes: ByteArray): FontInfo = runCatching { parseUnsafe(bytes) }.getOrDefault(FontInfo("unknown", false))

        private fun parseUnsafe(bytes: ByteArray): FontInfo {
            if (bytes.size < 12) return FontInfo("unknown", false)
            val tag = ascii(bytes, 0, 4)
            return when {
                tag == "wOFF" -> FontInfo("WOFF", previewable = false)
                tag == "wOF2" -> FontInfo("WOFF2", previewable = false)
                tag == "ttcf" -> parseSfnt(bytes, "TTC", u32(bytes, 12).toInt()) // first font's offset table
                tag == "OTTO" -> parseSfnt(bytes, "OTF", 0)
                u32(bytes, 0) == 0x00010000L || tag == "true" || tag == "typ1" -> parseSfnt(bytes, "TTF", 0)
                else -> FontInfo("unknown", false)
            }
        }

        private fun parseSfnt(bytes: ByteArray, format: String, base: Int): FontInfo {
            val numTables = u16(bytes, base + 4)
            var nameOff = -1
            var maxpOff = -1
            var dir = base + 12
            repeat(numTables) {
                if (dir + 16 > bytes.size) return@repeat
                when (ascii(bytes, dir, 4)) {
                    "name" -> nameOff = u32(bytes, dir + 8).toInt()
                    "maxp" -> maxpOff = u32(bytes, dir + 8).toInt()
                }
                dir += 16
            }
            val names = if (nameOff >= 0) parseNames(bytes, nameOff) else emptyMap()
            val glyphs = if (maxpOff in 0..(bytes.size - 6)) u16(bytes, maxpOff + 4) else null
            return FontInfo(
                format = format,
                previewable = true,
                family = names[1],
                subfamily = names[2],
                fullName = names[4],
                version = names[5],
                postScriptName = names[6],
                copyright = names[0],
                glyphCount = glyphs,
            )
        }

        /** Parse the `name` table into nameID → best string (preferring the Windows/Unicode record). */
        private fun parseNames(bytes: ByteArray, nameOff: Int): Map<Int, String> {
            if (nameOff + 6 > bytes.size) return emptyMap()
            val count = u16(bytes, nameOff + 2)
            val storage = nameOff + u16(bytes, nameOff + 4)
            val out = HashMap<Int, String>()
            val chosenPlatform = HashMap<Int, Int>()
            var rec = nameOff + 6
            repeat(count) {
                if (rec + 12 > bytes.size) return@repeat
                // NameRecord: platformID(0) encodingID(2) languageID(4) nameID(6) length(8) offset(10)
                val platformId = u16(bytes, rec)
                val nameId = u16(bytes, rec + 6)
                val length = u16(bytes, rec + 8)
                val offset = u16(bytes, rec + 10)
                val start = storage + offset
                if (length > 0 && start + length <= bytes.size) {
                    val value = when (platformId) {
                        0, 3 -> String(bytes, start, length, Charsets.UTF_16BE) // Unicode / Windows
                        else -> ascii(bytes, start, length) // Mac Roman ≈ Latin-1
                    }.trim().takeIf { it.isNotEmpty() }
                    // Prefer a Windows (3) record over Mac (1) so we don't keep a worse duplicate.
                    if (value != null && (nameId !in out || (platformId == 3 && chosenPlatform[nameId] != 3))) {
                        out[nameId] = value
                        chosenPlatform[nameId] = platformId
                    }
                }
                rec += 12
            }
            return out
        }

        private fun ascii(b: ByteArray, off: Int, len: Int): String {
            if (off < 0 || off + len > b.size) return ""
            return String(b, off, len, Charsets.ISO_8859_1)
        }

        private fun u16(b: ByteArray, off: Int): Int {
            if (off < 0 || off + 2 > b.size) return 0
            return ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)
        }

        private fun u32(b: ByteArray, off: Int): Long {
            if (off < 0 || off + 4 > b.size) return 0
            return ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
                ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)
        }
    }
}
