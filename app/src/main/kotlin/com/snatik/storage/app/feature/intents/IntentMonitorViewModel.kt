package com.snatik.storage.app.feature.intents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.intents.IntentMonitorParser
import com.snatik.storage.core.intents.MonitoredIntent
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.ShellExecutor
import com.snatik.storage.core.shell.lines
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonitorUiState(
    val shellAvailable: Boolean = false,
    val running: Boolean = false,
    val hideSelf: Boolean = true,
    val query: String = "",
    val intents: List<MonitoredIntent> = emptyList(),
)

/**
 * Streams `ActivityTaskManager: START` lines from logcat via the privileged shell and parses them
 * into every activity intent launched on the device, live.
 */
class IntentMonitorViewModel(private val privilege: PrivilegeManager) : ViewModel() {

    private val _state = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var wantRunning = true
    private var counter = 0L

    init {
        viewModelScope.launch {
            privilege.executor.collect { exec ->
                _state.update { it.copy(shellAvailable = exec != null) }
                when {
                    exec != null && wantRunning && job == null -> collect(exec)
                    exec == null -> { job?.cancel(); job = null; _state.update { it.copy(running = false) } }
                }
            }
        }
    }

    private fun collect(exec: ShellExecutor) {
        job = viewModelScope.launch {
            _state.update { it.copy(running = true) }
            runCatching {
                exec.lines("logcat -T 1 -s ActivityTaskManager:I")
                    .mapNotNull { line -> IntentMonitorParser.parse(line, counter++, System.currentTimeMillis()) }
                    .collect { intent -> _state.update { it.copy(intents = (listOf(intent) + it.intents).take(MAX)) } }
            }
            _state.update { it.copy(running = false) }
        }
    }

    fun toggle() {
        if (job != null) {
            wantRunning = false
            job?.cancel(); job = null
            _state.update { it.copy(running = false) }
        } else {
            wantRunning = true
            privilege.executor.value?.let { collect(it) }
        }
    }

    fun clear() = _state.update { it.copy(intents = emptyList()) }
    fun setHideSelf(on: Boolean) = _state.update { it.copy(hideSelf = on) }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        private const val MAX = 500
    }
}
