package com.snatik.storage.app.feature.intents

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.intents.BroadcastMonitor
import com.snatik.storage.core.intents.IntentMonitorStore
import com.snatik.storage.core.intents.IntentPreset
import com.snatik.storage.core.intents.IntentPresets
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IntentsUiState(
    val presets: List<IntentPreset> = emptyList(),
    val monitorActive: Int = 0,
    val monitorEvents: Int = 0,
    val shellAvailable: Boolean = false,
    val appCount: Int = 0,
    val monitorCaptured: Int = 0,
    val monitorRunning: Boolean = false,
)

class IntentsViewModel(
    private val context: Context,
    private val presets: IntentPresets,
    private val monitor: BroadcastMonitor,
    private val privilege: PrivilegeManager,
    private val monitorStore: IntentMonitorStore,
) : ViewModel() {

    private val appCount = MutableStateFlow(0)

    val state: StateFlow<IntentsUiState> = combine(presets.presets, monitor.active, monitor.events, privilege.executor, appCount, monitorStore.count, monitorStore.running) { values ->
        @Suppress("UNCHECKED_CAST")
        IntentsUiState(
            presets = values[0] as List<IntentPreset>,
            monitorActive = (values[1] as Set<*>).size,
            monitorEvents = (values[2] as List<*>).size,
            shellAvailable = values[3] != null,
            appCount = values[4] as Int,
            monitorCaptured = values[5] as Int,
            monitorRunning = values[6] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IntentsUiState())

    init {
        viewModelScope.launch { presets.load() }
        viewModelScope.launch {
            appCount.value = withContext(Dispatchers.IO) { context.packageManager.getInstalledApplications(0).size }
        }
    }

    fun deletePreset(id: Long) = viewModelScope.launch { presets.delete(id) }
}
