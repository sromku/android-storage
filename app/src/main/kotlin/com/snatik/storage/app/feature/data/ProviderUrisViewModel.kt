package com.snatik.storage.app.feature.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.core.data.ProviderQuery
import com.snatik.storage.core.data.QueryRequest
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Read-permission state for the provider, so the UI can explain how a query will actually run. */
data class PermStatus(val name: String, val protection: String, val held: Boolean)

/**
 * Pure helpers for turning a discovered pattern path (with `#`/`*` segments) into a query against
 * the parent collection, and back into a concrete path once a real value is chosen.
 */
object PatternUris {
    private fun segments(path: String) = path.split('/')

    fun firstWildcard(path: String): Char? = segments(path).firstOrNull { it == "#" || it == "*" }?.first()

    /** Segments before the first wildcard — the collection whose rows supply the wildcard's value. */
    fun parentPath(path: String): String {
        val segs = segments(path)
        val i = segs.indexOfFirst { it == "#" || it == "*" }
        return if (i <= 0) segs.take(maxOf(i, 0)).joinToString("/") else segs.take(i).joinToString("/")
    }

    /** Substitute the first wildcard segment of [path] with [value]. */
    fun substitute(path: String, value: String): String {
        val segs = segments(path).toMutableList()
        val i = segs.indexOfFirst { it == "#" || it == "*" }
        if (i >= 0) segs[i] = value
        return segs.joinToString("/")
    }

    /** For `#` prefer a numeric id column; for `*` a text key. Falls back to _id then first column. */
    fun pickIdColumn(columns: List<String>, wildcard: Char): String? {
        if (columns.isEmpty()) return null
        val lower = columns.associateBy { it.lowercase() }
        if (wildcard == '*') listOf("lookup", "_id", "name", "key").forEach { lower[it]?.let { c -> return c } }
        return lower["_id"] ?: columns.firstOrNull()
    }

    /** A human-readable column to show beside each value, if the collection has one. */
    fun pickLabelColumn(columns: List<String>, idCol: String?): String? {
        val lower = columns.associateBy { it.lowercase() }
        return listOf("display_name", "_display_name", "title", "name", "label", "bucket_display_name")
            .firstNotNullOfOrNull { lower[it] }
            ?.takeIf { it != idCol }
    }
}

/** One candidate value for a `#`/`*` wildcard, discovered by querying the parent collection. */
data class WildcardValue(val value: String, val label: String?)

/** Open when resolving a pattern URI into concrete ones. */
data class ResolveState(
    val path: String,
    val wildcard: Char,
    val loading: Boolean = true,
    val values: List<WildcardValue> = emptyList(),
    val error: String? = null,
)

data class UrisUiState(
    val running: Boolean = true,
    val phase: UriDiscovery.Phase = UriDiscovery.Phase.READING,
    val scanned: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val dexIndex: Int = 0,
    val dexCount: Int = 0,
    val paths: List<String> = emptyList(),
    val error: String? = null,
    /** True once a scoped (app-code-only) scan finished with no results — offer a full scan. */
    val offerScanAll: Boolean = false,
    /** When these paths came from a previous run: epoch millis of that run, else null. */
    val lastDiscoveredAt: Long? = null,
    /** Whether the shown paths came from a full (all-classes) scan. */
    val scannedAll: Boolean = false,
    /** URIs already saved to Quick queries. */
    val savedUris: Set<String> = emptySet(),
    val permission: PermStatus? = null,
    val privileged: Boolean = false,
    val resolve: ResolveState? = null,
)

