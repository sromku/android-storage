package com.snatik.storage.core.apps

/** What a file appears to be, from its magic bytes rather than its extension. */
data class DetectedType(val label: String, val mime: String, val hint: String = "")

/** A printable run found inside a binary. */
data class FoundString(val offset: Long, val text: String, val wide: Boolean)

/** One field decoded from protobuf wire format. */
data class ProtoField(val field: Int, val wireType: Int, val value: ProtoValue)

sealed interface ProtoValue {
    data class VarInt(val v: Long) : ProtoValue
    data class Fixed32(val v: Int) : ProtoValue
    data class Fixed64(val v: Long) : ProtoValue
    data class Text(val s: String) : ProtoValue
    data class Bytes(val size: Int, val preview: String) : ProtoValue
    data class Message(val fields: List<ProtoField>) : ProtoValue
}

/** Magic-byte file-type detection, a printable-strings extractor, and a schema-less protobuf reader. */
object BinaryInspector {

    private fun ByteArray.startsWith(vararg sig: Int, at: Int = 0): Boolean {
        if (size < at + sig.size) return false
        for (i in sig.indices) if ((this[at + i].toInt() and 0xFF) != sig[i]) return false
        return true
    }

    private fun ByteArray.ascii(off: Int, len: Int): String =
        if (size >= off + len) String(this, off, len, Charsets.US_ASCII) else ""

    /** Identify a file from a prefix of its bytes. Returns null when nothing matches. */
    fun detect(b: ByteArray): DetectedType? {
        if (b.isEmpty()) return null
        return when {
            b.startsWith(0x7F, 0x45, 0x4C, 0x46) -> DetectedType("ELF binary", "application/x-elf", "executable or shared library")
            b.startsWith(0x50, 0x4B, 0x03, 0x04) || b.startsWith(0x50, 0x4B, 0x05, 0x06) -> DetectedType("ZIP archive", "application/zip", "APK, JAR and many formats are ZIPs")
            b.ascii(0, 16) == "SQLite format 3\u0000" -> DetectedType("SQLite database", "application/vnd.sqlite3", "open in the SQLite viewer")
            b.startsWith(0x64, 0x65, 0x78, 0x0A) -> DetectedType("DEX bytecode", "application/octet-stream", "Android/Dalvik executable")
            b.startsWith(0x03, 0x00, 0x08, 0x00) -> DetectedType("Android binary XML", "application/xml", "compiled AXML")
            b.startsWith(0x02, 0x00, 0x0C, 0x00) -> DetectedType("Android resources.arsc", "application/octet-stream", "compiled resource table")
            b.startsWith(0x89, 0x50, 0x4E, 0x47) -> DetectedType("PNG image", "image/png")
            b.startsWith(0xFF, 0xD8, 0xFF) -> DetectedType("JPEG image", "image/jpeg")
            b.startsWith(0x47, 0x49, 0x46, 0x38) -> DetectedType("GIF image", "image/gif")
            b.startsWith(0x52, 0x49, 0x46, 0x46) && b.ascii(8, 4) == "WEBP" -> DetectedType("WebP image", "image/webp")
            b.startsWith(0x52, 0x49, 0x46, 0x46) && b.ascii(8, 4) == "WAVE" -> DetectedType("WAV audio", "audio/wav")
            b.ascii(4, 4) == "ftyp" -> DetectedType("MP4 / ISO media", "video/mp4", "video or audio container")
            b.startsWith(0x4F, 0x67, 0x67, 0x53) -> DetectedType("OGG media", "audio/ogg")
            b.startsWith(0x25, 0x50, 0x44, 0x46) -> DetectedType("PDF document", "application/pdf")
            b.startsWith(0x1F, 0x8B) -> DetectedType("GZIP stream", "application/gzip")
            b.startsWith(0x42, 0x5A, 0x68) -> DetectedType("BZIP2 stream", "application/x-bzip2")
            b.startsWith(0xFD, 0x37, 0x7A, 0x58, 0x5A) -> DetectedType("XZ stream", "application/x-xz")
            b.startsWith(0x28, 0xB5, 0x2F, 0xFD) -> DetectedType("Zstandard stream", "application/zstd")
            b.startsWith(0x37, 0x7A, 0xBC, 0xAF) -> DetectedType("7-Zip archive", "application/x-7z-compressed")
            b.startsWith(0xCA, 0xFE, 0xBA, 0xBE) -> DetectedType("Java class", "application/java-vm")
            b.startsWith(0x00, 0x01, 0x00, 0x00) || b.ascii(0, 4) == "OTTO" -> DetectedType("Font (TTF/OTF)", "font/ttf")
            b.startsWith(0x42, 0x4D) -> DetectedType("BMP image", "image/bmp")
            b.ascii(0, 5) == "%YAML" -> DetectedType("YAML text", "text/yaml")
            looksLikeProtobuf(b) -> DetectedType("Protocol Buffers", "application/protobuf", "schema-less; see the Protobuf tab")
            looksLikeText(b) -> DetectedType("Text", "text/plain")
            else -> null
        }
    }

