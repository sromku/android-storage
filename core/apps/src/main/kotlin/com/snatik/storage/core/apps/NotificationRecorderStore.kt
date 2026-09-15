package com.snatik.storage.core.apps

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * SQLite-backed store of recorded notifications, shared by the listener service and the monitor
 * screen. Keeps at most [capacity] rows (oldest dropped) and de-dupes by [NotificationRecord.key],
 * so the same posting seen twice is stored once. Survives the app closing.
 *
 * Unlike the app-ops recorder there is no foreground service: the notification listener is bound by
 * the system whenever access is granted, so recording is just a persisted flag the listener checks.
 */
class NotificationRecorderStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("notification_recorder", Context.MODE_PRIVATE)
    private val dao = NotificationRecorderDatabase.create(appContext).dao()
    private val labels = HashMap<String, String>()
    private var addsSinceTrim = 0

    // Persisted so the always-bound listener keeps recording after the app process is gone.
    private val _running = MutableStateFlow(prefs.getBoolean(KEY_RUNNING, false))
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _capacity = MutableStateFlow(prefs.getInt(KEY_CAPACITY, DEFAULT_CAPACITY))
    val capacity: StateFlow<Int> = _capacity.asStateFlow()

    /** Newest [DISPLAY_MAX] notifications, live, with app labels resolved. */
    val recent: Flow<List<NotificationRecord>> = dao.recent(DISPLAY_MAX).map { rows -> rows.map { it.toModel() } }

    val count: Flow<Int> = dao.count()
    val oldest: Flow<Long?> = dao.oldest()

    /** Persist posted notifications. Returns rows newly stored (dupes ignored). */
    suspend fun record(records: List<NotificationRecord>): Int {
        if (records.isEmpty()) return 0
        val inserted = dao.insertAll(records.map { it.toEntity() }).count { it >= 0 }
        addsSinceTrim += inserted
        if (addsSinceTrim >= TRIM_EVERY) { addsSinceTrim = 0; dao.trim(_capacity.value) }
        return inserted
    }

    suspend fun record(record: NotificationRecord): Int = record(listOf(record))

    suspend fun snapshot(limit: Int = DISPLAY_MAX): List<NotificationRecord> = dao.snapshot(limit).map { it.toModel() }

    suspend fun topApps(limit: Int = 12): List<OpCount> = dao.topApps(limit).map { it.copy(name = labelFor(it.name)) }
    suspend fun topCategories(limit: Int = 12): List<OpCount> = dao.topCategories(limit)

    suspend fun clear() = dao.clear()
    suspend fun applyCapacity() = dao.trim(_capacity.value)

    /** Record that a notification was dismissed, matched by its system key. */
    suspend fun markRemoved(sbnKey: String, atMs: Long, reason: String) = dao.markRemoved(sbnKey, atMs, reason)

    fun setRunning(running: Boolean) { _running.value = running; prefs.edit().putBoolean(KEY_RUNNING, running).apply() }
    fun setCapacity(value: Int) { _capacity.value = value; prefs.edit().putInt(KEY_CAPACITY, value).apply() }

    private fun labelFor(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { appContext.packageManager.getApplicationLabel(appContext.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    private fun NotificationRecord.toEntity() = NotificationEntity(
        key = key,
        packageName = packageName,
        title = title,
        text = text,
        category = category,
        channelId = channelId,
        postedAt = postedAt,
        ongoing = ongoing,
        subText = subText,
        bigText = bigText,
        summaryText = summaryText,
        infoText = infoText,
        actions = actions,
        progress = progress,
        hasLargeIcon = hasLargeIcon,
        hasBigPicture = hasBigPicture,
        sbnKey = sbnKey,
        removedReason = removedReason,
        removedAt = removedAt,
        flags = flags,
        importance = importance,
        lines = lines,
    )

    private fun NotificationEntity.toModel() = NotificationRecord(
        key = key,
        packageName = packageName,
        label = labelFor(packageName),
        title = title,
        text = text,
        category = category,
        channelId = channelId,
        postedAt = postedAt,
        ongoing = ongoing,
        subText = subText,
        bigText = bigText,
        summaryText = summaryText,
        infoText = infoText,
        actions = actions,
        progress = progress,
        hasLargeIcon = hasLargeIcon,
        hasBigPicture = hasBigPicture,
        sbnKey = sbnKey,
        removedReason = removedReason,
        removedAt = removedAt,
        flags = flags,
        importance = importance,
        lines = lines,
    )

    companion object {
        const val DEFAULT_CAPACITY = 20_000
        val CAPACITIES = listOf(5_000, 20_000, 100_000)
        private const val DISPLAY_MAX = 3_000
        private const val TRIM_EVERY = 200
        private const val KEY_CAPACITY = "capacity"
        private const val KEY_RUNNING = "running"
    }
}
