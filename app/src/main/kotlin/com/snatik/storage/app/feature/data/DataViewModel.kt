package com.snatik.storage.app.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.data.ProviderEntry
import com.snatik.storage.core.data.ProviderRepository
import com.snatik.storage.core.data.ProviderShortcut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProviderSource { ALL, SYSTEM, APPS }

data class DataUiState(
    val shortcuts: List<ProviderShortcut> = emptyList(),
    val providers: List<ProviderEntry> = emptyList(),
    val query: String = "",
    val source: ProviderSource = ProviderSource.ALL,
    val groupByApp: Boolean = false,
    val onlyExported: Boolean = false,
    val onlyPermission: Boolean = false,
    val onlyGrantsUri: Boolean = false,
    val onlyQueryable: Boolean = false,
    val loading: Boolean = true,
) {
    val visible: List<ProviderEntry>
        get() = providers.filter { p ->
            when (source) {
                ProviderSource.SYSTEM -> p.isSystem
                ProviderSource.APPS -> !p.isSystem
                ProviderSource.ALL -> true
            } &&
                (!onlyExported || p.exported) &&
                (!onlyPermission || p.hasPermission) &&
                (!onlyGrantsUri || p.grantUriPermissions) &&
                (!onlyQueryable || p.queryableNow) &&
                (query.isBlank() ||
                    p.authority.contains(query, true) || p.appLabel.contains(query, true) || p.packageName.contains(query, true))
        }

    /** For the grouped view: providers bucketed by app, app groups ordered by name. */
    val grouped: List<Pair<String, List<ProviderEntry>>>
        get() = visible.groupBy { it.appLabel }.entries.sortedBy { it.key.lowercase() }.map { it.key to it.value }
}

class DataViewModel(private val repository: ProviderRepository) : ViewModel() {

    private val _state = MutableStateFlow(DataUiState(shortcuts = repository.shortcuts()))
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val providers = repository.list()
            _state.update { it.copy(providers = providers, loading = false) }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }
    fun setSource(source: ProviderSource) = _state.update { it.copy(source = source) }
    fun toggleGroupByApp() = _state.update { it.copy(groupByApp = !it.groupByApp) }
    fun toggleExported() = _state.update { it.copy(onlyExported = !it.onlyExported) }
    fun togglePermission() = _state.update { it.copy(onlyPermission = !it.onlyPermission) }
    fun toggleGrantsUri() = _state.update { it.copy(onlyGrantsUri = !it.onlyGrantsUri) }
    fun toggleQueryable() = _state.update { it.copy(onlyQueryable = !it.onlyQueryable) }
}
