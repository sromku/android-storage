package com.snatik.storage.app.feature.monitor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
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

    /** Mark the most recent still-live entry with this system key as dismissed. */
    fun markRemoved(sbnKey: String, reason: String, atMs: Long) {
        var done = false
        _entries.value = _entries.value.map {
            if (!done && it.sbnKey == sbnKey && !it.removed) { done = true; it.copy(removedReason = reason, removedAt = atMs) } else it
        }
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

/** The rich bitmaps a notification carries: its large icon and its expanded big picture. */
data class NotificationImages(val largeIcon: Bitmap?, val bigPicture: Bitmap?)

/**
 * A small LRU cache of notification bitmaps keyed by record key. Bitmaps are heavy and not
 * persistable, so instead of holding them in the 500-entry log we keep only the most recent handful
 * here — enough for the detail sheet of anything you just tapped, without unbounded memory.
 */
object NotificationImageCache {
    private const val MAX = 24
    private val map = object : LinkedHashMap<String, NotificationImages>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NotificationImages>) = size > MAX
    }

    @Synchronized fun put(key: String, images: NotificationImages) {
        if (images.largeIcon != null || images.bigPicture != null) map[key] = images
    }

    @Synchronized fun get(key: String): NotificationImages? = map[key]
}

/**
 * System-bound listener that mirrors posted notifications into the live [NotificationLog] and, while
 * recording is on, into the persisted [NotificationRecorderStore] (and the external collector when
 * one is configured). Parses each notification's rich content — sub-text, expanded text, actions,
 * progress and images — so the monitor can show the whole notification, not just title and text.
 */
class NotificationMonitorService : NotificationListenerService(), KoinComponent {

    private val sink: ExternalSink by inject()
    private val store: NotificationRecorderStore by inject()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun onListenerConnected() {
        NotificationLog.connected.value = true
        // Re-register the live-stream heartbeat after a process restart, when a recording is running.
        if (store.running.value) sink.streaming("notifications", true)
        runCatching { activeNotifications }.getOrNull()?.let { active ->
            val records = active.map { it.toRecord() }
            records.forEach { NotificationLog.add(it) }
            if (store.running.value) scope.launch {
                val fresh = records.filter { it.capturable() }
                store.record(fresh)
                fresh.forEach { saveThumbnails(it) }
                if (sink.enabled) runCatching { sink.send("notifications", fresh.map { it.toJson() }) }
            }
        }
    }

