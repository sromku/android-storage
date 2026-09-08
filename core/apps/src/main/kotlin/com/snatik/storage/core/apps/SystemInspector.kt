package com.snatik.storage.core.apps

import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One mounted filesystem from /proc/mounts. */
data class Mount(val device: String, val mountPoint: String, val type: String, val options: String)

/** A block device / partition from /proc/partitions. */
data class Partition(val name: String, val bytes: Long)

/** A swap area from /proc/swaps. */
data class SwapArea(val name: String, val type: String, val sizeKb: Long, val usedKb: Long)

/** ZRAM compressed-swap stats from /sys/block/zram0. */
data class ZramInfo(val disksizeBytes: Long, val originalBytes: Long, val compressedBytes: Long, val memUsedBytes: Long) {
    val ratio: Double get() = if (compressedBytes > 0) originalBytes.toDouble() / compressedBytes else 0.0
}

/** A rollup of this process's /proc/self/smaps. */
data class SmapsRollup(val rssKb: Long, val pssKb: Long, val privateDirtyKb: Long, val swapKb: Long, val regions: Int)

data class SystemReport(
    val mounts: List<Mount>,
    val partitions: List<Partition>,
    val swaps: List<SwapArea>,
    val zram: ZramInfo?,
    val smaps: SmapsRollup?,
)

/**
 * Reads the kernel's own view of the device: mounted filesystems, block partitions, swap areas,
 * ZRAM compression stats, and this process's memory map rollup. These come from world-readable
 * /proc and /sys files; a shell is used only as a fallback for anything this process cannot read.
 */
class SystemInspector(private val privilege: PrivilegeManager) {

    suspend fun report(): SystemReport = withContext(Dispatchers.IO) {
        SystemReport(
            mounts = parseMounts(read("/proc/mounts")),
            partitions = parsePartitions(read("/proc/partitions")),
            swaps = parseSwaps(read("/proc/swaps")),
            zram = readZram(),
            // Read directly: via a shell, /proc/self would be the shell's process, not this app's.
            smaps = parseSmaps(runCatching { File("/proc/self/smaps").readText() }.getOrDefault("")),
        )
    }

    // Prefer the shell (uid 2000) when connected: it can read more of /proc and /sys than the app
    // uid. Fall back to a direct read for world-readable files when there is no shell.
    private suspend fun read(path: String): String {
        val exec = privilege.executor.value
        if (exec != null) {
            val out = runCatching { exec.run("cat $path 2>/dev/null", timeoutMs = 15_000).out }.getOrDefault("")
            if (out.isNotBlank()) return out
        }
        return runCatching { File(path).readText() }.getOrDefault("")
    }

    private suspend fun readZram(): ZramInfo? {
        val disksize = read("/sys/block/zram0/disksize").trim().toLongOrNull() ?: return null
        // mm_stat columns: orig_data_size compr_data_size mem_used_total ...
        val mm = read("/sys/block/zram0/mm_stat").trim().split(Regex("\\s+"))
        val orig = mm.getOrNull(0)?.toLongOrNull() ?: 0
        val compr = mm.getOrNull(1)?.toLongOrNull() ?: 0
        val memUsed = mm.getOrNull(2)?.toLongOrNull() ?: 0
        return ZramInfo(disksize, orig, compr, memUsed)
    }

    companion object {
        fun parseMounts(text: String): List<Mount> = text.lineSequence().mapNotNull { line ->
            val p = line.trim().split(' ')
            if (p.size >= 4) Mount(p[0], p[1], p[2], p[3]) else null
        }.toList()

        fun parsePartitions(text: String): List<Partition> = text.lineSequence().drop(1).mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            // major minor #blocks name  (#blocks are 1 KiB units)
            if (p.size >= 4) p[3].let { name -> (p[2].toLongOrNull())?.let { Partition(name, it * 1024) } } else null
        }.filter { it.bytes > 0 }.sortedByDescending { it.bytes }.toList()

        fun parseSwaps(text: String): List<SwapArea> = text.lineSequence().drop(1).mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size >= 4) SwapArea(p[0], p[1], p[2].toLongOrNull() ?: 0, p[3].toLongOrNull() ?: 0) else null
        }.toList()

        private val REGION = Regex("^[0-9a-f]+-[0-9a-f]+ ")

        fun parseSmaps(text: String): SmapsRollup? {
            if (text.isBlank()) return null
            var rss = 0L; var pss = 0L; var pd = 0L; var swap = 0L; var regions = 0
            text.lineSequence().forEach { line ->
                when {
                    REGION.containsMatchIn(line) -> regions++
                    line.startsWith("Rss:") -> rss += kb(line)
                    line.startsWith("Pss:") -> pss += kb(line)
                    line.startsWith("Private_Dirty:") -> pd += kb(line)
                    line.startsWith("Swap:") -> swap += kb(line)
                }
            }
            return SmapsRollup(rss, pss, pd, swap, regions)
        }

        private fun kb(line: String): Long = line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: 0
    }
}
