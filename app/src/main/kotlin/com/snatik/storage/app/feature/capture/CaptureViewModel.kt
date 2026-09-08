package com.snatik.storage.app.feature.capture

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.core.capture.RecordSource
import com.snatik.storage.core.capture.RecordingConfig
import com.snatik.storage.core.capture.RecordingEngine
import com.snatik.storage.core.capture.RecordingEntity
import com.snatik.storage.core.capture.SnapshotEntity
import com.snatik.storage.core.capture.SnapshotEvent
import com.snatik.storage.core.capture.SnapshotOptions
import com.snatik.storage.core.capture.SnapshotRepository
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SnapshotForm(val path: String = "", val label: String = "", val hash: Boolean = false, val keepCopies: Boolean = false)

data class RecordingForm(
    val name: String = "",
    val sources: Set<RecordSource> = setOf(RecordSource.LOGCAT, RecordSource.BROADCASTS),
    val logcatPackage: String = "",
    val logcatFilter: String = "",
    val watchPaths: String = "",
) {
    fun toConfig() = RecordingConfig(
        name = name,
        sources = sources,
        logcatFilter = logcatFilter,
        logcatPackage = logcatPackage.trim().ifEmpty { null },
        watchPaths = watchPaths.lines().map { it.trim() }.filter { it.isNotEmpty() },
    )
}

data class CaptureUiState(
    val active: RecordingEntity? = null,
    val liveCount: Int = 0,
    val snapshots: List<SnapshotEntity> = emptyList(),
    val recordings: List<RecordingEntity> = emptyList(),
    val shellAvailable: Boolean = false,
    val snapshotForm: SnapshotForm? = null,
    val snapshotProgress: SnapshotEvent.Progress? = null,
    val snapshotRunning: Boolean = false,
    val recordingForm: RecordingForm? = null,
)

class CaptureViewModel(
    route: Route.Capture,
    private val context: Context,
    private val snapshots: SnapshotRepository,
    private val engine: RecordingEngine,
    private val privilege: PrivilegeManager,
) : ViewModel() {

    private data class Local(
        val snapshotForm: SnapshotForm? = null,
        val snapshotProgress: SnapshotEvent.Progress? = null,
        val snapshotRunning: Boolean = false,
        val recordingForm: RecordingForm? = null,
    )

    private val local = MutableStateFlow(Local(snapshotForm = route.newSnapshotPath?.let { SnapshotForm(path = it, label = it.substringAfterLast('/')) }))

    val state: StateFlow<CaptureUiState> = combine(local, engine.active, engine.liveCount, snapshots.snapshots, engine.recordings, privilege.executor) { values ->
        val l = values[0] as Local
        @Suppress("UNCHECKED_CAST")
        CaptureUiState(
            active = values[1] as RecordingEntity?,
            liveCount = values[2] as Int,
            snapshots = values[3] as List<SnapshotEntity>,
            recordings = values[4] as List<RecordingEntity>,
            shellAvailable = values[5] != null,
            snapshotForm = l.snapshotForm,
            snapshotProgress = l.snapshotProgress,
            snapshotRunning = l.snapshotRunning,
            recordingForm = l.recordingForm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    // Snapshots

    fun openSnapshotForm(path: String = "") = local.update { it.copy(snapshotForm = SnapshotForm(path = path, label = path.substringAfterLast('/'))) }
    fun updateSnapshotForm(form: SnapshotForm) = local.update { it.copy(snapshotForm = form) }
    fun closeSnapshotForm() = local.update { it.copy(snapshotForm = null) }

    fun createSnapshot() {
        val form = local.value.snapshotForm ?: return
        if (form.path.isBlank()) return
        local.update { it.copy(snapshotForm = null, snapshotRunning = true, snapshotProgress = null) }
        viewModelScope.launch {
            try {
                snapshots.create(form.path.trim(), form.label.ifBlank { form.path.trim().substringAfterLast('/') }, SnapshotOptions(form.hash, form.keepCopies)).collect { event ->
                    if (event is SnapshotEvent.Progress) local.update { it.copy(snapshotProgress = event) }
                }
            } catch (e: Exception) {
                _messages.send(e.message ?: e.toString())
            } finally {
                local.update { it.copy(snapshotRunning = false, snapshotProgress = null) }
            }
        }
    }

    fun deleteSnapshot(id: Long) = viewModelScope.launch { snapshots.delete(id) }

    // Recordings

    fun openRecordingForm() = local.update { it.copy(recordingForm = RecordingForm()) }
    fun updateRecordingForm(form: RecordingForm) = local.update { it.copy(recordingForm = form) }
    fun closeRecordingForm() = local.update { it.copy(recordingForm = null) }

    /** Start without the screen. The screen path goes through the activity result first. */
    fun startRecording(projectionResult: Int = 0, projectionData: Intent? = null) {
        val form = local.value.recordingForm ?: return
        local.update { it.copy(recordingForm = null) }
        RecordingService.start(context, form.toConfig(), projectionResult, projectionData)
    }

    fun stopRecording() = RecordingService.stop(context)

    fun deleteRecording(id: Long) = viewModelScope.launch { engine.delete(id) }
}