    override fun onListenerDisconnected() {
        NotificationLog.connected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = record(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        val sbnKey = sbn.key ?: return
        val reasonStr = removalReason(reason)
        val now = System.currentTimeMillis()
        NotificationLog.markRemoved(sbnKey, reasonStr, now)
        if (store.running.value) scope.launch { runCatching { store.markRemoved(sbnKey, now, reasonStr) } }
    }

    private fun record(sbn: StatusBarNotification) {
        val record = sbn.toRecord()
        NotificationLog.add(record)
        if (store.running.value && record.capturable()) scope.launch {
            store.record(record)
            saveThumbnails(record)
            if (sink.enabled) runCatching { sink.send("notifications", listOf(record.toJson())) }
        }
    }

    private fun saveThumbnails(record: NotificationRecord) {
        if (record.hasLargeIcon || record.hasBigPicture) {
            NotificationImageCache.get(record.key)?.let { NotificationThumbnails.save(this, record.key, it) }
        }
    }

    /** Human label for a NotificationListenerService.REASON_* dismissal code. */
    private fun removalReason(reason: Int): String = when (reason) {
        REASON_CLICK -> "tapped"
        REASON_CANCEL -> "dismissed"
        REASON_CANCEL_ALL -> "cleared all"
        REASON_APP_CANCEL -> "app removed"
        REASON_APP_CANCEL_ALL -> "app cleared"
        REASON_LISTENER_CANCEL, REASON_LISTENER_CANCEL_ALL -> "removed by app"
        REASON_SNOOZED -> "snoozed"
        REASON_TIMEOUT -> "timed out"
        REASON_GROUP_SUMMARY_CANCELED, REASON_GROUP_OPTIMIZATION -> "grouped"
        REASON_ERROR -> "error"
        else -> "removed"
    }

    // Persist/stream every notification except our own — so the recorder isn't polluted by this app's
    // own service notifications, and there's no self-logging loop. The one exception is the user-fired
    // test notification (its dedicated channel), which is meant to be captured and streamed.
    private fun NotificationRecord.capturable(): Boolean =
        packageName != this@NotificationMonitorService.packageName || channelId == TestNotification.CHANNEL

    private fun StatusBarNotification.toRecord(): NotificationRecord {
        val n = notification
        val extras = n?.extras
        fun str(key: String) = extras?.getCharSequence(key)?.toString().orEmpty()
        val title = str("android.title")
        val text = str("android.text")
        val bigText = str("android.bigText")
        val actions = n?.actions?.mapNotNull { it.title?.toString() }?.filter { it.isNotBlank() }?.joinToString(" · ").orEmpty()
        val progress = run {
            val max = extras?.getInt("android.progressMax", 0) ?: 0
            val cur = extras?.getInt("android.progress", 0) ?: 0
            val indeterminate = extras?.getBoolean("android.progressIndeterminate", false) ?: false
            when {
                indeterminate -> "…"
                max > 0 -> "$cur/$max"
                else -> ""
            }
        }
        val largeIcon = n?.let { iconToBitmap(it.getLargeIcon()) } ?: (extras?.get("android.largeIcon") as? Bitmap)
        // BigPictureStyle stores a Bitmap under "android.picture" on older releases and an Icon under
        // "android.pictureIcon" from Android 12+ — try both.
        val bigPicture = bitmapExtra(extras, "android.picture") ?: iconToBitmap(iconExtra(extras, "android.pictureIcon"))
        val sbnKey = key ?: (packageName + postTime)
        val recordKey = sbnKey + "|" + postTime
        NotificationImageCache.put(recordKey, NotificationImages(largeIcon, bigPicture))

        return NotificationRecord(
            key = recordKey,
            packageName = packageName,
            title = title,
            text = text.ifEmpty { bigText },
            category = n?.category,
            channelId = n?.channelId.orEmpty(),
            postedAt = postTime,
            ongoing = isOngoing,
            subText = str("android.subText"),
            bigText = bigText,
            summaryText = str("android.summaryText"),
            infoText = str("android.infoText"),
            actions = actions,
            progress = progress,
            hasLargeIcon = largeIcon != null,
            hasBigPicture = bigPicture != null,
            sbnKey = sbnKey,
            flags = n?.flags ?: 0,
            importance = importanceOf(key),
            lines = extractLines(extras),
        )
    }

    /** Channel importance for this notification, from the current ranking, or unspecified. */
    private fun importanceOf(sbnKey: String?): Int {
        sbnKey ?: return NotificationRecord.IMPORTANCE_UNSPECIFIED
        val ranking = Ranking()
        return if (currentRanking.getRanking(sbnKey, ranking)) ranking.importance else NotificationRecord.IMPORTANCE_UNSPECIFIED
    }

    /** MessagingStyle messages ("Sender: text") and InboxStyle lines, newline-joined and capped. */
    private fun extractLines(extras: android.os.Bundle?): String {
        extras ?: return ""
        val out = ArrayList<String>()
        parcelableArray(extras, "android.messages")?.forEach { p ->
            (p as? android.os.Bundle)?.let { b ->
                val t = b.getCharSequence("text")?.toString().orEmpty()
                if (t.isNotBlank()) {
                    val sender = b.getCharSequence("sender")?.toString() ?: personName(b)
                    out.add(if (!sender.isNullOrBlank()) "$sender: $t" else t)
                }
            }
        }
        if (out.isEmpty()) {
            extras.getCharSequenceArray("android.textLines")?.forEach { cs ->
                cs?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            }
        }
        return out.joinToString("\n").take(2000)
    }

    @Suppress("DEPRECATION")
    private fun personName(b: android.os.Bundle): String? {
        val person = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) b.getParcelable("sender_person", android.app.Person::class.java) else b.getParcelable("sender_person") as? android.app.Person
        return person?.name?.toString()
    }

    @Suppress("DEPRECATION")
    private fun parcelableArray(extras: android.os.Bundle, key: String): Array<out android.os.Parcelable>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) extras.getParcelableArray(key, android.os.Parcelable::class.java)
        else extras.getParcelableArray(key)

    private fun iconToBitmap(icon: Icon?): Bitmap? {
        icon ?: return null
        val drawable = runCatching { icon.loadDrawable(this) }.getOrNull() ?: return null
        return drawable.toBitmapOrNull()
    }

    private fun Drawable.toBitmapOrNull(max: Int = 256): Bitmap? {
        (this as? BitmapDrawable)?.bitmap?.let { return it }
        val w = intrinsicWidth.takeIf { it > 0 } ?: max
        val h = intrinsicHeight.takeIf { it > 0 } ?: max
        return runCatching {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            setBounds(0, 0, w, h)
            draw(canvas)
            bmp
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun bitmapExtra(extras: android.os.Bundle?, key: String): Bitmap? {
        extras ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) extras.getParcelable(key, Bitmap::class.java)
        else extras.getParcelable(key) as? Bitmap
    }

    @Suppress("DEPRECATION")
    private fun iconExtra(extras: android.os.Bundle?, key: String): Icon? {
        extras ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) extras.getParcelable(key, Icon::class.java)
        else extras.getParcelable(key) as? Icon
    }

    private fun NotificationRecord.toJson() = org.json.JSONObject()
        .put("at_ms", postedAt)
        .put("package", packageName)
        .put("title", title)
        .put("text", text)
        .put("sub_text", subText)
        .put("big_text", bigText)
        .put("summary", summaryText)
        .put("actions", actions)
        .put("progress", progress)
        .put("lines", lines)
        .put("has_image", hasLargeIcon || hasBigPicture)
        .put("importance", importance)
        .put("flags", flags)
        .put("category", category ?: org.json.JSONObject.NULL)
        .put("channel", channelId)
        .put("ongoing", ongoing)
        .put("key", key)
}
