package com.snatik.storage.app.feature.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.apps.AppNetworkUsage
import com.snatik.storage.core.apps.AsnDb
import com.snatik.storage.core.apps.NetworkInspector
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetworkUiState(
    val apps: List<AppNetworkUsage> = emptyList(),
    val hostNames: Map<String, String> = emptyMap(),
    val orgNames: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val shellAvailable: Boolean = true,
    val query: String = "",
) {
    val visible: List<AppNetworkUsage>
        get() = if (query.isBlank()) apps else apps.filter { a ->
            a.label.contains(query, true) || a.packageName.contains(query, true) ||
                a.connections.any { it.remoteAddress.contains(query, true) || hostNames[it.remoteAddress]?.contains(query, true) == true }
        }

    fun forPackage(pkg: String): AppNetworkUsage? = apps.firstOrNull { it.packageName == pkg }
}

class NetworkViewModel(
    private val inspector: NetworkInspector,
    private val privilege: PrivilegeManager,
    private val asnDb: AsnDb,
    private val prefs: NetworkPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(NetworkUiState())
    val state: StateFlow<NetworkUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            if (privilege.executor.value == null) {
                _state.update { it.copy(loading = false, shellAvailable = false) }
                return@launch
            }
            val apps = inspector.usage()
            _state.update { it.copy(apps = apps, loading = false, shellAvailable = true) }
            val allHosts = apps.flatMap { it.remoteHosts }
            val orgs = runCatching { asnDb.orgs(allHosts) }.getOrDefault(emptyMap())
            if (orgs.isNotEmpty()) _state.update { it.copy(orgNames = it.orgNames + orgs) }
            // Reverse-DNS is the app's only self-inflicted traffic while browsing; gate it.
            if (prefs.resolveHosts.value) {
                val hosts = inspector.resolve(allHosts)
                if (hosts.isNotEmpty()) _state.update { it.copy(hostNames = it.hostNames + hosts) }
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
}
