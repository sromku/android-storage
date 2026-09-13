package com.snatik.storage.app.feature.intents

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.intents.FullDataResult
import com.snatik.storage.core.intents.IntentDataResolver
import com.snatik.storage.core.intents.IntentMonitorStore
import com.snatik.storage.core.intents.MonitoredIntent
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MonitorUiState(
    val shellAvailable: Boolean = false,
    val running: Boolean = false,
    val hideSelf: Boolean = true,
    val query: String = "",
    val intents: List<MonitoredIntent> = emptyList(),
    val count: Int = 0,
    val capacity: Int = IntentMonitorStore.DEFAULT_CAPACITY,
)

/**
 * Drives the background [IntentMonitorService] and reflects the SQLite-backed [IntentMonitorStore].
 * Collection lives in the service, so it keeps running when the app is closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntentMonitorViewModel(
    private val context: Context,
    private val privilege: PrivilegeManager,
    private val store: IntentMonitorStore,
) : ViewModel() {

    private val hideSelf = MutableStateFlow(true)
    private val query = MutableStateFlow("")
    private val refreshTrigger = MutableStateFlow(0)
    private val resolver = IntentDataResolver()

    private val monitor = refreshTrigger.flatMapLatest {
        combine(store.recent, store.count, store.running, store.capacity) { intents, count, running, capacity ->
            Monitor(intents, count, running, capacity)
        }
    }
    private val filters = combine(hideSelf, query, privilege.executor) { hide, q, exec ->
        Filters(hide, q, exec != null)
    }

    val state: StateFlow<MonitorUiState> = combine(monitor, filters) { m, f ->
        MonitorUiState(
            shellAvailable = f.shellAvailable, running = m.running, hideSelf = f.hideSelf,
            query = f.query, intents = m.intents, count = m.count, capacity = m.capacity,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitorUiState())

    val capacities: List<Int> = IntentMonitorStore.CAPACITIES

    fun start(capacity: Int) {
        store.setCapacity(capacity)
        viewModelScope.launch { store.applyCapacity() }
        IntentMonitorService.start(context)
    }

    fun stop() = IntentMonitorService.stop(context)

    suspend fun resolveFullData(intent: MonitoredIntent): FullDataResult {
        val shell = privilege.executor.value ?: return FullDataResult.NotFound
        return resolver.resolve(shell, intent)
    }

    fun refresh() { refreshTrigger.value++ }
    fun clear() { viewModelScope.launch { store.clear() } }
    fun setHideSelf(on: Boolean) { hideSelf.value = on }
    fun setQuery(q: String) { query.value = q }

    private data class Monitor(val intents: List<MonitoredIntent>, val count: Int, val running: Boolean, val capacity: Int)
    private data class Filters(val hideSelf: Boolean, val query: String, val shellAvailable: Boolean)
}
