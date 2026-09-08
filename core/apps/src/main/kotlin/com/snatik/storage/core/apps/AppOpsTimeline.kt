package com.snatik.storage.core.apps

import android.content.Context
import android.content.pm.PackageManager
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppOpAccess(val packageName: String, val label: String, val op: String, val agoMs: Long, val absTime: String, val sensitive: Boolean)

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
        private val ACCESS = Regex("""Access: \[[^\]]*] (\S+ \S+) \(-([0-9dhms]+)\)""")

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
                    val ago = AppWatchRepository.parseRelativeMs(access.groupValues[2]) ?: continue
                    out += AppOpAccess(pkg!!, pkg!!, op!!, ago, access.groupValues[1], op!! in SENSITIVE)
                }
            }
            return out
        }
    }
}
