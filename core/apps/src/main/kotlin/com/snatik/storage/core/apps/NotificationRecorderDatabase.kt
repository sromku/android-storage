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
 * One notification seen device-wide. Shared model used by both the live in-memory log and the
 * persisted recorder, so the timeline screen renders either source uniformly. [label] is the app's
 * display name when resolved (recorded rows), otherwise empty (live rows fall back to the package).
 */
data class NotificationRecord(
    val key: String,
    val packageName: String,
    val label: String = "",
    val title: String,
    val text: String,
    val category: String?,
    val channelId: String,
    val postedAt: Long,
    val ongoing: Boolean,
    val removed: Boolean = false,
)

/**
 * One recorded notification, persisted so history outlives the process. [key] is the de-dup identity
 * — the same posting seen twice (e.g. re-fed on listener reconnect) is ignored, while a repost with
 * a new time is a distinct row.
 */
@Entity(tableName = "notifications", indices = [Index(value = ["key"], unique = true), Index(value = ["postedAt"]), Index(value = ["packageName"]), Index(value = ["category"])])
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val category: String?,
    val channelId: String,
    val postedAt: Long,
    val ongoing: Boolean,
)

@Dao
interface NotificationRecorderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<NotificationEntity>): List<Long>

    @Query("SELECT * FROM notifications ORDER BY postedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY postedAt DESC LIMIT :limit")
    suspend fun snapshot(limit: Int): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM notifications")
    fun count(): Flow<Int>

    @Query("SELECT MIN(postedAt) FROM notifications")
    fun oldest(): Flow<Long?>

    @Query("SELECT packageName AS name, COUNT(*) AS count FROM notifications GROUP BY packageName ORDER BY count DESC LIMIT :limit")
    suspend fun topApps(limit: Int): List<OpCount>

    @Query("SELECT COALESCE(category, 'uncategorized') AS name, COUNT(*) AS count FROM notifications GROUP BY category ORDER BY count DESC LIMIT :limit")
    suspend fun topCategories(limit: Int): List<OpCount>

    /** Drop everything older than the newest [capacity] rows. */
    @Query("DELETE FROM notifications WHERE id NOT IN (SELECT id FROM notifications ORDER BY id DESC LIMIT :capacity)")
    suspend fun trim(capacity: Int)

    @Query("DELETE FROM notifications")
    suspend fun clear()
}

@Database(entities = [NotificationEntity::class], version = 1, exportSchema = false)
abstract class NotificationRecorderDatabase : RoomDatabase() {
    abstract fun dao(): NotificationRecorderDao

    companion object {
        fun create(context: Context): NotificationRecorderDatabase =
            Room.databaseBuilder(context, NotificationRecorderDatabase::class.java, "notification-recorder.db").build()
    }
}
