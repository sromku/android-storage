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

/** One change notification observed on a watched content URI. */
data class ProviderChange(
    val key: String,
    val target: String,   // the human label of the watched provider, e.g. "Media · Images"
    val uri: String,      // the changed URI, often with the specific row id appended
    val op: String,       // ProviderOp.name — INSERT / UPDATE / DELETE / UNKNOWN
    val atMs: Long,
)

@Entity(tableName = "provider_changes", indices = [Index(value = ["key"], unique = true), Index(value = ["atMs"]), Index(value = ["target"]), Index(value = ["op"])])
data class ProviderChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val target: String,
    val uri: String,
    val op: String,
    val atMs: Long,
)

@Dao
interface ProviderChangeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<ProviderChangeEntity>): List<Long>

    @Query("SELECT * FROM provider_changes ORDER BY atMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ProviderChangeEntity>>

    @Query("SELECT * FROM provider_changes ORDER BY atMs DESC LIMIT :limit")
    suspend fun snapshot(limit: Int): List<ProviderChangeEntity>

    @Query("SELECT COUNT(*) FROM provider_changes")
    fun count(): Flow<Int>

    @Query("SELECT MIN(atMs) FROM provider_changes")
    fun oldest(): Flow<Long?>

    @Query("SELECT target AS name, COUNT(*) AS count FROM provider_changes GROUP BY target ORDER BY count DESC LIMIT :limit")
    suspend fun topTargets(limit: Int): List<OpCount>

    @Query("SELECT op AS name, COUNT(*) AS count FROM provider_changes GROUP BY op ORDER BY count DESC LIMIT :limit")
    suspend fun topOps(limit: Int): List<OpCount>

    @Query("DELETE FROM provider_changes WHERE id NOT IN (SELECT id FROM provider_changes ORDER BY id DESC LIMIT :capacity)")
    suspend fun trim(capacity: Int)

    @Query("DELETE FROM provider_changes")
    suspend fun clear()
}

@Database(entities = [ProviderChangeEntity::class], version = 1, exportSchema = false)
abstract class ProviderChangeDatabase : RoomDatabase() {
    abstract fun dao(): ProviderChangeDao

    companion object {
        fun create(context: Context): ProviderChangeDatabase =
            Room.databaseBuilder(context, ProviderChangeDatabase::class.java, "provider-watch.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
