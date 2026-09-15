package com.snatik.storage.core.apps

import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import com.snatik.storage.core.shell.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* ---------- data ---------- */

/** One live process line from `top` (second frame, so %CPU is a real delta over the interval). */
data class ProcessSample(
    val pid: Int,
    val user: String,       // top's USER column, e.g. u0_a128, system, root, webview_zygote
    val cpuPercent: Double,  // 0..800 on an 8-core device (top reports per-core-summed)
    val rssKb: Long,
    val name: String,        // process name (no spaces); for apps this is the package/process name
)

/** One row of the `dumpsys meminfo` App Summary, whose PSS values sum to the total. */
data class MemCategory(val name: String, val pssKb: Long, val rssKb: Long)

/** The rich per-process memory breakdown from `dumpsys meminfo <pid>`. */
data class ProcessMem(
    val categories: List<MemCategory>,
    val totalPssKb: Long,
    val totalRssKb: Long,
    val totalSwapPssKb: Long,
)

/** Kernel-level facts about a process from /proc/<pid>. */
data class ProcessStatus(
    val state: String,
    val ppid: Int,
    val threads: Int,
    val uid: Int,
    val vmPeakKb: Long,
    val vmRssKb: Long,
    val oomScoreAdj: Int?,
    val cmdline: String,
)

/**
 * Reads the per-process CPU and memory breakdown the kernel and `dumpsys` expose: a live `top`
 * snapshot for the ranked list, `dumpsys meminfo <pid>` for the memory composition, and /proc/<pid>
 * for threads/state/oom. A privileged shell (uid 2000 via Shizuku) is required — without it the
 * device won't hand back other apps' processes, so every call returns empty.
 */
class ProcessInspector(private val privilege: PrivilegeManager) {

    /**
     * All live processes, ranked-ready. Runs two `top` frames one second apart and parses the
     * second, so %CPU reflects the last second rather than lifetime. Kernel threads (no RSS) are
     * dropped so the list is apps and services.
     */
    suspend fun processes(): List<ProcessSample> = withContext(Dispatchers.IO) {
        val exec = privilege.executor.value ?: return@withContext emptyList()
        val out = runCatching {
            exec.run("top -b -n 2 -d 1 -o PID,USER,%CPU,RES,NAME 2>/dev/null", timeoutMs = 20_000).out
        }.getOrDefault("")
        // Drop the `top`/`sh` invocation we just ran to measure — it's our own artifact, and it's
        // gone by the next refresh, so it only adds churn to the list.
        parseTop(out).filter { it.name != "top" && it.name != "sh" }
    }

    /** The memory composition of one process, or null if it has gone away. */
    suspend fun memDetail(pid: Int): ProcessMem? = withContext(Dispatchers.IO) {
        val exec = privilege.executor.value ?: return@withContext null
        val out = runCatching { exec.run("dumpsys meminfo $pid 2>/dev/null", timeoutMs = 15_000).out }.getOrDefault("")
        parseMemInfo(out)
    }

    /** Kernel-level status of one process, or null if it has gone away. */
    suspend fun status(pid: Int): ProcessStatus? = withContext(Dispatchers.IO) {
        val exec = privilege.executor.value ?: return@withContext null
        val out = runCatching {
            exec.run(
                "cat /proc/$pid/cmdline 2>/dev/null | tr '\\0' ' ';echo;echo @@@status;" +
                    "cat /proc/$pid/status 2>/dev/null;echo @@@oom;cat /proc/$pid/oom_score_adj 2>/dev/null",
                timeoutMs = 10_000,
            ).out
        }.getOrDefault("")
        parseStatus(out)
    }

    /** Force-stop a package (kills all its processes and cancels its alarms). Shizuku shell can do this. */
    suspend fun forceStop(pkg: String): Boolean = withContext(Dispatchers.IO) {
        val exec = privilege.executor.value ?: return@withContext false
        runCatching { exec.run("am force-stop ${pkg.shellQuote()}", timeoutMs = 15_000).exitCode == 0 }.getOrDefault(false)
    }

    /** Send SIGKILL to a single pid (only succeeds for processes the shell uid may signal). */
    suspend fun kill(pid: Int): Boolean = withContext(Dispatchers.IO) {
        val exec = privilege.executor.value ?: return@withContext false
        runCatching { exec.run("kill -9 $pid", timeoutMs = 5_000).exitCode == 0 }.getOrDefault(false)
    }

