package com.snatik.storage.app.feature.camera

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ReceivedFile(
    val name: String,
    val size: Long,
    val at: Long = System.currentTimeMillis(),
    /** Content uri of the stored item, for a live thumbnail. */
    val uri: String? = null,
    /** Absolute path of the stored file, used to preview RAW frames via their embedded JPEG. */
    val path: String? = null,
)

data class CameraSyncState(
    val running: Boolean = false,
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val user: String = DEFAULT_USER,
    val pass: String = DEFAULT_PASS,
    val files: List<ReceivedFile> = emptyList(),
    val totalBytes: Long = 0,
    val error: String? = null,
) {
    companion object {
        const val DEFAULT_PORT = 2121
        const val DEFAULT_USER = "camera"
        const val DEFAULT_PASS = "storage"
    }
}

/** Shared, process-wide state between the FTP foreground service and the Camera screen. */
object CameraSync {
    private val _state = MutableStateFlow(CameraSyncState())
    val state: StateFlow<CameraSyncState> = _state.asStateFlow()

    fun setRunning(running: Boolean, host: String, port: Int, user: String, pass: String) {
        _state.update { it.copy(running = running, host = host, port = port, user = user, pass = pass, error = null) }
    }

    fun setStopped() { _state.update { it.copy(running = false) } }

    fun addFile(name: String, size: Long, uri: String? = null, path: String? = null) {
        _state.update {
            it.copy(
                files = (listOf(ReceivedFile(name, size, uri = uri, path = path)) + it.files).take(200),
                totalBytes = it.totalBytes + size,
            )
        }
    }

    fun setError(message: String?) { _state.update { it.copy(error = message) } }

    fun reset() { _state.update { CameraSyncState() } }
}