class ProviderUrisViewModel(
    route: Route.ProviderUris,
    private val context: Context,
    private val discovery: UriDiscovery,
    private val discoveryStore: UriDiscoveryStore,
    private val savedStore: SavedQueryStore,
    private val query: ProviderQuery,
    private val privilege: PrivilegeManager,
) : ViewModel() {

    val authority = route.authority
    val label = route.label
    private val pkg = route.packageName
    private val providerClass = route.className

    private val _state = MutableStateFlow(
        UrisUiState(
            running = false,
            permission = permStatus(route.readPermission),
            privileged = privilege.state.value.isPrivileged,
        )
    )
    val state: StateFlow<UrisUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            savedStore.saved.collect { saved -> _state.update { it.copy(savedUris = saved.map { s -> s.uri }.toSet()) } }
        }
        val cached = discoveryStore.get(providerClass, authority)
        if (cached != null) {
            _state.update {
                it.copy(
                    running = false,
                    paths = cached.paths,
                    found = cached.paths.size,
                    lastDiscoveredAt = cached.discoveredAt,
                    scannedAll = cached.scannedAll,
                    offerScanAll = !cached.scannedAll && cached.paths.isEmpty(),
                )
            }
        } else {
            start(scanAll = false)
        }
    }

    fun start(scanAll: Boolean) {
        job?.cancel()
        _state.update { it.copy(running = true, phase = UriDiscovery.Phase.READING, paths = emptyList(), found = 0, error = null, offerScanAll = false, lastDiscoveredAt = null) }
        job = viewModelScope.launch {
            try {
                discovery.discover(pkg, providerClass, authority, scanAll).collect { e ->
                    when (e) {
                        is UriDiscovery.Event.Status -> _state.update { it.copy(phase = e.phase, scanned = e.scanned, total = e.total, found = e.found, dexIndex = e.dexIndex, dexCount = e.dexCount) }
                        is UriDiscovery.Event.Done -> {
                            discoveryStore.put(UriDiscoveryStore.Record(providerClass, authority, e.paths, System.currentTimeMillis(), scannedAll = !e.scopedOnly))
                            _state.update {
                                it.copy(
                                    running = false,
                                    paths = e.paths,
                                    found = e.paths.size,
                                    scannedAll = !e.scopedOnly,
                                    lastDiscoveredAt = System.currentTimeMillis(),
                                    offerScanAll = e.scopedOnly && e.paths.isEmpty(),
                                )
                            }
                        }
                    }
                }
            } catch (e: UriDiscovery.DiscoveryException) {
                _state.update { it.copy(running = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(running = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun rediscover() = start(scanAll = _state.value.scannedAll)
    fun scanAll() = start(scanAll = true)

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun uriFor(path: String) = "content://$authority/$path"

    fun toggleSave(path: String) {
        val uri = uriFor(path)
        if (savedStore.isSaved(uri)) savedStore.remove(uri)
        else savedStore.save(title = path, uri = uri, group = "${SavedQueryStore.DEFAULT_GROUP} · $label")
    }

    // ---- Pattern → concrete value discovery --------------------------------------------------

    /** Open the resolver for a pattern [path]: query its parent collection and surface real values. */
    fun resolvePattern(path: String) {
        val wildcard = PatternUris.firstWildcard(path) ?: return
        val parentPath = PatternUris.parentPath(path)
        val parentUri = if (parentPath.isEmpty()) "content://$authority" else "content://$authority/$parentPath"
        _state.update { it.copy(resolve = ResolveState(path = path, wildcard = wildcard, loading = true)) }
        viewModelScope.launch {
            try {
                val result = query.query(QueryRequest(uri = parentUri, limit = 100))
                val cols = result.columns
                val idCol = PatternUris.pickIdColumn(cols, wildcard)
                val labelCol = PatternUris.pickLabelColumn(cols, idCol)
                val idIdx = cols.indexOf(idCol).takeIf { it >= 0 }
                val labelIdx = labelCol?.let { cols.indexOf(it) }?.takeIf { it >= 0 }
                val values = if (idIdx == null) emptyList() else result.rows.mapNotNull { row ->
                    val v = row.getOrNull(idIdx) ?: return@mapNotNull null
                    WildcardValue(v, labelIdx?.let { row.getOrNull(it) })
                }.distinctBy { it.value }.take(60)
                _state.update { it.copy(resolve = it.resolve?.copy(loading = false, values = values, error = if (values.isEmpty()) "Nothing returned from $parentUri" else null)) }
            } catch (e: ProviderQuery.QueryException) {
                _state.update { it.copy(resolve = it.resolve?.copy(loading = false, error = e.message)) }
            } catch (e: Exception) {
                _state.update { it.copy(resolve = it.resolve?.copy(loading = false, error = e.message ?: e.toString())) }
            }
        }
    }

    fun closeResolve() = _state.update { it.copy(resolve = null) }

    fun concretePath(path: String, value: String): String = PatternUris.substitute(path, value)

    @Suppress("DEPRECATION")
    private fun permStatus(name: String?): PermStatus? {
        if (name == null) return null
        val info = runCatching { context.packageManager.getPermissionInfo(name, 0) }.getOrNull()
        val protection = when (info?.protectionLevel?.and(PermissionInfo.PROTECTION_MASK_BASE)) {
            PermissionInfo.PROTECTION_NORMAL -> "normal"
            PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
            PermissionInfo.PROTECTION_SIGNATURE -> "signature"
            else -> "special"
        }
        val held = context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
        return PermStatus(name, protection, held)
    }

    override fun onCleared() { job?.cancel() }
}
