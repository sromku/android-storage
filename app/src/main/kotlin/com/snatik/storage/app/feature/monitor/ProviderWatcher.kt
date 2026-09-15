package com.snatik.storage.app.feature.monitor

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.snatik.storage.core.apps.ExternalSink
import com.snatik.storage.core.apps.ProviderChange
import com.snatik.storage.core.apps.ProviderRecorderStore
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The kind of change a provider signalled. INSERT/UPDATE/DELETE need Android 11+; else UNKNOWN. */
enum class ProviderOp { INSERT, UPDATE, DELETE, UNKNOWN }

/** A content URI worth watching, with the read permission (if any) its changes require. */
data class WatchTarget(val label: String, val uri: String, val permission: String? = null)

/** A content provider installed on the device, offered for watching. */
data class ProviderTarget(val authority: String, val pkg: String, val readPermission: String?)

val COMMON_PROVIDERS = listOf(
    WatchTarget("Media · Images", "content://media/external/images/media", "android.permission.READ_MEDIA_IMAGES"),
    WatchTarget("Media · Video", "content://media/external/video/media", "android.permission.READ_MEDIA_VIDEO"),
    WatchTarget("Media · Audio", "content://media/external/audio/media", "android.permission.READ_MEDIA_AUDIO"),
    WatchTarget("Media · Downloads", "content://media/external/downloads"),
    WatchTarget("Media · Files", "content://media/external/file"),
    WatchTarget("Contacts", "content://com.android.contacts/contacts", "android.permission.READ_CONTACTS"),
    WatchTarget("Call log", "content://call_log/calls", "android.permission.READ_CALL_LOG"),
    WatchTarget("SMS", "content://sms", "android.permission.READ_SMS"),
    WatchTarget("Calendar events", "content://com.android.calendar/events", "android.permission.READ_CALENDAR"),
    WatchTarget("Settings · System", "content://settings/system"),
    WatchTarget("Settings · Secure", "content://settings/secure"),
    WatchTarget("Settings · Global", "content://settings/global"),
)

/**
 * Registers [ContentObserver]s on chosen content URIs and logs every change — with its operation
 * (insert / update / delete) on Android 11+. Feeds a live in-process log for the "Live" view and,
 * while recording, the persisted [ProviderRecorderStore] (and the external collector). The watched
 * set is persisted so a foreground service can re-attach the observers after the app closes.
 */
class ProviderWatcher(
    private val context: Context,
    private val store: ProviderRecorderStore,
    private val sink: ExternalSink,
    private val privilege: PrivilegeManager,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val observers = mutableMapOf<String, ContentObserver>()
    private val prefs = context.applicationContext.getSharedPreferences("provider_watch", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _watching = MutableStateFlow(loadWatched().keys)
    val watching: StateFlow<Set<String>> = _watching.asStateFlow()

    private val _events = MutableStateFlow<List<ProviderChange>>(emptyList())
    val events: StateFlow<List<ProviderChange>> = _events.asStateFlow()

    /** Suspends to self-grant the read permission (via Shizuku) when needed, then starts observing. */
    suspend fun watch(target: WatchTarget) {
        grantIfNeeded(target.permission)
        start(target)
    }

    fun unwatch(uri: String) {
        observers.remove(uri)?.let { context.contentResolver.unregisterContentObserver(it) }
        val map = loadWatched().toMutableMap().also { it.remove(uri) }
        saveWatched(map)
        _watching.value = map.keys
    }

    /** (Re)attach observers for every persisted watched target — used on service start / screen open. */
    fun ensureObservers() {
        loadWatched().forEach { (uri, label) ->
            if (uri !in observers) start(WatchTarget(label, uri))
        }
        _watching.value = loadWatched().keys
    }

    private fun start(target: WatchTarget) {
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = record(target, uri, ProviderOp.UNKNOWN)
            override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
                val op = opOf(flags)
                if (uris.isEmpty()) record(target, null, op) else uris.forEach { record(target, it, op) }
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(Uri.parse(target.uri), true, observer)
            observers[target.uri] = observer
            val map = loadWatched().toMutableMap().also { it[target.uri] = target.label }
            saveWatched(map)
            _watching.value = map.keys
        }
    }

    private fun record(target: WatchTarget, uri: Uri?, op: ProviderOp) {
        val now = System.currentTimeMillis()
        val uriStr = uri?.toString() ?: target.uri
        val change = ProviderChange(key = "$uriStr|${op.name}|$now", target = target.label, uri = uriStr, op = op.name, atMs = now)
        _events.value = (listOf(change) + _events.value).take(500)
        if (store.running.value) scope.launch {
            store.record(change)
            if (sink.enabled) runCatching { sink.send("providers", listOf(change.toJson())) }
        }
    }

    private fun opOf(flags: Int): ProviderOp = when {
        flags and ContentResolver.NOTIFY_INSERT != 0 -> ProviderOp.INSERT
        flags and ContentResolver.NOTIFY_UPDATE != 0 -> ProviderOp.UPDATE
        flags and ContentResolver.NOTIFY_DELETE != 0 -> ProviderOp.DELETE
        else -> ProviderOp.UNKNOWN
    }

    fun clearLog() { _events.value = emptyList() }

    /** Stop observing everything and forget the watched set. */
    fun stopAll() {
        observers.values.forEach { context.contentResolver.unregisterContentObserver(it) }
        observers.clear()
        saveWatched(emptyMap())
        _watching.value = emptySet()
    }

    /** Whether the read permission for a target is already held (or none is needed). */
    fun granted(target: WatchTarget): Boolean =
        target.permission == null || context.checkSelfPermission(target.permission) == PackageManager.PERMISSION_GRANTED

    val hasShell: Boolean get() = privilege.executor.value != null

    private suspend fun grantIfNeeded(perm: String?) {
        perm ?: return
        if (context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return
        val exec = privilege.executor.value ?: return
        runCatching { exec.run("pm grant ${context.packageName} $perm") }
    }

    /** Content providers installed on the device, for the "discover" picker. */
    fun installedProviders(): List<ProviderTarget> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val pkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PROVIDERS.toLong()))
        else pm.getInstalledPackages(PackageManager.GET_PROVIDERS)
        val out = ArrayList<ProviderTarget>()
        pkgs.forEach { p ->
            p.providers?.forEach { pi ->
                pi.authority?.split(";")?.forEach { auth ->
                    if (auth.isNotBlank()) out.add(ProviderTarget(auth.trim(), p.packageName, pi.readPermission))
                }
            }
        }
        return out.distinctBy { it.authority }.sortedBy { it.authority }
    }

    private fun ProviderChange.toJson() = org.json.JSONObject()
        .put("at_ms", atMs).put("target", target).put("uri", uri).put("op", op).put("key", key)

    // Watched set persisted as uri\tlabel lines so the service can rebuild observers after a restart.
    private fun loadWatched(): Map<String, String> =
        prefs.getStringSet(KEY_WATCHED, emptySet()).orEmpty().mapNotNull { line ->
            val i = line.indexOf('\t'); if (i < 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()

    private fun saveWatched(map: Map<String, String>) {
        prefs.edit().putStringSet(KEY_WATCHED, map.entries.map { "${it.key}\t${it.value}" }.toSet()).apply()
    }

    companion object { private const val KEY_WATCHED = "watched" }
}
