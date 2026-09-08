package com.snatik.storage.core.apps

import com.snatik.storage.core.fs.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.roundToInt

data class ElfSection(val name: String, val type: String, val offset: Long, val size: Long, val entropy: Double)

data class ElfReport(
    val is64Bit: Boolean,
    val littleEndian: Boolean,
    val typeName: String,
    val machine: String,
    val entryPoint: Long,
    val needed: List<String>,
    val soname: String?,
    val buildId: String?,
    val sections: List<ElfSection>,
    val overallEntropy: Double,
    val packer: String?,
    val stripped: Boolean,
) {
    /** High whole-file entropy with few readable sections suggests packing or encryption. */
    val looksPacked: Boolean get() = packer != null || overallEntropy > 7.2
}

/**
 * Parses an ELF binary (shared library or executable) without loading it: header, section table,
 * dynamic dependencies (DT_NEEDED), SONAME, GNU build-id, and per-section Shannon entropy. Flags
 * common packer signatures and unusually high entropy that hints at packing or encryption.
 */
class ElfInspector(private val fs: FileSystem) {

    suspend fun inspect(path: String): ElfReport? = withContext(Dispatchers.IO) {
        val head = runCatching { fs.readBytes(path, 0, 64) }.getOrNull() ?: return@withContext null
        if (head.size < 64 || head[0] != 0x7F.toByte() || head[1] != 'E'.code.toByte() || head[2] != 'L'.code.toByte() || head[3] != 'F'.code.toByte()) return@withContext null

        val is64 = head[4].toInt() == 2
        val le = head[5].toInt() == 1
        fun u16(b: ByteArray, o: Int) = if (le) (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) else ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
        fun u32(b: ByteArray, o: Int): Long {
            val v = if (le) (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
            else ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)
            return v and 0xFFFFFFFFL
        }
        fun u64(b: ByteArray, o: Int): Long {
            var v = 0L
            if (le) for (i in 7 downTo 0) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
            else for (i in 0..7) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
            return v
        }

        val type = u16(head, 16)
        val machine = u16(head, 18)
        val entry = if (is64) u64(head, 24) else u32(head, 24)
        val shoff = if (is64) u64(head, 40) else u32(head, 32)
        val shentsize = u16(head, if (is64) 58 else 46)
        val shnum = u16(head, if (is64) 60 else 48)
        val shstrndx = u16(head, if (is64) 62 else 50)

        val sections = ArrayList<ElfSection>()
        var stripped = true
        if (shoff > 0 && shnum in 1..2000 && shentsize >= 40) {
            val shTable = runCatching { fs.readBytes(path, shoff, shentsize * shnum) }.getOrNull()
            if (shTable != null && shTable.size >= shentsize * shnum) {
                // string table section header
                val strHdrOff = shstrndx * shentsize
                val strOff = if (is64) u64(shTable, strHdrOff + 24) else u32(shTable, strHdrOff + 16)
                val strSize = if (is64) u64(shTable, strHdrOff + 32) else u32(shTable, strHdrOff + 20)
                val strTab = if (strSize in 1..5_000_000) runCatching { fs.readBytes(path, strOff, strSize.toInt()) }.getOrNull() else null
                for (i in 0 until shnum) {
                    val o = i * shentsize
                    val nameIdx = u32(shTable, o).toInt()
                    val shType = u32(shTable, o + 4)
                    val secOff = if (is64) u64(shTable, o + 24) else u32(shTable, o + 16)
                    val secSize = if (is64) u64(shTable, o + 32) else u32(shTable, o + 20)
                    val name = strTab?.let { cString(it, nameIdx) } ?: ""
                    if (name == ".symtab") stripped = false
                    val entropy = if (secSize in 1..4_000_000 && shType.toInt() != 8 /* not NOBITS */) {
                        runCatching { entropyOf(fs.readBytes(path, secOff, secSize.toInt().coerceAtMost(1_000_000))) }.getOrDefault(0.0)
                    } else 0.0
                    sections += ElfSection(name.ifEmpty { "[$i]" }, sectionTypeName(shType), secOff, secSize, entropy)
                }
            }
        }

        // Whole-file entropy from a sampled prefix.
        val sample = runCatching { fs.readBytes(path, 0, 2_000_000) }.getOrDefault(head)
        val overall = entropyOf(sample)
        val packer = detectPacker(sample, sections)

        // Dynamic dependencies (DT_NEEDED) and SONAME from .dynamic + .dynstr.
        var needed = emptyList<String>()
        var soname: String? = null
        val dynamic = sections.firstOrNull { it.name == ".dynamic" }
        val dynstr = sections.firstOrNull { it.name == ".dynstr" }
        if (dynamic != null && dynstr != null && dynamic.size in 1..1_000_000 && dynstr.size in 1..5_000_000) {
            val dyn = runCatching { fs.readBytes(path, dynamic.offset, dynamic.size.toInt()) }.getOrNull()
            val strs = runCatching { fs.readBytes(path, dynstr.offset, dynstr.size.toInt()) }.getOrNull()
            if (dyn != null && strs != null) {
                val step = if (is64) 16 else 8
                val neededList = ArrayList<String>()
                var o = 0
                while (o + step <= dyn.size) {
                    val tag = if (is64) u64(dyn, o) else u32(dyn, o)
                    val value = if (is64) u64(dyn, o + 8) else u32(dyn, o + 4)
                    when (tag) {
                        1L -> cString(strs, value.toInt()).takeIf { it.isNotEmpty() }?.let { neededList += it }
                        14L -> soname = cString(strs, value.toInt()).takeIf { it.isNotEmpty() }
                        0L -> { o = dyn.size } // DT_NULL terminates
                    }
                    o += step
                }
                needed = neededList
            }
        }

        ElfReport(
            is64Bit = is64,
            littleEndian = le,
            typeName = elfTypeName(type),
            machine = machineName(machine),
            entryPoint = entry,
            needed = needed,
            soname = soname,
            buildId = buildIdFrom(sample),
            sections = sections.sortedByDescending { it.size },
            overallEntropy = overall,
            packer = packer,
            stripped = stripped,
        )
    }

