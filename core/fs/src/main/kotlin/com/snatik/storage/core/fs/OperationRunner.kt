package com.snatik.storage.core.fs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * Runs one file operation at a time in an application scope so it survives screen changes,
 * and exposes its progress and outcome to whoever is looking.
 */
class OperationRunner(private val scope: CoroutineScope) {

    enum class Kind { COPY, MOVE, DELETE }

    data class Running(val kind: Kind, val itemCount: Int, val progress: OperationProgress?)

    sealed interface Event {
        data class Finished(val kind: Kind, val itemCount: Int) : Event
        data class Failed(val kind: Kind, val error: Throwable) : Event
        data class Cancelled(val kind: Kind) : Event
    }

    private val _running = MutableStateFlow<Running?>(null)
    val running: StateFlow<Running?> = _running.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var job: Job? = null

    /** Start [operation]. Returns false when another operation is still running. */
    fun start(kind: Kind, itemCount: Int, operation: Flow<OperationProgress>): Boolean {
        if (job?.isActive == true) return false
        _running.value = Running(kind, itemCount, null)
        job = scope.launch {
            try {
                operation.collect { progress -> _running.value = Running(kind, itemCount, progress) }
                _events.tryEmit(Event.Finished(kind, itemCount))
            } catch (e: CancellationException) {
                _events.tryEmit(Event.Cancelled(kind))
                throw e
            } catch (e: Throwable) {
                _events.tryEmit(Event.Failed(kind, e))
            } finally {
                _running.value = null
            }
        }
        return true
    }

    fun cancel() {
        job?.cancel()
    }
}
