package com.snatik.storage.core.apps

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** A device-wide storage/memory reading at one moment. */
@Entity(tableName = "device_telemetry")
data class DeviceTelemetry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val freeBytes: Long,
    val totalBytes: Long,
    val ramFreeBytes: Long,
    val ramTotalBytes: Long,
)

/** One app's footprint at one moment. */
@Entity(tableName = "app_telemetry")
data class AppTelemetry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val packageName: String,
    val versionCode: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
)

@Dao
interface TelemetryDao {
    @Insert suspend fun insertDevice(row: DeviceTelemetry)
    @Insert suspend fun insertApps(rows: List<AppTelemetry>)

    @Query("SELECT * FROM device_telemetry ORDER BY ts ASC")
    suspend fun deviceSeries(): List<DeviceTelemetry>

    @Query("SELECT COUNT(*) FROM device_telemetry")
    suspend fun snapshotCount(): Int

    @Query("SELECT * FROM app_telemetry WHERE packageName = :pkg ORDER BY ts ASC")
    suspend fun appSeries(pkg: String): List<AppTelemetry>

    /** The most recent snapshot's app rows. */
    @Query("SELECT * FROM app_telemetry WHERE ts = (SELECT MAX(ts) FROM app_telemetry)")
    suspend fun latestApps(): List<AppTelemetry>

    /** The earliest snapshot's app rows. */
    @Query("SELECT * FROM app_telemetry WHERE ts = (SELECT MIN(ts) FROM app_telemetry)")
    suspend fun earliestApps(): List<AppTelemetry>

    @Query("DELETE FROM device_telemetry WHERE ts < :cutoff")
    suspend fun pruneDevice(cutoff: Long)

    @Query("DELETE FROM app_telemetry WHERE ts < :cutoff")
    suspend fun pruneApps(cutoff: Long)
}

@Database(entities = [DeviceTelemetry::class, AppTelemetry::class], version = 1, exportSchema = false)
abstract class TelemetryDatabase : RoomDatabase() {
    abstract fun dao(): TelemetryDao

    companion object {
        @Volatile private var instance: TelemetryDatabase? = null

        /** A process-wide singleton so the worker and the UI share one database. */
        fun create(context: Context): TelemetryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TelemetryDatabase::class.java, "telemetry.db")
                .build().also { instance = it }
        }
    }
}
