package com.snatik.storage.app.feature.monitor

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A well-known content URI worth watching for change notifications. */
data class WatchTarget(val label: String, val uri: String)

/** One change tick observed on a watched URI. */
data class ProviderEvent(val uri: String, val at: Long, val target: String)

val COMMON_PROVIDERS = listOf(
    WatchTarget("Media · Images", "content://media/external/images/media"),
    WatchTarget("Media · Video", "content://media/external/video/media"),
    WatchTarget("Media · Audio", "content://media/external/audio/media"),
    WatchTarget("Media · Downloads", "content://media/external/downloads"),
    WatchTarget("Media · Files", "content://media/external/file"),
    WatchTarget("Contacts", "content://com.android.contacts/contacts"),
    WatchTarget("Call log", "content://call_log/calls"),
    WatchTarget("SMS", "content://sms"),
    WatchTarget("Calendar events", "content://com.android.calendar/events"),
    WatchTarget("Settings · System", "content://settings/system"),
    WatchTarget("Settings · Secure", "content://settings/secure"),
    WatchTarget("Settings · Global", "content://settings/global"),
)

/**
 * Registers [ContentObserver]s on chosen content URIs and logs every change notification, so you
 * can see which providers churn and when. In-process only; the log resets each launch.
 */
class ProviderWatcher(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val observers = mutableMapOf<String, ContentObserver>()

    private val _watching = MutableStateFlow<Set<String>>(emptySet())
    val watching: StateFlow<Set<String>> = _watching.asStateFlow()

    private val _events = MutableStateFlow<List<ProviderEvent>>(emptyList())
    val events: StateFlow<List<ProviderEvent>> = _events.asStateFlow()

    fun toggle(target: WatchTarget) {
        if (target.uri in observers) stop(target) else start(target)
    }

    private fun start(target: WatchTarget) {
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                _events.value = (listOf(ProviderEvent(uri?.toString() ?: target.uri, System.currentTimeMillis(), target.label)) + _events.value).take(500)
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(Uri.parse(target.uri), true, observer)
            observers[target.uri] = observer
            _watching.value = _watching.value + target.uri
        }
    }

    private fun stop(target: WatchTarget) {
        observers.remove(target.uri)?.let { context.contentResolver.unregisterContentObserver(it) }
        _watching.value = _watching.value - target.uri
    }

    fun clearLog() {
        _events.value = emptyList()
    }

    fun stopAll() {
        observers.values.forEach { context.contentResolver.unregisterContentObserver(it) }
        observers.clear()
        _watching.value = emptySet()
    }
}
