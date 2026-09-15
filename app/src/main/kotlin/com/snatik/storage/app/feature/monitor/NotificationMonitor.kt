package com.snatik.storage.app.feature.monitor

import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.snatik.storage.core.apps.ExternalSink
import com.snatik.storage.core.apps.NotificationRecord
import com.snatik.storage.core.apps.NotificationRecorderStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * A process-lived log of every notification posted while the listener is connected. Feeds the "Live"
 * source of the monitor screen. Not persisted: it starts empty each launch and holds the most recent
 * entries. The "Recorded" source is the persisted [NotificationRecorderStore] instead.
 */
object NotificationLog {
    private const val CAP = 500
    private val _entries = MutableStateFlow<List<NotificationRecord>>(emptyList())
    val entries: StateFlow<List<NotificationRecord>> = _entries.asStateFlow()

    val connected = MutableStateFlow(false)

    fun add(record: NotificationRecord) {
        _entries.value = (listOf(record) + _entries.value).take(CAP)
    }

    fun markRemoved(key: String) {
        _entries.value = _entries.value.map { if (it.packageName + "|" + it.title == key && !it.removed) it.copy(removed = true) else it }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /** Whether this app currently holds notification-access. */
    fun hasAccess(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { it.substringBefore('/') == context.packageName }
    }
}

/**
 * System-bound listener that mirrors posted notifications into the live [NotificationLog] and, while
 * recording is on, into the persisted [NotificationRecorderStore] (and the external collector when
 * one is configured). The listener is always bound by the system when access is granted, so the
 * persisted record keeps growing even after the app's UI is gone.
 */
class NotificationMonitorService : NotificationListenerService(), KoinComponent {

    private val sink: ExternalSink by inject()
    private val store: NotificationRecorderStore by inject()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun onListenerConnected() {
        NotificationLog.connected.value = true
        // Backfill whatever is currently posted so a fresh recording isn't empty; de-dup handles repeats.
        runCatching { activeNotifications }.getOrNull()?.let { active ->
            val records = active.map { it.toRecord() }
            records.forEach { NotificationLog.add(it) }
            if (store.running.value) scope.launch {
                val fresh = records.filter { it.packageName != packageName }
                store.record(fresh)
                if (sink.enabled) runCatching { sink.send("notifications", fresh.map { it.toJson() }) }
            }
        }
    }

    override fun onListenerDisconnected() {
        NotificationLog.connected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = record(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationLog.markRemoved(sbn.packageName + "|" + (sbn.notification?.extras?.getCharSequence("android.title")?.toString().orEmpty()))
    }

    private fun record(sbn: StatusBarNotification) {
        val record = sbn.toRecord()
        NotificationLog.add(record)
        // Never persist our own notifications — avoids self-logging and any feedback loop.
        if (store.running.value && sbn.packageName != packageName) scope.launch {
            store.record(record)
            if (sink.enabled) runCatching { sink.send("notifications", listOf(record.toJson())) }
        }
    }

    private fun StatusBarNotification.toRecord(): NotificationRecord {
        val extras = notification?.extras
        val title = extras?.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras?.getCharSequence("android.text")?.toString()
            ?: extras?.getCharSequence("android.bigText")?.toString().orEmpty()
        return NotificationRecord(
            // key = smbKey + postTime so each repost is a distinct row, but a re-fed identical
            // posting (e.g. on reconnect) de-dups.
            key = (key ?: (packageName + postTime)) + "|" + postTime,
            packageName = packageName,
            title = title,
            text = text,
            category = notification?.category,
            channelId = notification?.channelId.orEmpty(),
            postedAt = postTime,
            ongoing = isOngoing,
        )
    }

    private fun NotificationRecord.toJson() = org.json.JSONObject()
        .put("at_ms", postedAt)
        .put("package", packageName)
        .put("title", title)
        .put("text", text)
        .put("category", category ?: org.json.JSONObject.NULL)
        .put("channel", channelId)
        .put("ongoing", ongoing)
        .put("key", key)
}
