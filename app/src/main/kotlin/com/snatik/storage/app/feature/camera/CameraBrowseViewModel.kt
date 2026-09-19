package com.snatik.storage.app.feature.camera

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.feature.camera.ptp.PtpIpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class CamStatus { IDLE, CONNECTING, LISTING, CONNECTED, DOWNLOADING, ERROR }

data class CameraItem(
    val handle: Long,
    val name: String,
    val size: Long,
    val isRaw: Boolean,
    val width: Long,
    val height: Long,
    val thumb: ByteArray? = null,
)

data class CameraBrowseState(
    val status: CamStatus = CamStatus.IDLE,
    val host: String = "",
    val items: List<CameraItem> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val listed: Int = 0,
    val total: Int = 0,
    val downloadDone: Int = 0,
    val downloadTotal: Int = 0,
    val error: String? = null,
)

class CameraBrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("camera_ptp", Context.MODE_PRIVATE)
    private val ptpMutex = Mutex()
    private var client: PtpIpClient? = null
    private var connectJob: kotlinx.coroutines.Job? = null

    private val _state = MutableStateFlow(CameraBrowseState(host = defaultGateway()))
    val state: StateFlow<CameraBrowseState> = _state.asStateFlow()

    fun setHost(host: String) = _state.update { it.copy(host = host) }

    fun connect() {
        val host = _state.value.host.trim()
        if (host.isEmpty()) { _state.update { it.copy(status = CamStatus.ERROR, error = "Enter the camera's IP") }; return }
        connectJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(status = CamStatus.CONNECTING, error = null, items = emptyList(), selected = emptySet(), listed = 0, total = 0) }
            val ok = runCatching {
                ptpMutex.withLock {
                    val c = PtpIpClient(host, guid()).also { client = it }
                    c.connect()
                    c.openSession()
                    _state.update { it.copy(status = CamStatus.LISTING) }
                    val handles = c.storageIds().flatMap { c.objectHandles(it).toList() }
                    _state.update { it.copy(total = handles.size) }
                    for (h in handles) {
                        if (!isActive) break
                        val info = runCatching { c.objectInfo(h) }.getOrNull()
                        if (info != null && !info.isFolder && isMedia(info.filename)) {
                            val item = CameraItem(h, info.filename, info.compressedSize, isRawExt(info.filename), info.imageWidth, info.imageHeight)
                            _state.update { it.copy(items = it.items + item, listed = it.listed + 1) }
                        } else {
                            _state.update { it.copy(listed = it.listed + 1) }
                        }
                    }
                }
            }
            if (!isActive) return@launch // cancelled - leave the state as cancelConnect() set it
            if (ok.isSuccess) { _state.update { it.copy(status = CamStatus.CONNECTED) }; loadThumbnails() }
            else _state.update { it.copy(status = CamStatus.ERROR, error = ok.exceptionOrNull()?.message ?: "Connection failed") }
        }
    }

    /** Abort an in-progress connect immediately (closing the socket interrupts the blocking connect). */
    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        runCatching { client?.close() }
        client = null
        _state.update { it.copy(status = CamStatus.IDLE, error = null, items = emptyList(), selected = emptySet(), listed = 0, total = 0) }
    }

    fun toggle(handle: Long) = _state.update {
        it.copy(selected = if (handle in it.selected) it.selected - handle else it.selected + handle)
    }

    fun selectAll() = _state.update { it.copy(selected = it.items.map { i -> i.handle }.toSet()) }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun downloadSelected() {
        val targets = _state.value.items.filter { it.handle in _state.value.selected }
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(status = CamStatus.DOWNLOADING, downloadDone = 0, downloadTotal = targets.size) }
            for (t in targets) {
                runCatching {
                    ptpMutex.withLock {
                        val sink = CameraMediaStore.begin(getApplication(), t.name) ?: return@withLock
                        val c = client ?: return@withLock
                        runCatching { c.getObject(t.handle, sink.output) }
                            .onSuccess { sink.commit(t.size) }
                            .onFailure { sink.abort() }
                    }
                }
                _state.update { it.copy(downloadDone = it.downloadDone + 1) }
            }
            _state.update { it.copy(status = CamStatus.CONNECTED, selected = emptySet()) }
        }
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ptpMutex.withLock { client?.closeSession(); client?.close() } }
            client = null
            _state.update { CameraBrowseState(host = it.host) }
        }
    }

    override fun onCleared() {
        runCatching { client?.close() }
    }

    /** Fill in thumbnails one at a time on the shared socket, after listing completes. */
    private fun loadThumbnails() {
        viewModelScope.launch(Dispatchers.IO) {
            val handles = _state.value.items.map { it.handle }
            for (h in handles) {
                if (_state.value.status == CamStatus.IDLE) return@launch
                val bytes = runCatching { ptpMutex.withLock { client?.thumbnail(h) } }.getOrNull() ?: continue
                _state.update { s -> s.copy(items = s.items.map { if (it.handle == h) it.copy(thumb = bytes) else it }) }
            }
        }
    }

    private fun guid(): ByteArray {
        prefs.getString("guid", null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        return PtpIpClient.randomGuid().also { prefs.edit { putString("guid", Base64.encodeToString(it, Base64.NO_WRAP)) } }
    }

    private fun defaultGateway(): String = runCatching {
        val wm = getApplication<Application>().applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val gw = wm?.dhcpInfo?.gateway ?: 0
        if (gw != 0) "${gw and 0xff}.${gw shr 8 and 0xff}.${gw shr 16 and 0xff}.${gw shr 24 and 0xff}" else ""
    }.getOrDefault("")

    private companion object {
        val MEDIA_EXT = setOf("jpg", "jpeg", "heic", "heif", "png", "tif", "tiff", "dng", "arw", "raw", "cr2", "cr3", "nef", "rw2", "orf", "raf", "mp4", "mov", "mts", "m2ts")
        val RAW_EXT = setOf("arw", "raw", "dng", "cr2", "cr3", "nef", "rw2", "orf", "raf")
        fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
        fun isMedia(name: String) = ext(name) in MEDIA_EXT
        fun isRawExt(name: String) = ext(name) in RAW_EXT
    }
}
