package com.snatik.storage.app.feature.intents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.apps.AppRepository
import com.snatik.storage.core.apps.AppSummary
import com.snatik.storage.core.apps.IntentFilterInspector
import com.snatik.storage.core.apps.ReceivableIntent
import com.snatik.storage.core.intents.IntentSpec
import com.snatik.storage.core.intents.SendAs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IntentDiscoverUiState(
    val apps: List<AppSummary> = emptyList(),
    val query: String = "",
    val loadingApps: Boolean = true,
    val selected: AppSummary? = null,
    val inspecting: Boolean = false,
    val receivable: List<ReceivableIntent> = emptyList(),
) {
    val visibleApps: List<AppSummary>
        get() = if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
        }
}

class IntentDiscoverViewModel(private val apps: AppRepository, private val inspector: IntentFilterInspector) : ViewModel() {

    private val _state = MutableStateFlow(IntentDiscoverUiState())
    val state: StateFlow<IntentDiscoverUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(apps = apps.list(), loadingApps = false) }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun select(app: AppSummary) {
        _state.update { it.copy(selected = app, inspecting = true, receivable = emptyList()) }
        viewModelScope.launch {
            val found = inspector.inspect(app.packageName)
            _state.update { it.copy(inspecting = false, receivable = found) }
        }
    }

    fun clearSelection() = _state.update { it.copy(selected = null, receivable = emptyList()) }

    /** Turn a discovered filter into a builder spec targeting that specific component. */
    fun specFor(ri: ReceivableIntent): String = IntentSpec(
        action = ri.action,
        data = ri.sampleData,
        type = ri.mimeTypes.firstOrNull(),
        categories = ri.categories,
        packageName = _state.value.selected?.packageName,
        className = ri.className,
        sendAs = when (ri.componentType) {
            "receiver" -> SendAs.BROADCAST
            "service" -> SendAs.SERVICE
            else -> SendAs.ACTIVITY
        },
    ).toJson()
}
