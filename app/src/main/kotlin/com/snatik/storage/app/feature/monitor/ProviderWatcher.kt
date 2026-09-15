package com.snatik.storage.app.feature.monitor

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.snatik.storage.core.apps.ExternalSink
import com.snatik.storage.core.apps.ProviderChange
import com.snatik.storage.core.apps.ProviderRecorderStore
import com.snatik.storage.core.data.ProviderEntry
import com.snatik.storage.core.data.ProviderRepository
import com.snatik.storage.core.data.ProviderShortcut
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

/**
 * Registers [ContentObserver]s on chosen content URIs and logs every change — with its operation
 * (insert / update / delete) on Android 11+. The list of providers to pick from comes from the
 * shared [ProviderRepository] (the same catalogue the Data tab uses), so there's one source of truth
 * for what's on the device. Feeds a live in-process log for the "Live" view and, while recording,
 * the persisted [ProviderRecorderStore] (and the external collector). The watched set is persisted
 * so a foreground service can re-attach the observers after the app closes.
 */
class ProviderWatcher(
    private val context: Context,
    private val store: ProviderRecorderStore,
    private val sink: ExternalSink,
    private val privilege: PrivilegeManager,
    private val repo: ProviderRepository,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val observers = mutableMapOf<String, ContentObserver>()
    private val prefs = context.applicationContext.getSharedPreferences("provider_watch", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _watching = MutableStateFlow(loadWatched().keys)
    val watching: StateFlow<Set<String>> = _watching.asStateFlow()

    private val _events = MutableStateFlow<List<ProviderChange>>(emptyList())
    val events: StateFlow<List<ProviderChange>> = _events.asStateFlow()

    /** The curated well-known provider URIs, from the shared catalogue. */
    fun shortcuts(): List<ProviderShortcut> = repo.shortcuts()

    /** Every content provider on the device (for the discover picker), from the shared catalogue. */
    suspend fun providers(): List<ProviderEntry> = repo.list()

    /** Self-grants the read permission the provider needs (via Shizuku) when possible, then observes. */
    suspend fun watch(uri: String, label: String) {
        grantFor(uri)
        start(uri, label)
    }

    fun unwatch(uri: String) {
        observers.remove(uri)?.let { context.contentResolver.unregisterContentObserver(it) }
        val map = loadWatched().toMutableMap().also { it.remove(uri) }
        saveWatched(map)
        _watching.value = map.keys
    }

    /** (Re)attach observers for every persisted watched target — used on service start / screen open. */
    fun ensureObservers() {
        loadWatched().forEach { (uri, label) -> if (uri !in observers) start(uri, label) }
        _watching.value = loadWatched().keys
    }

    private fun start(uri: String, label: String) {
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, u: Uri?) = record(label, u?.toString() ?: uri, ProviderOp.UNKNOWN)
            override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
                val op = opOf(flags)
                if (uris.isEmpty()) record(label, uri, op) else uris.forEach { record(label, it.toString(), op) }
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(Uri.parse(uri), true, observer)
            observers[uri] = observer
            val map = loadWatched().toMutableMap().also { it[uri] = label }
            saveWatched(map)
            _watching.value = map.keys
        }
    }

    private fun record(label: String, uri: String, op: ProviderOp) {
        val now = System.currentTimeMillis()
        val change = ProviderChange(key = "$uri|${op.name}|$now", target = label, uri = uri, op = op.name, atMs = now)
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

    fun stopAll() {
        observers.values.forEach { context.contentResolver.unregisterContentObserver(it) }
        observers.clear()
        saveWatched(emptyMap())
        _watching.value = emptySet()
    }

    val hasShell: Boolean get() = privilege.executor.value != null

    /** Grant every read permission this URI's changes require — MediaStore's runtime perms, or the
     *  provider's own declared read permission (resolved from the catalogue). */
    private suspend fun grantFor(uri: String) {
        val perms = LinkedHashSet<String>()
        if (uri.startsWith("content://media")) {
            perms += listOf("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO")
        }
        Uri.parse(uri).authority?.let { auth -> runCatching { repo.resolve(auth)?.readPermission }.getOrNull()?.let { perms += it } }
        val exec = privilege.executor.value
        for (perm in perms) {
            if (context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) continue
            exec ?: continue
            runCatching { exec.run("pm grant ${context.packageName} $perm") }
        }
    }

    private fun ProviderChange.toJson() = org.json.JSONObject()
        .put("at_ms", atMs).put("target", target).put("uri", uri).put("op", op).put("key", key)

    private fun loadWatched(): Map<String, String> =
        prefs.getStringSet(KEY_WATCHED, emptySet()).orEmpty().mapNotNull { line ->
            val i = line.indexOf('\t'); if (i < 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()

    private fun saveWatched(map: Map<String, String>) {
        prefs.edit().putStringSet(KEY_WATCHED, map.entries.map { "${it.key}\t${it.value}" }.toSet()).apply()
    }

    companion object { private const val KEY_WATCHED = "watched" }
}
