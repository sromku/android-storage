package com.snatik.storage.app.feature.intents

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.intents.BroadcastMonitor
import com.snatik.storage.core.intents.IntentLog
import com.snatik.storage.core.intents.IntentPreset
import com.snatik.storage.core.intents.IntentPresets
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IntentsUiState(
    val sinkEnabled: Boolean = false,
    val captured: Int = 0,
    val presets: List<IntentPreset> = emptyList(),
    val monitorActive: Int = 0,
    val monitorEvents: Int = 0,
    val shellAvailable: Boolean = false,
)

class IntentsViewModel(
    private val context: Context,
    private val log: IntentLog,
    private val presets: IntentPresets,
    private val monitor: BroadcastMonitor,
    private val privilege: PrivilegeManager,
) : ViewModel() {

    private val sinkComponent = ComponentName(context, IntentSinkActivity::class.java)
    private val sinkEnabled = MutableStateFlow(isSinkEnabled())

    val state: StateFlow<IntentsUiState> = combine(sinkEnabled, log.entries, presets.presets, monitor.active, monitor.events, privilege.executor) { values ->
        @Suppress("UNCHECKED_CAST")
        IntentsUiState(
            sinkEnabled = values[0] as Boolean,
            captured = (values[1] as List<*>).size,
            presets = values[2] as List<IntentPreset>,
            monitorActive = (values[3] as Set<*>).size,
            monitorEvents = (values[4] as List<*>).size,
            shellAvailable = values[5] != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IntentsUiState(sinkEnabled = sinkEnabled.value))

    init {
        viewModelScope.launch { log.load(); presets.load() }
    }

    private fun isSinkEnabled(): Boolean =
        context.packageManager.getComponentEnabledSetting(sinkComponent) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setSinkEnabled(enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            sinkComponent,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        sinkEnabled.value = enabled
    }

    fun deletePreset(id: Long) = viewModelScope.launch { presets.delete(id) }
}
