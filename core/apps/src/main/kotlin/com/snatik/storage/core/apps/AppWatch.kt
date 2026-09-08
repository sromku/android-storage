package com.snatik.storage.core.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.RunAsShellExecutor
import com.snatik.storage.core.shell.run
import com.snatik.storage.core.shell.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class OpMode { ALLOW, IGNORE, DENY, DEFAULT, FOREGROUND, UNKNOWN }

data class OpAccess(
    val op: String,
    val mode: OpMode,
    /** Milliseconds since the last successful access, or null if never accessed. */
    val lastAccessAgoMs: Long?,
    val running: Boolean,
) {
    val everAccessed: Boolean get() = lastAccessAgoMs != null
}

data class RunningService(val name: String, val process: String, val pid: Int?, val foreground: Boolean, val lastActivityAgoMs: Long?)
data class RunningProcess(val pid: Int, val name: String, val rssKb: Long)
data class RecentFile(val path: String, val lastModified: Long)

enum class Severity { INFO, WARN, ALERT }
data class Signal(val title: String, val detail: String, val severity: Severity)

data class AppWatch(
    val packageName: String,
    val ops: List<OpAccess>,
    val services: List<RunningService>,
    val processes: List<RunningProcess>,
    val recentFiles: List<RecentFile>,
    val signals: List<Signal>,
    val score: Int,
    val shellAvailable: Boolean,
) {
    val level: Severity get() = when { score >= 60 -> Severity.ALERT; score >= 25 -> Severity.WARN; else -> Severity.INFO }
    val accessedOps: List<OpAccess> get() = ops.filter { it.everAccessed }.sortedBy { it.lastAccessAgoMs }
}

/** Builds the behaviour dossier for one app from shell output; parsing is pure and tested. */
class AppWatchRepository(private val context: Context, private val privilege: PrivilegeManager, private val apps: AppRepository) {

    suspend fun watch(packageName: String): AppWatch = withContext(Dispatchers.IO) {
        val shell = privilege.executor.value
        val info = apps.applicationInfo(packageName)
        if (shell == null) {
            return@withContext AppWatch(packageName, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0, shellAvailable = false)
        }
        val ops = runCatching { parseAppOps(shell.run("cmd appops get ${packageName.shellQuote()}", timeoutMs = 20_000).out) }.getOrDefault(emptyList())
        val services = runCatching { parseServices(shell.run("dumpsys activity services ${packageName.shellQuote()}", timeoutMs = 20_000).out) }.getOrDefault(emptyList())
        val processes = runCatching { parseProcesses(shell.run("ps -A -o PID,RSS,NAME", timeoutMs = 15_000).out, packageName) }.getOrDefault(emptyList())
        val recentFiles = info?.let { recentFiles(packageName, it) }.orEmpty()
        val signals = signalsFrom(ops, services)
        AppWatch(packageName, ops, services, processes, recentFiles, signals, score(signals), shellAvailable = true)
    }

    private suspend fun recentFiles(packageName: String, info: ApplicationInfo): List<RecentFile> {
        val shell = privilege.executor.value ?: return emptyList()
        val debuggable = info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val root = if (debuggable || privilege.state.value.tier == com.snatik.storage.core.shell.PrivilegeTier.ROOT) info.dataDir else return emptyList()
        val exec = if (debuggable && privilege.state.value.tier == com.snatik.storage.core.shell.PrivilegeTier.SHIZUKU) RunAsShellExecutor(shell, packageName) else shell
        val prefix = if (exec is RunAsShellExecutor) "." else root
        val result = runCatching { exec.run("find $prefix -type f -newermt '-3 days' 2>/dev/null | head -50", timeoutMs = 30_000) }.getOrNull() ?: return emptyList()
        return result.out.lineSequence().filter { it.isNotBlank() }.map { line ->
            val path = if (line.startsWith(".")) root + line.drop(1) else line
            RecentFile(path, 0)
        }.toList()
    }

