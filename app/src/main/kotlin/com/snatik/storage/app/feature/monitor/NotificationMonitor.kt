package com.snatik.storage.app.feature.monitor

import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** One notification seen device-wide. */
data class NotificationRecord(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val ongoing: Boolean,
    val removed: Boolean = false,
)

/**
 * A process-lived log of every notification posted while the listener is connected. The service
 * feeds it; the UI reads it. Not persisted: it starts empty each launch and holds the most recent
 * entries. Requires the user to grant notification access in system settings.
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
        _entries.value = _entries.value.map { if (it.key == key && !it.removed) it.copy(removed = true) else it }
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

/** System-bound listener that mirrors posted and removed notifications into [NotificationLog]. */
class NotificationMonitorService : NotificationListenerService(), KoinComponent {

    private val sink: com.snatik.storage.core.apps.ExternalSink by inject()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun onListenerConnected() {
        NotificationLog.connected.value = true
        runCatching { activeNotifications }.getOrNull()?.forEach { record(it) }
    }

    override fun onListenerDisconnected() {
        NotificationLog.connected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = record(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationLog.markRemoved(sbn.key)
    }

    private fun record(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras?.getCharSequence("android.text")?.toString()
            ?: extras?.getCharSequence("android.bigText")?.toString().orEmpty()
        val record = NotificationRecord(
            key = sbn.key ?: (sbn.packageName + sbn.postTime),
            packageName = sbn.packageName,
            title = title,
            text = text,
            postedAt = sbn.postTime,
            ongoing = sbn.isOngoing,
        )
        NotificationLog.add(record)
        if (sink.enabled) scope.launch {
            runCatching {
                sink.send("notifications", listOf(
                    org.json.JSONObject()
                        .put("at_ms", record.postedAt)
                        .put("package", record.packageName)
                        .put("title", record.title)
                        .put("text", record.text)
                        .put("ongoing", record.ongoing)
                        .put("key", record.key),
                ))
            }
        }
    }
}