    companion object {
        /** Parse `top -b -n 2` output: keep only the last frame, one ProcessSample per data row. */
        fun parseTop(text: String): List<ProcessSample> {
            val lines = text.split('\n')
            // Column header repeats once per frame ("  PID USER  %CPU  RES[NAME]"); start after the last one.
            val lastHeader = lines.indexOfLast { HEADER.containsMatchIn(it) }
            if (lastHeader < 0) return emptyList()
            return lines.asSequence().drop(lastHeader + 1).mapNotNull { line ->
                val t = line.trim()
                if (t.isEmpty()) return@mapNotNull null
                val cols = t.split(Regex("\\s+"))
                if (cols.size < 5) return@mapNotNull null
                val pid = cols[0].toIntOrNull() ?: return@mapNotNull null
                val user = cols[1]
                val cpu = cols[2].toDoubleOrNull() ?: return@mapNotNull null
                val rss = parseSize(cols[3])
                val name = cols.subList(4, cols.size).joinToString(" ")
                if (name.startsWith("[") || (rss == 0L && cpu == 0.0)) return@mapNotNull null  // kernel thread
                ProcessSample(pid, user, cpu, rss, name)
            }.toList()
        }

        /** top RES values carry K/M/G suffixes; a bare number is KB. */
        fun parseSize(s: String): Long {
            if (s.isEmpty()) return 0
            val mult = when (s.last().uppercaseChar()) {
                'K' -> 1L; 'M' -> 1024L; 'G' -> 1024L * 1024
                else -> return s.toLongOrNull() ?: 0
            }
            val n = s.dropLast(1).toDoubleOrNull() ?: return 0
            return (n * mult).toLong()
        }

        fun parseMemInfo(text: String): ProcessMem? {
            if (!text.contains("App Summary")) return null
            val cats = mutableListOf<MemCategory>()
            var totalPss = 0L; var totalRss = 0L; var totalSwap = 0L
            var inSummary = false
            for (raw in text.split('\n')) {
                val line = raw.trimEnd()
                when {
                    line.trim() == "App Summary" -> { inSummary = true; continue }
                    !inSummary -> continue
                    line.contains("TOTAL PSS:") -> {
                        totalPss = numberAfter(line, "TOTAL PSS:") ?: 0
                        totalRss = numberAfter(line, "TOTAL RSS:") ?: 0
                        totalSwap = numberAfter(line, "TOTAL SWAP PSS:") ?: 0
                    }
                    line.trim() == "Objects" -> break
                    line.contains(':') -> {
                        val label = line.substringBefore(':').trim()
                        if (label.isEmpty() || label.contains("Pss") || label.contains("Rss")) continue
                        // PSS is in the left column (offset < 40), RSS in the right column.
                        var pss = 0L; var rss = 0L
                        Regex("\\d+").findAll(line).forEach { m ->
                            if (m.range.first < 40) pss = m.value.toLong() else rss = m.value.toLong()
                        }
                        if (pss != 0L || rss != 0L) cats += MemCategory(label, pss, rss)
                    }
                }
            }
            if (cats.isEmpty() && totalPss == 0L) return null
            return ProcessMem(cats, totalPss, totalRss, totalSwap)
        }

        fun parseStatus(text: String): ProcessStatus? {
            val parts = text.split("@@@status", "@@@oom")
            if (parts.size < 2) return null
            val cmdline = parts[0].trim()
            val status = parts[1]
            val oom = parts.getOrNull(2)?.trim()?.toIntOrNull()
            fun field(key: String): String? = status.lineSequence()
                .firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.trim()
            fun kb(key: String): Long = field(key)?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull() ?: 0
            val state = field("State") ?: "?"
            val ppid = field("PPid")?.toIntOrNull() ?: 0
            val threads = field("Threads")?.toIntOrNull() ?: 0
            val uid = field("Uid")?.split(Regex("\\s+"))?.firstOrNull()?.toIntOrNull() ?: -1
            if (state == "?" && ppid == 0 && threads == 0) return null
            return ProcessStatus(state, ppid, threads, uid, kb("VmPeak"), kb("VmRSS"), oom, cmdline)
        }

        private val HEADER = Regex("^\\s*PID\\s+USER\\s")
        private fun numberAfter(line: String, marker: String): Long? {
            val i = line.indexOf(marker); if (i < 0) return null
            return Regex("\\d+").find(line.substring(i + marker.length))?.value?.toLongOrNull()
        }
    }
}
