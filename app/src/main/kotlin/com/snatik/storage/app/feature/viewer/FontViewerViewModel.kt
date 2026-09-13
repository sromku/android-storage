package com.snatik.storage.app.feature.viewer

import android.content.Context
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.fs.FontInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FontViewerState(
    val name: String,
    val path: String,
    val loading: Boolean = true,
    val error: String? = null,
    val info: FontInfo? = null,
    val typeface: Typeface? = null,
    val sizeBytes: Long = 0,
)

class FontViewerViewModel(private val path: String, private val fs: FileSystem, private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(FontViewerState(name = File(path).name, path = path))
    val state: StateFlow<FontViewerState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val size = fs.stat(path)?.size ?: 0
                val bytes = fs.readBytes(path, 0, MAX_BYTES)
                val info = FontInfo.parse(bytes)
                // Android renders sfnt fonts from a file only; WOFF/WOFF2 aren't supported natively.
                val typeface = if (info.previewable) loadTypeface(bytes) else null
                _state.update { it.copy(loading = false, info = info, typeface = typeface, sizeBytes = size) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    private suspend fun loadTypeface(bytes: ByteArray): Typeface? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "fonts").apply { mkdirs() }
            val file = File(dir, "preview_" + File(path).name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            file.writeBytes(bytes)
            Typeface.createFromFile(file)
        }.getOrNull()
    }

    private companion object {
        // Fonts are small; cap defensively so a mislabelled huge file can't exhaust memory.
        const val MAX_BYTES = 32 * 1024 * 1024
    }
}
