package com.snatik.storage.core.apps

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceStats(
    val model: String,
    val androidRelease: String,
    val sdk: Int,
    val securityPatch: String,
    val buildId: String,
    val kernel: String,
    val selinux: String,
    val uptimeMs: Long,
    val totalRamBytes: Long,
    val availRamBytes: Long,
    val batteryPercent: Int,
    val batteryStatus: String,
    val batteryTempC: Float,
    val zramTotalBytes: Long,
    val zramUsedBytes: Long,
    val wakelocks: List<Wakelock>,
)

data class Wakelock(val name: String, val heldMs: Long, val count: Int)

/** One-shot device health snapshot from framework APIs plus a few shell reads. */
class DeviceStatsRepository(private val context: Context, private val privilege: PrivilegeManager) {

    suspend fun stats(): DeviceStats = withContext(Dispatchers.IO) {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val bm = context.getSystemService(BatteryManager::class.java)
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val shell = privilege.executor.value

        val selinux = shell?.let { runCatching { it.run("getenforce").out.trim() }.getOrNull() } ?: "?"
        val kernel = shell?.let { runCatching { it.run("uname -r").out.trim() }.getOrNull() } ?: System.getProperty("os.version").orEmpty()
        val (zTotal, zUsed) = shell?.let { runCatching { zram(it.run("cat /sys/block/zram0/mm_stat 2>/dev/null; echo ---; cat /sys/block/zram0/disksize 2>/dev/null").out) }.getOrNull() } ?: (0L to 0L)
        val wakelocks = shell?.let { runCatching { parseWakelocks(it.run("dumpsys batterystats", timeoutMs = 30_000).out) }.getOrNull() }.orEmpty()

        DeviceStats(
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidRelease = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            buildId = Build.DISPLAY,
            kernel = kernel,
            selinux = selinux,
            uptimeMs = SystemClock.elapsedRealtime(),
            totalRamBytes = mem.totalMem,
            availRamBytes = mem.availMem,
            batteryPercent = batteryPct,
            batteryStatus = batteryStatus(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)),
            batteryTempC = shell?.let { runCatching { it.run("cat /sys/class/power_supply/battery/temp").out.trim().toFloat() / 10 }.getOrNull() } ?: 0f,
            zramTotalBytes = zTotal,
            zramUsedBytes = zUsed,
            wakelocks = wakelocks,
        )
    }

    private fun batteryStatus(s: Int) = when (s) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not charging"
        else -> "unknown"
    }

    companion object {
        /** zram mm_stat: orig_data_size compr_data_size mem_used_total ...; disksize after ---. */
        fun zram(text: String): Pair<Long, Long> {
            val parts = text.split("---")
            val used = parts.getOrNull(0)?.trim()?.split(Regex("\\s+"))?.getOrNull(2)?.toLongOrNull() ?: 0
            val total = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0
            return total to used
        }

        private val WAKE = Regex("""Wake lock (\S+): (?:([0-9dhms]+) )?.*?\((\d+) times\)""")
        fun parseWakelocks(text: String): List<Wakelock> {
            val out = ArrayList<Wakelock>()
            var inSection = false
            for (line in text.lineSequence()) {
                val t = line.trim()
                if (t.startsWith("All kernel wake locks") || t.startsWith("All partial wake locks")) { inSection = true; continue }
                if (inSection && t.isEmpty()) inSection = false
                if (!inSection) continue
                val m = WAKE.find(t) ?: continue
                out += Wakelock(m.groupValues[1], AppWatchRepository.parseRelativeMs(m.groupValues[2]) ?: 0, m.groupValues[3].toIntOrNull() ?: 0)
            }
            return out.sortedByDescending { it.heldMs }.take(20)
        }
    }
}
