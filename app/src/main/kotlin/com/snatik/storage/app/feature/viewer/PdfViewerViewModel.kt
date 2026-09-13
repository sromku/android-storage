package com.snatik.storage.app.feature.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.fs.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class PdfViewerState(
    val name: String,
    val path: String,
    val loading: Boolean = true,
    val error: String? = null,
    val pageCount: Int = 0,
    /** Height / width of the first page, so placeholders can be sized before a page renders. */
    val aspect: Float = 1.414f,
    val sizeBytes: Long = 0,
)

class PdfViewerViewModel(private val path: String, private val fs: FileSystem, private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(PdfViewerState(name = File(path).name, path = path))
    val state: StateFlow<PdfViewerState> = _state.asStateFlow()

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    // PdfRenderer allows only one open page at a time, so every render is serialized.
    private val lock = Mutex()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    val size = fs.stat(path)?.size ?: 0
                    // Open the file directly when readable; otherwise copy it (via the shell-backed
                    // fs) into the cache so PdfRenderer has a seekable descriptor.
                    val direct = File(path)
                    val file = if (direct.canRead()) direct else cacheCopy()
                    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = PdfRenderer(descriptor)
                    val aspect = if (r.pageCount > 0) r.openPage(0).use { it.height.toFloat() / it.width } else 1.414f
                    pfd = descriptor
                    renderer = r
                    _state.update { it.copy(loading = false, pageCount = r.pageCount, aspect = aspect, sizeBytes = if (size > 0) size else file.length()) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    private suspend fun cacheCopy(): File {
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val out = File(dir, "view_" + File(path).name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        out.writeBytes(fs.readBytes(path, 0, MAX_BYTES))
        return out
    }

    /** Render one page to a bitmap [widthPx] wide (height follows the page's aspect). */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            val r = renderer ?: return@withContext null
            if (index !in 0 until r.pageCount || widthPx <= 0) return@withContext null
            runCatching {
                r.openPage(index).use { page ->
                    val height = (widthPx.toFloat() * page.height / page.width).roundToInt().coerceIn(1, 8000)
                    val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE) // PDFs assume white paper; renderer leaves gaps transparent
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }.getOrNull()
        }
    }

    override fun onCleared() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
    }

    private companion object {
        const val MAX_BYTES = 128 * 1024 * 1024
    }
}
