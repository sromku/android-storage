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
    // Rich content parsed from the notification's extras.
    val subText: String = "",
    val bigText: String = "",
    val summaryText: String = "",
    val infoText: String = "",
    val actions: String = "",       // action button titles, joined by " · "
    val progress: String = "",      // "3/10", "45%" or "…" for indeterminate
    val hasLargeIcon: Boolean = false,
    val hasBigPicture: Boolean = false,
    // Lifecycle: set when the notification is later dismissed.
    val sbnKey: String = "",         // the system key, stable across post/remove — used to match removals
    val removedReason: String = "",  // "" while live; e.g. "swiped", "tapped", "cleared", "app removed"
    val removedAt: Long? = null,     // when it was removed
    // Delivery signal.
    val flags: Int = 0,              // Notification.flags (foreground-service, group-summary, …)
    val importance: Int = IMPORTANCE_UNSPECIFIED,
    // Conversation / list content (MessagingStyle messages or InboxStyle lines), newline-joined.
    val lines: String = "",
) {
    val removed: Boolean get() = removedAt != null || removedReason.isNotEmpty()
    companion object { const val IMPORTANCE_UNSPECIFIED = -1000 }
}

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
    val subText: String = "",
    val bigText: String = "",
    val summaryText: String = "",
    val infoText: String = "",
    val actions: String = "",
    val progress: String = "",
    val hasLargeIcon: Boolean = false,
    val hasBigPicture: Boolean = false,
    val sbnKey: String = "",
    val removedReason: String = "",
    val removedAt: Long? = null,
    val flags: Int = 0,
    val importance: Int = NotificationRecord.IMPORTANCE_UNSPECIFIED,
    val lines: String = "",
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

    /** Mark the most recent still-live row with this system key as removed. */
    @Query("UPDATE notifications SET removedAt = :at, removedReason = :reason WHERE id = (SELECT id FROM notifications WHERE sbnKey = :sbnKey AND removedAt IS NULL ORDER BY postedAt DESC LIMIT 1)")
    suspend fun markRemoved(sbnKey: String, at: Long, reason: String)

    /** Drop everything older than the newest [capacity] rows. */
    @Query("DELETE FROM notifications WHERE id NOT IN (SELECT id FROM notifications ORDER BY id DESC LIMIT :capacity)")
    suspend fun trim(capacity: Int)

    @Query("DELETE FROM notifications")
    suspend fun clear()
}

@Database(entities = [NotificationEntity::class], version = 3, exportSchema = false)
abstract class NotificationRecorderDatabase : RoomDatabase() {
    abstract fun dao(): NotificationRecorderDao

    companion object {
        fun create(context: Context): NotificationRecorderDatabase =
            Room.databaseBuilder(context, NotificationRecorderDatabase::class.java, "notification-recorder.db")
                // Rich-content columns were added in v2; recorded history is disposable, so wipe on upgrade.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
