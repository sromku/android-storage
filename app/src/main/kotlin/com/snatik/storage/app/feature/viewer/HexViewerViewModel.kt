package com.snatik.storage.app.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.fs.FileSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class HexViewerState(
    val name: String,
    val path: String,
    val bytes: ByteArray = ByteArray(0),
    val totalSize: Long = 0,
    val loading: Boolean = true,
    val error: String? = null,
) {
    val truncated: Boolean get() = bytes.size < totalSize
    fun rowCount(bytesPerRow: Int): Int = (bytes.size + bytesPerRow - 1) / bytesPerRow
}

class HexViewerViewModel(private val path: String, private val fs: FileSystem) : ViewModel() {

    private val _state = MutableStateFlow(HexViewerState(name = File(path).name, path = path))
    val state: StateFlow<HexViewerState> = _state.asStateFlow()

    init {
        load(MAX_BYTES)
    }

    fun loadMore() = load(_state.value.bytes.size + MAX_BYTES)

    private fun load(limit: Int) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val total = fs.stat(path)?.size ?: 0
                val bytes = fs.readBytes(path, 0, limit)
                _state.update { it.copy(bytes = bytes, totalSize = total, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    private companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}
