package com.snatik.storage.core.capture

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val rootPath: String,
    val createdAt: Long,
    val fileCount: Int,
    val totalBytes: Long,
    val hashed: Boolean,
    val copied: Boolean,
)

@Entity(tableName = "snapshot_files", primaryKeys = ["snapshotId", "path"], indices = [Index("snapshotId")])
data class SnapshotFileEntity(
    val snapshotId: Long,
    /** Relative to the snapshot root. */
    val path: String,
    val size: Long,
    val lastModified: Long,
    val hash: String?,
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    /** Comma separated [RecordSource] names. */
    val sources: String,
    val logcatFilter: String?,
    val logcatPackage: String?,
    val watchPaths: String,
    val videoPath: String?,
    val eventCount: Int,
)

@Entity(tableName = "recording_events", indices = [Index("sessionId")])
data class RecordingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val time: Long,
    val source: String,
    val tag: String?,
    val text: String,
)

@Dao
interface SnapshotDao {
    @Insert suspend fun insert(snapshot: SnapshotEntity): Long
    @Insert suspend fun insertFiles(files: List<SnapshotFileEntity>)
    @Query("SELECT * FROM snapshots ORDER BY createdAt DESC") fun snapshots(): Flow<List<SnapshotEntity>>
    @Query("SELECT * FROM snapshots WHERE id = :id") suspend fun snapshot(id: Long): SnapshotEntity?
    @Query("UPDATE snapshots SET label = :label WHERE id = :id") suspend fun rename(id: Long, label: String)
    @Query("SELECT * FROM snapshot_files WHERE snapshotId = :id ORDER BY path") suspend fun files(id: Long): List<SnapshotFileEntity>
    @Query("DELETE FROM snapshot_files WHERE snapshotId = :id") suspend fun deleteFiles(id: Long)
    @Query("DELETE FROM snapshots WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface RecordingDao {
    @Insert suspend fun insert(recording: RecordingEntity): Long
    @Update suspend fun update(recording: RecordingEntity)
    @Insert suspend fun insertEvents(events: List<RecordingEventEntity>)
    @Query("SELECT * FROM recordings ORDER BY startedAt DESC") fun recordings(): Flow<List<RecordingEntity>>
    @Query("SELECT * FROM recordings WHERE id = :id") suspend fun recording(id: Long): RecordingEntity?
    @Query("SELECT * FROM recording_events WHERE sessionId = :id ORDER BY time, id") suspend fun events(id: Long): List<RecordingEventEntity>
    @Query("SELECT count(*) FROM recording_events WHERE sessionId = :id") fun eventCount(id: Long): Flow<Int>
    @Query("DELETE FROM recording_events WHERE sessionId = :id") suspend fun deleteEvents(id: Long)
    @Query("DELETE FROM recordings WHERE id = :id") suspend fun delete(id: Long)
}

@Database(entities = [SnapshotEntity::class, SnapshotFileEntity::class, RecordingEntity::class, RecordingEventEntity::class], version = 1, exportSchema = false)
abstract class CaptureDatabase : RoomDatabase() {
    abstract fun snapshots(): SnapshotDao
    abstract fun recordings(): RecordingDao

    companion object {
        fun create(context: Context): CaptureDatabase =
            Room.databaseBuilder(context, CaptureDatabase::class.java, "capture.db").build()

        fun inMemory(context: Context): CaptureDatabase =
            Room.inMemoryDatabaseBuilder(context, CaptureDatabase::class.java).allowMainThreadQueries().build()
    }
}
