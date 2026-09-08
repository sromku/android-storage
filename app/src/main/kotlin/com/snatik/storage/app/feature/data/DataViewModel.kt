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

data class DataUiState(
    val shortcuts: List<ProviderShortcut> = emptyList(),
    val providers: List<ProviderEntry> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
) {
    val visible: List<ProviderEntry>
        get() = if (query.isBlank()) providers else providers.filter {
            it.authority.contains(query, true) || it.appLabel.contains(query, true) || it.packageName.contains(query, true)
        }
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
}
