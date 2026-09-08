package com.snatik.storage.core.apps

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Change in one app's footprint between the first and most recent snapshot. */
data class AppGrowth(
    val packageName: String,
    val label: String,
    val startBytes: Long,
    val endBytes: Long,
    val versionChanged: Boolean,
) {
    val deltaBytes: Long get() = endBytes - startBytes
}

/** A linear forecast of when free space runs out. */
data class Forecast(val bytesPerDay: Double, val daysUntilFull: Double?, val confident: Boolean)

data class TimeMachineReport(
    val snapshots: Int,
    val device: List<DeviceTelemetry>,
    val forecast: Forecast?,
    val topGrowers: List<AppGrowth>,
    val cacheBytesPerDay: Double,
    val spanMs: Long,
)

/**
 * Records periodic snapshots of device and per-app storage, then reads growth trends from them:
 * which apps are swelling, how fast cache accretes, and a linear forecast of storage exhaustion.
 * Snapshots come from a WorkManager periodic job or an on-demand capture.
 */
class TelemetryRepository(
    private val context: Context,
    private val apps: AppRepository,
    private val db: TelemetryDatabase,
) {
    private val prefs = context.getSharedPreferences("telemetry", Context.MODE_PRIVATE)

    val enabled: Boolean get() = prefs.getBoolean("enabled", false)

    suspend fun capture() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val stat = StatFs(Environment.getDataDirectory().path)
        val free = stat.availableBytes
        val total = stat.totalBytes
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        db.dao().insertDevice(DeviceTelemetry(ts = now, freeBytes = free, totalBytes = total, ramFreeBytes = mem.availMem, ramTotalBytes = mem.totalMem))

        if (apps.hasUsageAccess()) {
            val rows = apps.list().mapNotNull { s ->
                val st = s.storage ?: return@mapNotNull null
                AppTelemetry(ts = now, packageName = s.packageName, versionCode = s.versionCode, appBytes = st.appBytes, dataBytes = st.dataBytes, cacheBytes = st.cacheBytes)
            }
            if (rows.isNotEmpty()) db.dao().insertApps(rows)
        }
        // keep 90 days
        val cutoff = now - 90L * 24 * 3600 * 1000
        db.dao().pruneDevice(cutoff)
        db.dao().pruneApps(cutoff)
    }

    suspend fun report(): TimeMachineReport = withContext(Dispatchers.IO) {
        val device = db.dao().deviceSeries()
        val count = db.dao().snapshotCount()
        val forecast = forecast(device)
        val growers = topGrowers()
        val span = if (device.size >= 2) device.last().ts - device.first().ts else 0L
        val cacheVel = cacheVelocity(span)
        TimeMachineReport(count, device, forecast, growers, cacheVel, span)
    }

    private suspend fun topGrowers(limit: Int = 12): List<AppGrowth> {
        val first = db.dao().earliestApps().associateBy { it.packageName }
        val last = db.dao().latestApps()
        if (first.isEmpty() || last.isEmpty()) return emptyList()
        val labels = runCatching { apps.list().associate { it.packageName to it.label } }.getOrDefault(emptyMap())
        return last.mapNotNull { l ->
            val f = first[l.packageName] ?: return@mapNotNull null
            val startTotal = f.appBytes + f.dataBytes + f.cacheBytes
            val endTotal = l.appBytes + l.dataBytes + l.cacheBytes
            AppGrowth(l.packageName, labels[l.packageName] ?: l.packageName, startTotal, endTotal, l.versionCode != f.versionCode)
        }.filter { it.deltaBytes != 0L }.sortedByDescending { it.deltaBytes }.take(limit)
    }

    private suspend fun cacheVelocity(spanMs: Long): Double {
        if (spanMs <= 0) return 0.0
        val first = db.dao().earliestApps().sumOf { it.cacheBytes }
        val last = db.dao().latestApps().sumOf { it.cacheBytes }
        val days = spanMs.toDouble() / (24 * 3600 * 1000)
        return if (days > 0) (last - first) / days else 0.0
    }

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("enabled", on).apply()
        val wm = WorkManager.getInstance(context)
        if (on) {
            val request = PeriodicWorkRequestBuilder<TelemetryWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }

    companion object {
        const val WORK_NAME = "telemetry-snapshot"

        /** Least-squares fit of freeBytes over time, projecting to zero free space. */
        fun forecast(series: List<DeviceTelemetry>): Forecast? {
            if (series.size < 2) return null
            val t0 = series.first().ts
            val xs = series.map { (it.ts - t0).toDouble() / (24 * 3600 * 1000) } // days
            val ys = series.map { it.freeBytes.toDouble() }
            val n = xs.size
            val sx = xs.sum(); val sy = ys.sum()
            val sxx = xs.sumOf { it * it }; val sxy = xs.indices.sumOf { xs[it] * ys[it] }
            val denom = n * sxx - sx * sx
            if (denom == 0.0) return Forecast(0.0, null, false)
            val slope = (n * sxy - sx * sy) / denom // bytes per day (negative when filling)
            val intercept = (sy - slope * sx) / n
            val bytesPerDay = -slope // positive = losing free space
            val lastX = xs.last()
            val lastFree = ys.last()
            val daysUntilFull = if (slope < 0) {
                // free(x) = intercept + slope*x; solve for free=0
                val xFull = -intercept / slope
                (xFull - lastX).coerceAtLeast(0.0)
            } else null
            val confident = n >= 4
            return Forecast(bytesPerDay, daysUntilFull, confident)
        }
    }
}