    companion object {
        private val TIME = Regex("""time=\+([0-9dhms]+?)ago""")
        private val REL = Regex("""(\d+)d|(\d+)h|(\d+)m(?!s)|(\d+)s|(\d+)ms""")

        /** Ops that matter for a suspicion score, with weights, keyed by op name. */
        private val SENSITIVE: Map<String, Pair<Int, String>> = mapOf(
            "FINE_LOCATION" to (12 to "Precise location"),
            "COARSE_LOCATION" to (8 to "Approximate location"),
            "MONITOR_LOCATION" to (10 to "Ongoing location monitoring"),
            "MONITOR_HIGH_POWER_LOCATION" to (12 to "High-power location"),
            "BLUETOOTH_SCAN" to (14 to "Scanned for Bluetooth devices"),
            "WIFI_SCAN" to (14 to "Scanned Wi-Fi networks"),
            "NEARBY_WIFI_DEVICES" to (12 to "Looked for nearby Wi-Fi devices"),
            "RECORD_AUDIO" to (16 to "Recorded audio"),
            "CAMERA" to (16 to "Used the camera"),
            "READ_CLIPBOARD" to (14 to "Read the clipboard"),
            "SYSTEM_ALERT_WINDOW" to (10 to "Can draw over other apps"),
            "READ_SMS" to (14 to "Read SMS"),
            "READ_CONTACTS" to (10 to "Read contacts"),
            "READ_CALL_LOG" to (12 to "Read the call log"),
            "GET_USAGE_STATS" to (10 to "Read app usage stats"),
            "REQUEST_INSTALL_PACKAGES" to (12 to "Can install packages"),
            "BODY_SENSORS" to (10 to "Read body sensors"),
        )

        fun parseRelativeMs(text: String): Long? {
            var total = 0L
            var matched = false
            for (m in REL.findAll(text)) {
                matched = true
                val (d, h, mm, s, ms) = m.destructured
                total += when {
                    d.isNotEmpty() -> d.toLong() * 86_400_000
                    h.isNotEmpty() -> h.toLong() * 3_600_000
                    mm.isNotEmpty() -> mm.toLong() * 60_000
                    s.isNotEmpty() -> s.toLong() * 1_000
                    ms.isNotEmpty() -> ms.toLong()
                    else -> 0
                }
            }
            return if (matched) total else null
        }

        fun parseAppOps(text: String): List<OpAccess> {
            val out = ArrayList<OpAccess>()
            for (raw in text.lineSequence()) {
                val line = raw.removePrefix("Uid mode: ").trim()
                if (line.isEmpty()) continue
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val op = line.substring(0, colon).trim()
                if (op.isEmpty() || op.contains(' ')) continue
                val rest = line.substring(colon + 1).trim()
                val modeWord = rest.substringBefore(';').trim().substringBefore(' ')
                val mode = when (modeWord) {
                    "allow" -> OpMode.ALLOW; "ignore" -> OpMode.IGNORE; "deny" -> OpMode.DENY
                    "default" -> OpMode.DEFAULT; "foreground" -> OpMode.FOREGROUND; else -> OpMode.UNKNOWN
                }
                if (mode == OpMode.UNKNOWN && op.uppercase() != op) continue
                val ago = TIME.find(rest.replace(" ", ""))?.let { parseRelativeMs(it.groupValues[1]) }
                val running = rest.contains("(running)")
                out += OpAccess(op, mode, ago, running)
            }
            return out
        }

        fun parseServices(text: String): List<RunningService> {
            val out = ArrayList<RunningService>()
            val record = Regex("""ServiceRecord\{[0-9a-f]+ u\d+ [^/]+/([^ }]+)""")
            val appLine = Regex("""ProcessRecord\{[0-9a-f]+ (\d+):([^/]+)/""")
            val last = Regex("""lastActivity=-([0-9dhms]+)""")
            val fg = Regex("""createdFromFg=(true|false)""")
            var current: String? = null
            var process = ""
            var pid: Int? = null
            var lastAgo: Long? = null
            var foreground = false
            fun flush() { current?.let { out += RunningService(it, process, pid, foreground, lastAgo) } }
            for (line in text.lineSequence()) {
                record.find(line)?.let {
                    flush()
                    current = it.groupValues[1]; process = ""; pid = null; lastAgo = null; foreground = false
                }
                appLine.find(line)?.let { pid = it.groupValues[1].toIntOrNull(); process = it.groupValues[2] }
                last.find(line)?.let { lastAgo = parseRelativeMs(it.groupValues[1]) }
                fg.find(line)?.let { foreground = it.groupValues[1] == "true" }
            }
            flush()
            return out
        }

        fun parseProcesses(text: String, packageName: String): List<RunningProcess> {
            val out = ArrayList<RunningProcess>()
            for (line in text.lineSequence()) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 3) continue
                val pid = parts[0].toIntOrNull() ?: continue
                val rss = parts[1].toLongOrNull() ?: continue
                val name = parts.subList(2, parts.size).joinToString(" ")
                if (name == packageName || name.startsWith("$packageName:") || name.startsWith("$packageName.")) out += RunningProcess(pid, name, rss)
            }
            return out
        }

        fun signalsFrom(ops: List<OpAccess>, services: List<RunningService>): List<Signal> {
            val out = ArrayList<Signal>()
            for (op in ops) {
                val weight = SENSITIVE[op.op] ?: continue
                val allowed = op.mode == OpMode.ALLOW || op.mode == OpMode.FOREGROUND
                if (!allowed) continue
                val (base, label) = weight
                if (op.everAccessed) {
                    val recent = (op.lastAccessAgoMs ?: Long.MAX_VALUE) < 24 * 3_600_000L
                    out += Signal(label, "last access " + humanAgo(op.lastAccessAgoMs) + if (op.running) ", running now" else "", if (recent || op.running) Severity.ALERT else Severity.WARN)
                } else if (op.op == "SYSTEM_ALERT_WINDOW" || op.op == "REQUEST_INSTALL_PACKAGES") {
                    out += Signal(label, "granted", Severity.WARN)
                }
            }
            val fg = services.count { it.foreground }
            if (fg > 0) out += Signal("Foreground services", "$fg running", Severity.INFO)
            return out.sortedByDescending { it.severity.ordinal }
        }

        fun score(signals: List<Signal>): Int = signals.sumOf { when (it.severity) { Severity.ALERT -> 20; Severity.WARN -> 10; Severity.INFO -> 2 } }.coerceAtMost(100)

        private fun humanAgo(ms: Long?): String {
            if (ms == null) return "never"
            val s = ms / 1000
            return when {
                s < 60 -> "${s}s ago"
                s < 3600 -> "${s / 60}m ago"
                s < 86400 -> "${s / 3600}h ago"
                else -> "${s / 86400}d ago"
            }
        }
    }
}
