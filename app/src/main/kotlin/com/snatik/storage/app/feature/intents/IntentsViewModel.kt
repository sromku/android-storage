package com.snatik.storage.app.feature.intents

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.intents.BroadcastStore
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
    val shellAvailable: Boolean = false,
    val appCount: Int = 0,
    val monitorCaptured: Int = 0,
    val monitorRunning: Boolean = false,
    val broadcastCaptured: Int = 0,
    val broadcastRunning: Boolean = false,
    val broadcastActive: Int = 0,
)

class IntentsViewModel(
    private val context: Context,
    private val presets: IntentPresets,
    private val privilege: PrivilegeManager,
    private val intentStore: IntentMonitorStore,
    private val broadcastStore: BroadcastStore,
) : ViewModel() {

    private val appCount = MutableStateFlow(0)

    val state: StateFlow<IntentsUiState> = combine(
        presets.presets, privilege.executor, appCount,
        intentStore.count, intentStore.running,
        broadcastStore.count, broadcastStore.running, broadcastStore.active,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        IntentsUiState(
            presets = values[0] as List<IntentPreset>,
            shellAvailable = values[1] != null,
            appCount = values[2] as Int,
            monitorCaptured = values[3] as Int,
            monitorRunning = values[4] as Boolean,
            broadcastCaptured = values[5] as Int,
            broadcastRunning = values[6] as Boolean,
            broadcastActive = (values[7] as List<*>).size,
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
