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

/** One captured clipboard clip, with everything worth knowing about it. */
data class ClipRecord(
    val key: String,
    val label: String,
    val text: String,
    val html: String,
    val uris: List<String>,
    val mimeTypes: List<String>,
    val itemCount: Int,
    val sensitive: Boolean,
    val copiedAt: Long,     // when the clip was set (ClipDescription timestamp), or capture time
    val capturedAt: Long,   // when this app saw it
)

@Entity(tableName = "clips", indices = [Index(value = ["key"], unique = true), Index(value = ["capturedAt"])])
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val label: String,
    val text: String,
    val html: String,
    val uris: String,       // newline-joined
    val mimeTypes: String,  // comma-joined
    val itemCount: Int,
    val sensitive: Boolean,
    val copiedAt: Long,
    val capturedAt: Long,
)

@Dao
interface ClipboardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: ClipEntity): Long

    @Query("SELECT * FROM clips ORDER BY capturedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun snapshot(limit: Int): List<ClipEntity>

    @Query("SELECT COUNT(*) FROM clips")
    fun count(): Flow<Int>

    @Query("DELETE FROM clips WHERE id NOT IN (SELECT id FROM clips ORDER BY id DESC LIMIT :capacity)")
    suspend fun trim(capacity: Int)

    @Query("DELETE FROM clips")
    suspend fun clear()
}

@Database(entities = [ClipEntity::class], version = 1, exportSchema = false)
abstract class ClipboardDatabase : RoomDatabase() {
    abstract fun dao(): ClipboardDao

    companion object {
        fun create(context: Context): ClipboardDatabase =
            Room.databaseBuilder(context, ClipboardDatabase::class.java, "clipboard.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