    /** Printable ASCII and UTF-16LE runs of at least [min] characters, capped at [limit]. */
    fun strings(b: ByteArray, min: Int = 4, limit: Int = 5000): List<FoundString> {
        val out = ArrayList<FoundString>()
        // ASCII runs
        var start = -1
        var i = 0
        while (i < b.size) {
            val c = b[i].toInt() and 0xFF
            val printable = c in 0x20..0x7E
            if (printable) { if (start < 0) start = i } else {
                if (start >= 0 && i - start >= min) { out += FoundString(start.toLong(), String(b, start, i - start, Charsets.US_ASCII), false); if (out.size >= limit) return out }
                start = -1
            }
            i++
        }
        if (start >= 0 && b.size - start >= min) out += FoundString(start.toLong(), String(b, start, b.size - start, Charsets.US_ASCII), false)
        // UTF-16LE runs: printable ascii followed by 0x00
        var j = 0
        var wstart = -1; val sb = StringBuilder()
        while (j + 1 < b.size) {
            val lo = b[j].toInt() and 0xFF; val hi = b[j + 1].toInt() and 0xFF
            if (hi == 0 && lo in 0x20..0x7E) { if (wstart < 0) wstart = j; sb.append(lo.toChar()); j += 2 } else {
                if (wstart >= 0 && sb.length >= min) { out += FoundString(wstart.toLong(), sb.toString(), true); if (out.size >= limit) return out.sortedBy { it.offset } }
                wstart = -1; sb.setLength(0); j++
            }
        }
        if (wstart >= 0 && sb.length >= min) out += FoundString(wstart.toLong(), sb.toString(), true)
        return out.sortedBy { it.offset }.take(limit)
    }

    /** Whether [b] parses cleanly as a protobuf message (consumes all bytes, sane fields). */
    fun looksLikeProtobuf(b: ByteArray): Boolean {
        if (b.size < 2) return false
        val fields = runCatching { parseMessage(b, 0, b.size, depth = 0) }.getOrNull() ?: return false
        return fields.isNotEmpty() && fields.all { it.field in 1..536870911 }
    }

    /** Decode protobuf wire format without a schema. Throws on malformed input. */
    fun decodeProtobuf(b: ByteArray): List<ProtoField> = parseMessage(b, 0, b.size, depth = 0)

    private fun parseMessage(b: ByteArray, from: Int, to: Int, depth: Int): List<ProtoField> {
        val fields = ArrayList<ProtoField>()
        var p = from
        while (p < to) {
            val (tag, p1) = readVarint(b, p, to)
            p = p1
            val field = (tag ushr 3).toInt()
            val wire = (tag and 0x7).toInt()
            if (field == 0) throw IllegalStateException("field 0")
            when (wire) {
                0 -> { val (v, p2) = readVarint(b, p, to); p = p2; fields += ProtoField(field, wire, ProtoValue.VarInt(v)) }
                1 -> { if (p + 8 > to) throw IllegalStateException("trunc64"); fields += ProtoField(field, wire, ProtoValue.Fixed64(le64(b, p))); p += 8 }
                5 -> { if (p + 4 > to) throw IllegalStateException("trunc32"); fields += ProtoField(field, wire, ProtoValue.Fixed32(le32(b, p))); p += 4 }
                2 -> {
                    val (len, p2) = readVarint(b, p, to); p = p2
                    val end = p + len.toInt()
                    if (len < 0 || end > to) throw IllegalStateException("badlen")
                    val slice = b.copyOfRange(p, end)
                    // Prefer a printable string (the common wire-2 case); only then try a nested
                    // message, so short text like "hi" is not misread as a sub-message.
                    val nested = if (!isPrintable(slice) && depth < 6) runCatching { parseMessage(b, p, end, depth + 1) }.getOrNull() else null
                    val value = when {
                        isPrintable(slice) -> ProtoValue.Text(String(slice, Charsets.UTF_8))
                        nested != null && nested.isNotEmpty() -> ProtoValue.Message(nested)
                        else -> ProtoValue.Bytes(slice.size, slice.take(16).joinToString(" ") { "%02x".format(it) })
                    }
                    fields += ProtoField(field, wire, value)
                    p = end
                }
                else -> throw IllegalStateException("wire $wire")
            }
        }
        return fields
    }

    private fun readVarint(b: ByteArray, from: Int, to: Int): Pair<Long, Int> {
        var result = 0L; var shift = 0; var p = from
        while (p < to) {
            val byte = b[p].toInt() and 0xFF; p++
            result = result or ((byte.toLong() and 0x7F) shl shift)
            if (byte and 0x80 == 0) return result to p
            shift += 7
            if (shift >= 64) throw IllegalStateException("varint too long")
        }
        throw IllegalStateException("varint truncated")
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
    private fun le64(b: ByteArray, o: Int): Long { var v = 0L; for (i in 7 downTo 0) v = (v shl 8) or (b[o + i].toLong() and 0xFF); return v }

    private fun isPrintable(b: ByteArray): Boolean {
        if (b.isEmpty()) return false
        var printable = 0
        for (x in b) { val c = x.toInt() and 0xFF; if (c == 0x09 || c == 0x0A || c == 0x0D || c in 0x20..0x7E) printable++ }
        return printable.toDouble() / b.size > 0.85
    }

    private fun looksLikeText(b: ByteArray): Boolean = isPrintable(b.copyOfRange(0, minOf(b.size, 512)))
}
