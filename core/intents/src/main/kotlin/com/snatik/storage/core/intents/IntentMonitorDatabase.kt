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

/** One captured activity intent, persisted so the monitor's history survives the app being closed. */
@Entity(tableName = "monitored_intents")
data class MonitoredIntentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val action: String?,
    val data: String?,
    val type: String?,
    /** Newline-joined category names. */
    val categories: String,
    val flags: Int,
    val packageName: String?,
    val className: String?,
    val callerUid: Int?,
    val callerPackage: String?,
    val hasExtras: Boolean,
)

@Dao
interface IntentMonitorDao {
    @Insert suspend fun insert(intent: MonitoredIntentEntity): Long

    @Query("SELECT * FROM monitored_intents ORDER BY id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<MonitoredIntentEntity>>

    @Query("SELECT COUNT(*) FROM monitored_intents")
    fun count(): Flow<Int>

    /** Drop everything older than the newest [capacity] rows. */
    @Query("DELETE FROM monitored_intents WHERE id NOT IN (SELECT id FROM monitored_intents ORDER BY id DESC LIMIT :capacity)")
    suspend fun trim(capacity: Int)

    @Query("DELETE FROM monitored_intents")
    suspend fun clear()
}

@Database(entities = [MonitoredIntentEntity::class], version = 1, exportSchema = false)
abstract class IntentMonitorDatabase : RoomDatabase() {
    abstract fun dao(): IntentMonitorDao

    companion object {
        fun create(context: Context): IntentMonitorDatabase =
            Room.databaseBuilder(context, IntentMonitorDatabase::class.java, "intent-monitor.db").build()
    }
}
