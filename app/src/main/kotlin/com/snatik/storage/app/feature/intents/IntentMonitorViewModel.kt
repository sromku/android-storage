package com.snatik.storage.app.feature.intents

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.intents.IntentMonitorStore
import com.snatik.storage.core.intents.MonitoredIntent
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MonitorUiState(
    val shellAvailable: Boolean = false,
    val running: Boolean = false,
    val hideSelf: Boolean = true,
    val query: String = "",
    val intents: List<MonitoredIntent> = emptyList(),
)

/**
 * Drives the background [IntentMonitorService] and reflects the shared [IntentMonitorStore] it fills.
 * The collection itself lives in the service, so it keeps running when the app is closed.
 */
class IntentMonitorViewModel(
    private val context: Context,
    privilege: PrivilegeManager,
    private val store: IntentMonitorStore,
) : ViewModel() {

    private val hideSelf = MutableStateFlow(true)
    private val query = MutableStateFlow("")

    val state: StateFlow<MonitorUiState> = combine(store.intents, store.running, privilege.executor, hideSelf, query) { intents, running, exec, hide, q ->
        MonitorUiState(shellAvailable = exec != null, running = running, hideSelf = hide, query = q, intents = intents)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitorUiState())

    fun toggle() {
        if (store.running.value) IntentMonitorService.stop(context) else IntentMonitorService.start(context)
    }

    fun clear() = store.clear()
    fun setHideSelf(on: Boolean) { hideSelf.value = on }
    fun setQuery(q: String) { query.value = q }
}
