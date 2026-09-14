package com.snatik.storage.core.apps

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One recorded app-op access (or denial), persisted so history outlives the rolling `dumpsys appops`
 * snapshot. [key] is the de-dup identity — the same access seen on repeated polls is ignored.
 */
@Entity(tableName = "app_op_accesses", indices = [Index(value = ["key"], unique = true), Index(value = ["atMs"]), Index(value = ["packageName"]), Index(value = ["op"])])
data class AppOpAccessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val packageName: String,
    val op: String,
    val atMs: Long,            // epoch millis of the access
    val state: String,         // AppOpState.name
    val durationMs: Long?,
    val denied: Boolean,
    val sensitive: Boolean,
)

/** A (label, count) aggregate row returned by the by-app / by-op rollups. */
data class OpCount(val name: String, val count: Int)

@Dao
interface AppOpRecorderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<AppOpAccessEntity>): List<Long>

    @Query("SELECT * FROM app_op_accesses ORDER BY atMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<AppOpAccessEntity>>

    @Query("SELECT * FROM app_op_accesses ORDER BY atMs DESC LIMIT :limit")
    suspend fun snapshot(limit: Int): List<AppOpAccessEntity>

    @Query("SELECT COUNT(*) FROM app_op_accesses")
    fun count(): Flow<Int>

    @Query("SELECT MIN(atMs) FROM app_op_accesses")
    fun oldest(): Flow<Long?>

    @Query("SELECT packageName AS name, COUNT(*) AS count FROM app_op_accesses GROUP BY packageName ORDER BY count DESC LIMIT :limit")
    suspend fun topApps(limit: Int): List<OpCount>

    @Query("SELECT op AS name, COUNT(*) AS count FROM app_op_accesses GROUP BY op ORDER BY count DESC LIMIT :limit")
    suspend fun topOps(limit: Int): List<OpCount>

    /** Drop everything older than the newest [capacity] rows. */
    @Query("DELETE FROM app_op_accesses WHERE id NOT IN (SELECT id FROM app_op_accesses ORDER BY id DESC LIMIT :capacity)")
    suspend fun trim(capacity: Int)

    @Query("DELETE FROM app_op_accesses")
    suspend fun clear()
}

@Database(entities = [AppOpAccessEntity::class], version = 1, exportSchema = false)
abstract class AppOpsRecorderDatabase : RoomDatabase() {
    abstract fun dao(): AppOpRecorderDao

    companion object {
        fun create(context: Context): AppOpsRecorderDatabase =
            Room.databaseBuilder(context, AppOpsRecorderDatabase::class.java, "appops-recorder.db").build()
    }
}
