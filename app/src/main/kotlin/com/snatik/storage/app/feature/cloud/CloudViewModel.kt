package com.snatik.storage.app.feature.cloud

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class CloudPreview(val name: String, val bytes: ByteArray)

data class CloudState(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val loading: Boolean = false,
    val path: String = "",           // "" is the Dropbox root
    val entries: List<CloudEntry> = emptyList(),
    val error: String? = null,
    val preview: CloudPreview? = null,
    val previewLoading: Boolean = false,
)

class CloudViewModel(
    application: Application,
    private val provider: DropboxProvider,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        CloudState(configured = provider.isConfigured(), connected = provider.isConnected()),
    )
    val state: StateFlow<CloudState> = _state.asStateFlow()

    init {
        if (provider.isConnected()) refresh("")
        // Complete the OAuth handshake when the redirect delivers a code.
        viewModelScope.launch {
            CloudAuthBus.pendingCode.collect { code ->
                if (code != null) {
                    _state.value = _state.value.copy(loading = true)
                    val ok = provider.exchangeCode(code)
                    CloudAuthBus.consume()
                    if (ok) refresh("") else _state.value = _state.value.copy(loading = false, error = "Sign-in failed")
                }
            }
        }
    }

    fun authUrl(): String? = provider.authUrl()

    fun refresh(path: String) {
        _state.value = _state.value.copy(loading = true, connected = true, path = path, error = null)
        viewModelScope.launch {
            val entries = provider.list(path)
            _state.value = _state.value.copy(loading = false, entries = entries)
        }
    }

    fun open(entry: CloudEntry) {
        if (entry.isFolder) refresh(entry.pathLower)
    }

    fun goUp() {
        val current = _state.value.path
        if (current.isEmpty()) return
        refresh(current.substringBeforeLast('/', ""))
    }

    fun preview(entry: CloudEntry) {
        if (!entry.isImage) return
        _state.value = _state.value.copy(previewLoading = true)
        viewModelScope.launch {
            val link = provider.temporaryLink(entry.pathLower)
            val bytes = link?.let { download(it) }
            _state.value = _state.value.copy(
                previewLoading = false,
                preview = bytes?.let { CloudPreview(entry.name, it) },
                error = if (bytes == null) "Could not open ${entry.name}" else null,
            )
        }
    }

    fun closePreview() { _state.value = _state.value.copy(preview = null) }

    fun disconnect() {
        provider.disconnect()
        _state.value = CloudState(configured = provider.isConfigured(), connected = false)
    }

    private suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            bytes
        }.getOrNull()
    }
}