    companion object {
        fun entropyOf(bytes: ByteArray): Double {
            if (bytes.isEmpty()) return 0.0
            val counts = IntArray(256)
            for (b in bytes) counts[b.toInt() and 0xFF]++
            var e = 0.0
            val n = bytes.size.toDouble()
            for (c in counts) if (c > 0) { val p = c / n; e -= p * (ln(p) / ln(2.0)) }
            return (e * 100).roundToInt() / 100.0
        }

        private fun cString(b: ByteArray, start: Int): String {
            if (start < 0 || start >= b.size) return ""
            var end = start
            while (end < b.size && b[end].toInt() != 0) end++
            return String(b, start, end - start, Charsets.US_ASCII)
        }

        private fun detectPacker(bytes: ByteArray, sections: List<ElfSection>): String? {
            val text = String(bytes, Charsets.ISO_8859_1)
            return when {
                text.contains("UPX!") || text.contains("UPX0") -> "UPX"
                text.contains("\$Info: This file is packed") -> "UPX"
                sections.any { it.name.contains("upx", true) } -> "UPX"
                text.contains("libAPKProtect") || text.contains("APKProtect") -> "APKProtect"
                text.contains("libjiagu") -> "Qihoo Jiagu"
                text.contains("libDexHelper") -> "SecNeo DexHelper"
                text.contains("libtup.so") || text.contains("libshell") -> "Tencent Legu"
                else -> null
            }
        }

        private fun buildIdFrom(bytes: ByteArray): String? {
            // GNU build-id note: owner "GNU\0" then 16-20 bytes. Find the marker heuristically.
            val marker = byteArrayOf('G'.code.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 0)
            var i = 0
            while (i < bytes.size - 24) {
                if (bytes[i] == marker[0] && bytes[i + 1] == marker[1] && bytes[i + 2] == marker[2] && bytes[i + 3] == marker[3]) {
                    // note structure varies; take the next 20 bytes as a plausible id
                    val id = StringBuilder()
                    for (j in i + 4 until i + 24) id.append("%02x".format(bytes[j].toInt() and 0xFF))
                    val s = id.toString()
                    if (s.any { it != '0' }) return s
                }
                i++
            }
            return null
        }

        private fun elfTypeName(t: Int) = when (t) { 1 -> "Relocatable"; 2 -> "Executable"; 3 -> "Shared object"; 4 -> "Core"; else -> "Type $t" }
        private fun machineName(m: Int) = when (m) { 0x28 -> "ARM"; 0xB7 -> "AArch64"; 0x3E -> "x86-64"; 0x03 -> "x86"; 0xF3 -> "RISC-V"; else -> "Machine 0x%X".format(m) }
        private fun sectionTypeName(t: Long) = when (t.toInt()) { 1 -> "PROGBITS"; 2 -> "SYMTAB"; 3 -> "STRTAB"; 4 -> "RELA"; 8 -> "NOBITS"; 9 -> "REL"; 11 -> "DYNSYM"; 14 -> "INIT_ARRAY"; 6 -> "DYNAMIC"; 7 -> "NOTE"; else -> "T$t" }
    }
}
