package com.snatik.storage.app.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.fs.FileKind
import com.snatik.storage.core.fs.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TextViewerState(
    val name: String,
    val path: String,
    val kind: FileKind,
    val text: String = "",
    val lines: List<String> = emptyList(),
    val totalSize: Long = 0,
    val loadedBytes: Long = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val wrap: Boolean = true,
    val pretty: Boolean = false,
    val canPretty: Boolean = false,
    val looksBinary: Boolean = false,
) {
    val truncated: Boolean get() = loadedBytes < totalSize
}

class TextViewerViewModel(private val path: String, private val fs: FileSystem) : ViewModel() {

    private val _state = MutableStateFlow(TextViewerState(name = File(path).name, path = path, kind = FileKind.of(File(path).name, false)))
    val state: StateFlow<TextViewerState> = _state.asStateFlow()

    private var raw: String = ""

    init {
        load(MAX_BYTES)
    }

    fun loadMore() = load(_state.value.loadedBytes + MAX_BYTES)

    private fun load(limit: Long) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val total = fs.stat(path)?.size ?: 0
                val bytes = fs.readBytes(path, 0, limit.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                val looksBinary = withContext(Dispatchers.Default) { isProbablyBinary(bytes) }
                raw = String(bytes, Charsets.UTF_8)
                val canPretty = _state.value.kind == FileKind.JSON || raw.trimStart().let { it.startsWith("{") || it.startsWith("[") }
                _state.update {
                    it.copy(totalSize = total, loadedBytes = bytes.size.toLong(), loading = false, canPretty = canPretty, looksBinary = looksBinary)
                }
                publish()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun toggleWrap() = _state.update { it.copy(wrap = !it.wrap) }

    fun togglePretty() {
        _state.update { it.copy(pretty = !it.pretty) }
        publish()
    }

    private fun publish() {
        viewModelScope.launch {
            val current = _state.value
            val text = withContext(Dispatchers.Default) {
                if (current.pretty) prettyJson(raw) ?: raw else raw
            }
            _state.update { it.copy(text = text, lines = text.lines()) }
        }
    }

    private companion object {
        const val MAX_BYTES = 512L * 1024

        fun prettyJson(text: String): String? = runCatching {
            val trimmed = text.trim()
            if (trimmed.startsWith("{")) JSONObject(trimmed).toString(2) else JSONArray(trimmed).toString(2)
        }.getOrNull()

        fun isProbablyBinary(bytes: ByteArray): Boolean {
            val sample = bytes.take(4096)
            if (sample.isEmpty()) return false
            val control = sample.count { b -> b.toInt() == 0 || (b in 1..8) || (b in 14..31) }
            return control > sample.size / 20
        }
    }
}
