package com.snatik.storage.core.apps

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SQLite-backed store of recorded app-op accesses, shared by the recorder service and the timeline
 * screen. Keeps at most [capacity] rows (oldest dropped) and de-dupes by [AppOpAccess.key], so the
 * same access seen on repeated `dumpsys appops` polls is stored once. Survives the app closing.
 */
class AppOpsRecorderStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("appops_recorder", Context.MODE_PRIVATE)
    private val dao = AppOpsRecorderDatabase.create(appContext).dao()
    private val labels = HashMap<String, String>()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var addsSinceTrim = 0

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _capacity = MutableStateFlow(prefs.getInt(KEY_CAPACITY, DEFAULT_CAPACITY))
    val capacity: StateFlow<Int> = _capacity.asStateFlow()

    private val _intervalSec = MutableStateFlow(prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_SEC))
    val intervalSec: StateFlow<Int> = _intervalSec.asStateFlow()

    /** Newest [DISPLAY_MAX] accesses, live, with labels resolved and "ago" relative to read time. */
    val recent: Flow<List<AppOpAccess>> = dao.recent(DISPLAY_MAX).map { rows ->
        val now = System.currentTimeMillis()
        rows.map { it.toModel(now) }
    }

    val count: Flow<Int> = dao.count()
    val oldest: Flow<Long?> = dao.oldest()

    /** Persist a poll's parsed accesses. [pollTimeMs] anchors each row's absolute time. Returns rows newly stored. */
    suspend fun record(accesses: List<AppOpAccess>, pollTimeMs: Long): Int {
        if (accesses.isEmpty()) return 0
        val inserted = dao.insertAll(accesses.map { it.toEntity(pollTimeMs) }).count { it >= 0 }
        addsSinceTrim += inserted
        if (addsSinceTrim >= TRIM_EVERY) { addsSinceTrim = 0; dao.trim(_capacity.value) }
        return inserted
    }

    suspend fun snapshot(limit: Int = DISPLAY_MAX): List<AppOpAccess> {
        val now = System.currentTimeMillis()
        return dao.snapshot(limit).map { it.toModel(now) }
    }

    suspend fun topApps(limit: Int = 12): List<OpCount> = dao.topApps(limit).map { it.copy(name = labelFor(it.name)) }
    suspend fun topOps(limit: Int = 12): List<OpCount> = dao.topOps(limit)

    suspend fun clear() = dao.clear()
    suspend fun applyCapacity() = dao.trim(_capacity.value)
    fun setRunning(running: Boolean) { _running.value = running }

    fun setCapacity(value: Int) { _capacity.value = value; prefs.edit().putInt(KEY_CAPACITY, value).apply() }
    fun setInterval(sec: Int) { _intervalSec.value = sec; prefs.edit().putInt(KEY_INTERVAL, sec).apply() }

    private fun labelFor(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { appContext.packageManager.getApplicationLabel(appContext.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    private fun AppOpAccess.toEntity(pollTimeMs: Long) = AppOpAccessEntity(
        key = key,
        packageName = packageName,
        op = op,
        atMs = (pollTimeMs - agoMs).coerceAtLeast(0),
        state = state.name,
        durationMs = durationMs,
        denied = denied,
        sensitive = sensitive,
    )

    private fun AppOpAccessEntity.toModel(now: Long) = AppOpAccess(
        packageName = packageName,
        label = labelFor(packageName),
        op = op,
        agoMs = (now - atMs).coerceAtLeast(0),
        absTime = fmt.format(atMs),
        sensitive = sensitive,
        state = runCatching { AppOpState.valueOf(state) }.getOrDefault(AppOpState.UNKNOWN),
        durationMs = durationMs,
        denied = denied,
    )

    companion object {
        const val DEFAULT_CAPACITY = 20_000
        val CAPACITIES = listOf(5_000, 20_000, 100_000)
        const val DEFAULT_INTERVAL_SEC = 60
        val INTERVALS = listOf(30, 60, 300)
        private const val DISPLAY_MAX = 3_000
        private const val TRIM_EVERY = 500
        private const val KEY_CAPACITY = "capacity"
        private const val KEY_INTERVAL = "interval"
    }
}
