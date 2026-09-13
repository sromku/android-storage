package com.snatik.storage.core.intents

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A broadcast the monitor is subscribed to. Persisted so the service re-registers after a restart. */
@kotlinx.serialization.Serializable
data class ActiveBroadcast(val action: String, val dataScheme: String? = null)

@Entity(tableName = "broadcast_events")
data class BroadcastEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val action: String?,
    val data: String?,
    val extrasCount: Int,
    /** Full IntentSpec as JSON, for the detail view (extras values included). */
    val specJson: String,
)

@Dao
interface BroadcastDao {
    @Insert suspend fun insert(event: BroadcastEventEntity): Long

    @Query("SELECT * FROM broadcast_events ORDER BY id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<BroadcastEventEntity>>

    @Query("SELECT COUNT(*) FROM broadcast_events")
    fun count(): Flow<Int>

    @Query("DELETE FROM broadcast_events WHERE id NOT IN (SELECT id FROM broadcast_events ORDER BY id DESC LIMIT :capacity)")
    suspend fun trim(capacity: Int)

    @Query("DELETE FROM broadcast_events")
    suspend fun clear()
}

@Database(entities = [BroadcastEventEntity::class], version = 1, exportSchema = false)
abstract class BroadcastDatabase : RoomDatabase() {
    abstract fun dao(): BroadcastDao

    companion object {
        fun create(context: Context): BroadcastDatabase =
            Room.databaseBuilder(context, BroadcastDatabase::class.java, "broadcast-monitor.db").build()
    }
}

/**
 * SQLite-backed store of received broadcasts plus the persisted subscription set, capacity and
 * running state. Shared by the collector service and the viewer screen.
 */
class BroadcastStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("broadcast_monitor", Context.MODE_PRIVATE)
    private val dao = BroadcastDatabase.create(context.applicationContext).dao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var addsSinceTrim = 0

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _capacity = MutableStateFlow(prefs.getInt(KEY_CAPACITY, DEFAULT_CAPACITY))
    val capacity: StateFlow<Int> = _capacity.asStateFlow()

    private val _active = MutableStateFlow(loadActive())
    val active: StateFlow<List<ActiveBroadcast>> = _active.asStateFlow()

    val recent: Flow<List<BroadcastEvent>> = dao.recent(DISPLAY_MAX).map { rows ->
        rows.map { BroadcastEvent(it.id, it.time, runCatching { IntentSpec.fromJson(it.specJson) }.getOrDefault(IntentSpec(action = it.action, data = it.data))) }
    }

    val count: Flow<Int> = dao.count()

    suspend fun add(spec: IntentSpec) {
        val entity = BroadcastEventEntity(time = System.currentTimeMillis(), action = spec.action, data = spec.data, extrasCount = spec.extras.size, specJson = spec.toJson())
        dao.insert(entity)
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

    suspend fun applyCapacity() = dao.trim(_capacity.value)

    fun setRunning(running: Boolean) { _running.value = running }

    fun setActive(action: String, on: Boolean, dataScheme: String? = null) {
        val next = _active.value.filterNot { it.action == action }.toMutableList()
        if (on) next.add(ActiveBroadcast(action, dataScheme))
        _active.value = next
        prefs.edit().putString(KEY_ACTIVE, json.encodeToString(ListSerializer(ActiveBroadcast.serializer()), next)).apply()
    }

    private fun loadActive(): List<ActiveBroadcast> {
        val raw = prefs.getString(KEY_ACTIVE, null) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(ActiveBroadcast.serializer()), raw) }.getOrDefault(emptyList())
    }

    companion object {
        const val DEFAULT_CAPACITY = 10_000
        val CAPACITIES = listOf(1_000, 10_000, 50_000)
        private const val DISPLAY_MAX = 2_000
        private const val TRIM_EVERY = 100
        private const val KEY_CAPACITY = "capacity"
        private const val KEY_ACTIVE = "active"
    }
}
