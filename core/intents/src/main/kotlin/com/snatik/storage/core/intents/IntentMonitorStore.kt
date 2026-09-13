package com.snatik.storage.core.intents

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * SQLite-backed store of monitored intents, shared by the collector service and the viewer screen.
 * Keeps at most [capacity] rows (oldest dropped), persisted so history survives the app closing.
 */
class IntentMonitorStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("intent_monitor", Context.MODE_PRIVATE)
    private val dao = IntentMonitorDatabase.create(context.applicationContext).dao()
    private var addsSinceTrim = 0

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _capacity = MutableStateFlow(prefs.getInt(KEY_CAPACITY, DEFAULT_CAPACITY))
    val capacity: StateFlow<Int> = _capacity.asStateFlow()

    private val _hideSelf = MutableStateFlow(prefs.getBoolean(KEY_HIDE_SELF, false))
    val hideSelf: StateFlow<Boolean> = _hideSelf.asStateFlow()

    /** Newest [DISPLAY_MAX] intents, live. */
    val recent: Flow<List<MonitoredIntent>> = dao.recent(DISPLAY_MAX).map { rows -> rows.map { it.toModel() } }

    /** Total rows kept. */
    val count: Flow<Int> = dao.count()

    suspend fun add(intent: MonitoredIntent) {
        dao.insert(intent.toEntity())
        if (++addsSinceTrim >= TRIM_EVERY) {
            addsSinceTrim = 0
            dao.trim(_capacity.value)
        }
    }

    suspend fun clear() = dao.clear()

    fun setCapacity(value: Int) {
        _capacity.value = value
        prefs.edit().putInt(KEY_CAPACITY, value).apply()
    }

    fun setHideSelf(value: Boolean) {
        _hideSelf.value = value
        prefs.edit().putBoolean(KEY_HIDE_SELF, value).apply()
    }

    /** Enforce the current capacity immediately (e.g. after the user lowered it). */
    suspend fun applyCapacity() = dao.trim(_capacity.value)

    fun setRunning(running: Boolean) { _running.value = running }

    private fun MonitoredIntent.toEntity() = MonitoredIntentEntity(
        time = time, action = action, data = data, type = type,
        categories = categories.joinToString("\n"), flags = flags,
        packageName = packageName, className = className,
        callerUid = callerUid, callerPackage = callerPackage, hasExtras = hasExtras,
    )

    private fun MonitoredIntentEntity.toModel() = MonitoredIntent(
        id = id, time = time, action = action, data = data, type = type,
        categories = categories.split("\n").filter { it.isNotEmpty() }, flags = flags,
        packageName = packageName, className = className,
        callerUid = callerUid, callerPackage = callerPackage, hasExtras = hasExtras,
    )

    companion object {
        const val DEFAULT_CAPACITY = 10_000
        val CAPACITIES = listOf(1_000, 10_000, 50_000)
        private const val DISPLAY_MAX = 2_000
        private const val TRIM_EVERY = 200
        private const val KEY_CAPACITY = "capacity"
        private const val KEY_HIDE_SELF = "hide_self"
    }
}
