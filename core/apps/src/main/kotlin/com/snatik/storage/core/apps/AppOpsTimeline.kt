package com.snatik.storage.core.apps

import android.content.Context
import android.content.pm.PackageManager
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The process state the app was in when it made the access — the key forensic signal. */
enum class AppOpState { FOREGROUND, FOREGROUND_SERVICE, BACKGROUND, PERSISTENT, UNKNOWN }

data class AppOpAccess(
    val packageName: String,
    val label: String,
    val op: String,
    val agoMs: Long,
    val absTime: String,
    val sensitive: Boolean,
    val state: AppOpState = AppOpState.UNKNOWN,
    val durationMs: Long? = null,
    val denied: Boolean = false,   // a Reject: the app tried the op and was blocked
) {
    /** True when the app was not in the foreground — a background camera/mic/location hit is a red flag. */
    val background: Boolean get() = state == AppOpState.BACKGROUND

    /** Stable identity of one access across repeated dumpsys polls, for de-duplication. */
    val key: String get() = "$packageName|$op|$absTime|${if (denied) "r" else "a"}"
}

/** Device-wide sensitive-access timeline from a single `dumpsys appops` call. */
class AppOpsTimeline(private val context: Context, private val privilege: PrivilegeManager) {

    private val labels = HashMap<String, String>()

    suspend fun recent(limit: Int = 300): List<AppOpAccess> = withContext(Dispatchers.IO) {
        val shell = privilege.executor.value ?: return@withContext emptyList()
        val text = shell.run("dumpsys appops", timeoutMs = 30_000).out
        parse(text).sortedBy { it.agoMs }.take(limit).map { it.copy(label = labelFor(it.packageName)) }
    }

    private fun labelFor(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    companion object {
        private val SENSITIVE = setOf(
            "FINE_LOCATION", "COARSE_LOCATION", "MONITOR_LOCATION", "MONITOR_HIGH_POWER_LOCATION", "GPS",
            "BLUETOOTH_SCAN", "WIFI_SCAN", "NEARBY_WIFI_DEVICES", "RECORD_AUDIO", "CAMERA", "READ_CLIPBOARD",
            "READ_CONTACTS", "READ_SMS", "READ_CALL_LOG", "READ_CALENDAR", "GET_USAGE_STATS", "SYSTEM_ALERT_WINDOW",
            "READ_EXTERNAL_STORAGE", "BODY_SENSORS", "ACTIVITY_RECOGNITION",
        )
        private val PACKAGE = Regex("""^Package (\S+):""")
        private val OP = Regex("""^([A-Z_]+) \(""")
        // Access:/Reject: [<uidstate>-<flag>] <date> <time> (-<rel>) [duration=+<dur>]
        // (Reject lines have no space after the closing bracket, hence \s*.)
        private val ACCESS = Regex("""(Access|Reject): \[([^\]]*)]\s*(\S+ \S+) \(-([0-9dhms]+)\)(?: duration=\+([0-9dhms]+))?""")

        /** Map the appops uid-state tag (before the `-`) to a coarse process state. */
        fun stateOf(tag: String): AppOpState = when (tag.substringBefore('-')) {
            "top" -> AppOpState.FOREGROUND
            "fg" -> AppOpState.FOREGROUND
            "fgsvc" -> AppOpState.FOREGROUND_SERVICE
            "bg", "cch" -> AppOpState.BACKGROUND
            "pers" -> AppOpState.PERSISTENT
            else -> AppOpState.UNKNOWN
        }

        /** Walk the Uid Op State: `Package pkg:` then `OP (...)` then `Access: [..] <abs> (-rel)`. */
        fun parse(text: String): List<AppOpAccess> {
            val out = ArrayList<AppOpAccess>()
            var pkg: String? = null
            var op: String? = null
            for (raw in text.lineSequence()) {
                val t = raw.trim()
                PACKAGE.find(t)?.let { pkg = it.groupValues[1]; op = null }
                OP.find(t)?.let { op = it.groupValues[1] }
                val access = ACCESS.find(t)
                if (access != null && pkg != null && op != null) {
                    val ago = AppWatchRepository.parseRelativeMs(access.groupValues[4]) ?: continue
                    val duration = access.groupValues[5].takeIf { it.isNotEmpty() }?.let { AppWatchRepository.parseRelativeMs(it) }
                    out += AppOpAccess(
                        packageName = pkg!!,
                        label = pkg!!,
                        op = op!!,
                        agoMs = ago,
                        absTime = access.groupValues[3],
                        sensitive = op!! in SENSITIVE,
                        state = stateOf(access.groupValues[2]),
                        durationMs = duration,
                        denied = access.groupValues[1] == "Reject",
                    )
                }
            }
            return out
        }
    }
}
