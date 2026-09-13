package com.snatik.storage.core.intents

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared, in-memory buffer of monitored intents plus whether the background monitor service is
 * running. Held as a singleton so the service (collector) and the screen (viewer) share one list.
 */
class IntentMonitorStore {

    private val _intents = MutableStateFlow<List<MonitoredIntent>>(emptyList())
    val intents: StateFlow<List<MonitoredIntent>> = _intents.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun add(intent: MonitoredIntent) = _intents.update { (listOf(intent) + it).take(MAX) }

    fun setRunning(running: Boolean) { _running.value = running }

    fun clear() { _intents.value = emptyList() }

    companion object {
        const val MAX = 1000
    }
}
