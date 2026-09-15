package com.snatik.storage.core.apps

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * SQLite-backed store of recorded content-provider changes, shared by the watcher/service and the
 * screen. Keeps at most [capacity] rows (oldest dropped) and de-dupes by [ProviderChange.key].
 * Survives the app closing.
 */
class ProviderRecorderStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("provider_recorder", Context.MODE_PRIVATE)
    private val dao = ProviderChangeDatabase.create(context.applicationContext).dao()
    private var addsSinceTrim = 0

    private val _running = MutableStateFlow(prefs.getBoolean(KEY_RUNNING, false))
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _capacity = MutableStateFlow(prefs.getInt(KEY_CAPACITY, DEFAULT_CAPACITY))
    val capacity: StateFlow<Int> = _capacity.asStateFlow()

    val recent: Flow<List<ProviderChange>> = dao.recent(DISPLAY_MAX).map { rows -> rows.map { it.toModel() } }
    val count: Flow<Int> = dao.count()
    val oldest: Flow<Long?> = dao.oldest()

    suspend fun record(changes: List<ProviderChange>): Int {
        if (changes.isEmpty()) return 0
        val inserted = dao.insertAll(changes.map { it.toEntity() }).count { it >= 0 }
        addsSinceTrim += inserted
        if (addsSinceTrim >= TRIM_EVERY) { addsSinceTrim = 0; dao.trim(_capacity.value) }
        return inserted
    }

    suspend fun record(change: ProviderChange): Int = record(listOf(change))

    suspend fun snapshot(limit: Int = DISPLAY_MAX): List<ProviderChange> = dao.snapshot(limit).map { it.toModel() }
    suspend fun topTargets(limit: Int = 12): List<OpCount> = dao.topTargets(limit)
    suspend fun topOps(limit: Int = 8): List<OpCount> = dao.topOps(limit)

    suspend fun clear() = dao.clear()
    suspend fun applyCapacity() = dao.trim(_capacity.value)

    fun setRunning(running: Boolean) { _running.value = running; prefs.edit().putBoolean(KEY_RUNNING, running).apply() }
    fun setCapacity(value: Int) { _capacity.value = value; prefs.edit().putInt(KEY_CAPACITY, value).apply() }

    private fun ProviderChange.toEntity() = ProviderChangeEntity(key = key, target = target, uri = uri, op = op, atMs = atMs)
    private fun ProviderChangeEntity.toModel() = ProviderChange(key = key, target = target, uri = uri, op = op, atMs = atMs)

    companion object {
        const val DEFAULT_CAPACITY = 20_000
        val CAPACITIES = listOf(5_000, 20_000, 100_000)
        private const val DISPLAY_MAX = 3_000
        private const val TRIM_EVERY = 200
        private const val KEY_CAPACITY = "capacity"
        private const val KEY_RUNNING = "running"
    }
}
