package com.snatik.storage.core.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppEventType { INSTALLED, UPDATED, UNINSTALLED }

/** One package lifecycle event — an install, update or uninstall — as it happened. */
@Entity(tableName = "app_events")
data class AppEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val packageName: String,
    val label: String,
    val type: String,
    val versionName: String?,
    val versionCode: Long,
    val system: Boolean,
    /** true when reconstructed from PackageManager's install times at first run, not observed. */
    val seeded: Boolean,
)

/** The last-seen state of an installed package, used to detect changes between app opens. */
@Entity(tableName = "known_apps")
data class KnownApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val firstInstallTime: Long,
    val versionCode: Long,
    val system: Boolean,
)

@Dao
interface AppEventDao {
    @Insert suspend fun insert(event: AppEvent)
    @Insert suspend fun insertAll(events: List<AppEvent>)

    @Query("SELECT * FROM app_events ORDER BY ts DESC, id DESC")
    suspend fun all(): List<AppEvent>

    @Query("SELECT COUNT(*) FROM app_events")
    suspend fun count(): Int

    @Query("SELECT label FROM app_events WHERE packageName = :pkg ORDER BY ts DESC LIMIT 1")
    suspend fun lastLabel(pkg: String): String?

    @Query("SELECT system FROM app_events WHERE packageName = :pkg ORDER BY ts DESC LIMIT 1")
    suspend fun lastSystem(pkg: String): Boolean?

    @Query("DELETE FROM app_events")
    suspend fun clear()

    @Query("SELECT * FROM known_apps")
    suspend fun known(): List<KnownApp>

    @Query("SELECT COUNT(*) FROM known_apps")
    suspend fun knownCount(): Int

    @Upsert suspend fun upsertKnown(app: KnownApp)
    @Insert suspend fun insertKnownAll(apps: List<KnownApp>)

    @Query("DELETE FROM known_apps WHERE packageName = :pkg")
    suspend fun deleteKnown(pkg: String)

    @Query("DELETE FROM known_apps")
    suspend fun clearKnown()
}

@Database(entities = [AppEvent::class, KnownApp::class], version = 2, exportSchema = false)
abstract class AppEventDatabase : RoomDatabase() {
    abstract fun dao(): AppEventDao

    companion object {
        @Volatile private var instance: AppEventDatabase? = null

        /** Process-wide singleton so the manifest receiver and the UI share one database. */
        fun create(context: Context): AppEventDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppEventDatabase::class.java, "app_events.db")
                .fallbackToDestructiveMigration(true)
                .build().also { instance = it }
        }
    }
}

/**
 * A running log of when packages were installed, updated or removed. [reconcile] diffs the currently
 * installed set against a stored snapshot on every open, so nothing is missed even when the app was
 * closed (install/update times come from PackageManager; an uninstall is timestamped when first seen
 * missing). A manifest [android.content.BroadcastReceiver] also records events in real time via
 * [record] while the app runs; both paths keep the snapshot in sync, so they never double-count.
 */
class AppEventLog(
    private val context: Context,
    private val db: AppEventDatabase,
    private val apps: AppRepository,
) {
    private val dao get() = db.dao()

    /** Real-time path used by the manifest receiver. */
    suspend fun record(packageName: String, type: AppEventType): Unit = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val info = runCatching { @Suppress("DEPRECATION") pm.getPackageInfo(packageName, 0) }.getOrNull()
        val app = info?.applicationInfo
        val label = app?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() } ?: dao.lastLabel(packageName) ?: packageName
        val system = app?.let { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 } ?: (dao.lastSystem(packageName) ?: false)
        dao.insert(
            AppEvent(
                ts = System.currentTimeMillis(),
                packageName = packageName,
                label = label,
                type = type.name.lowercase(),
                versionName = info?.versionName,
                versionCode = info?.longVersionCode ?: 0,
                system = system,
                seeded = false,
            ),
        )
        if (type == AppEventType.UNINSTALLED) {
            dao.deleteKnown(packageName)
        } else if (info != null) {
            dao.upsertKnown(KnownApp(packageName, label, info.firstInstallTime, info.longVersionCode, system))
        }
    }

    /** Compare the installed set to our snapshot; append any installs/updates/removals we missed. */
    suspend fun reconcile(): Unit = withContext(Dispatchers.IO) {
        val current = apps.list().associateBy { it.packageName }
        if (dao.knownCount() == 0) {
            // First run: reconstruct a baseline from install/update times.
            val events = ArrayList<AppEvent>()
            val knowns = ArrayList<KnownApp>()
            for (a in current.values) {
                events += event(a, AppEventType.INSTALLED, a.firstInstallTime, seeded = true)
                if (a.lastUpdateTime > a.firstInstallTime + 1000) events += event(a, AppEventType.UPDATED, a.lastUpdateTime, seeded = true)
                knowns += KnownApp(a.packageName, a.label, a.firstInstallTime, a.versionCode, a.isSystem)
            }
            dao.insertAll(events)
            dao.insertKnownAll(knowns)
            return@withContext
        }
        val known = dao.known().associateBy { it.packageName }
        for (a in current.values) {
            val k = known[a.packageName]
            when {
                k == null -> {
                    dao.insert(event(a, AppEventType.INSTALLED, a.firstInstallTime, seeded = false))
                    dao.upsertKnown(KnownApp(a.packageName, a.label, a.firstInstallTime, a.versionCode, a.isSystem))
                }
                a.versionCode > k.versionCode || a.firstInstallTime != k.firstInstallTime -> {
                    dao.insert(event(a, AppEventType.UPDATED, a.lastUpdateTime, seeded = false))
                    dao.upsertKnown(KnownApp(a.packageName, a.label, a.firstInstallTime, a.versionCode, a.isSystem))
                }
            }
        }
        for (pkg in known.keys - current.keys) {
            val k = known.getValue(pkg)
            dao.insert(
                AppEvent(
                    ts = System.currentTimeMillis(), packageName = pkg, label = k.label,
                    type = AppEventType.UNINSTALLED.name.lowercase(), versionName = null,
                    versionCode = k.versionCode, system = k.system, seeded = false,
                ),
            )
            dao.deleteKnown(pkg)
        }
    }

    private fun event(a: AppSummary, type: AppEventType, ts: Long, seeded: Boolean) = AppEvent(
        ts = ts, packageName = a.packageName, label = a.label, type = type.name.lowercase(),
        versionName = a.versionName, versionCode = a.versionCode, system = a.isSystem, seeded = seeded,
    )

    suspend fun history(): List<AppEvent> = withContext(Dispatchers.IO) { dao.all() }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        dao.clear()
        dao.clearKnown()
    }

    companion object {
        fun from(context: Context): AppEventLog =
            AppEventLog(context.applicationContext, AppEventDatabase.create(context), AppRepository(context.applicationContext))
    }
}
