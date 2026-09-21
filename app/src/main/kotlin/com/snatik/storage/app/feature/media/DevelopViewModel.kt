package com.snatik.storage.app.feature.media

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class DevelopState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val params: DevelopParams = DevelopParams(),
    val preview: Bitmap? = null,
    val rendering: Boolean = false,
    val highQuality: Boolean = false, // the shown bitmap is the full-quality zoom render
    val renderingHq: Boolean = false,
    val exporting: Boolean = false,
    val exportMsg: String? = null,
)

private const val PREVIEW_CAP = 2048 // longest-edge cap for the fast live preview
private const val QUALITY_CAP = 4096 // longest-edge cap for the zoomed full-quality render

/** Drives a live LibRaw develop session for one RAW file. */
@OptIn(FlowPreview::class)
class DevelopViewModel(
    application: Application,
    private val path: String,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DevelopState())
    val state: StateFlow<DevelopState> = _state.asStateFlow()

    private val paramsFlow = MutableStateFlow(DevelopParams())
    private val zoomActive = MutableStateFlow(false)
    private val renderLock = Mutex()
    private var dev: RawDeveloper? = null

    init {
        viewModelScope.launch {
            val opened = withContext(Dispatchers.IO) { RawDeveloper.open(path) }
            if (opened == null) {
                _state.value = _state.value.copy(loading = false, failed = true)
                return@launch
            }
            dev = opened
            renderPreview(paramsFlow.value, first = true)
        }
        // Fast live preview on parameter changes (debounced, latest wins).
        viewModelScope.launch {
            paramsFlow.drop(1).debounce(180).collectLatest { renderPreview(it, first = false) }
        }
        // Full-quality render for the zoomed view: kicks in once the edit/zoom settles.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(paramsFlow, zoomActive) { p, z -> p to z }
                .debounce(420)
                .collectLatest { (p, z) -> if (z) renderQuality(p) }
        }
    }

    fun update(params: DevelopParams) {
        _state.value = _state.value.copy(params = params)
        paramsFlow.value = params
    }

    /** The preview is zoomed in; ask for the full-quality render (called from the editor). */
    fun setZoomActive(active: Boolean) { zoomActive.value = active }

    private suspend fun renderPreview(params: DevelopParams, first: Boolean) {
        val d = dev ?: return
        _state.value = _state.value.copy(rendering = true, loading = first && _state.value.preview == null)
        val bmp = withContext(Dispatchers.IO) {
            renderLock.withLock {
                val full = d.render(params.copy(demosaic = Demosaic.LINEAR), half = true) ?: return@withLock null
                val longest = maxOf(full.width, full.height)
                if (longest > PREVIEW_CAP) {
                    val s = PREVIEW_CAP.toFloat() / longest
                    val small = full.scale((full.width * s).toInt().coerceAtLeast(1), (full.height * s).toInt().coerceAtLeast(1))
                    if (small != full) full.recycle()
                    small
                } else full
            }
        }
        val old = _state.value.preview
        _state.value = _state.value.copy(preview = bmp ?: old, rendering = false, loading = false, highQuality = false)
        if (bmp != null && old != null && old != bmp) old.recycle()
    }

    /** Full-resolution develop with the chosen demosaic, downscaled to a texture-safe size, for the zoomed view. */
    private suspend fun renderQuality(params: DevelopParams) {
        val d = dev ?: return
        if (_state.value.highQuality) return // already sharp for these params
        _state.value = _state.value.copy(renderingHq = true)
        val bmp = withContext(Dispatchers.IO) {
            renderLock.withLock {
                val full = d.render(params, half = false) ?: return@withLock null
                val longest = maxOf(full.width, full.height)
                if (longest > QUALITY_CAP) {
                    val s = QUALITY_CAP.toFloat() / longest
                    val small = full.scale((full.width * s).toInt().coerceAtLeast(1), (full.height * s).toInt().coerceAtLeast(1))
                    if (small != full) full.recycle()
                    small
                } else full
            }
        }
        val old = _state.value.preview
        if (bmp != null) {
            _state.value = _state.value.copy(preview = bmp, highQuality = true, renderingHq = false)
            if (old != null && old != bmp) old.recycle()
        } else {
            _state.value = _state.value.copy(renderingHq = false)
        }
    }

    fun exportJpeg(quality: Int = 95) {
        val d = dev ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(exporting = true, exportMsg = null)
            val ok = withContext(Dispatchers.IO) {
                renderLock.withLock {
                    val full = d.render(_state.value.params, half = false) ?: return@withLock false
                    val saved = saveBitmapJpeg(full, quality)
                    full.recycle()
                    saved
                }
            }
            _state.value = _state.value.copy(exporting = false, exportMsg = if (ok) "Saved JPEG to Pictures/Storage Studio" else "Export failed")
        }
    }

    fun exportTiff() {
        val d = dev ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(exporting = true, exportMsg = null)
            val ok = withContext(Dispatchers.IO) {
                renderLock.withLock {
                    val base = File(path).name.substringBeforeLast('.')
                    val cache = File(getApplication<Application>().cacheDir, "$base.tiff")
                    if (!d.exportTiff(cache.absolutePath, _state.value.params)) return@withLock false
                    val moved = publishImage(cache, "image/tiff")
                    cache.delete()
                    moved
                }
            }
            _state.value = _state.value.copy(exporting = false, exportMsg = if (ok) "Saved 16-bit TIFF to Pictures/Storage Studio" else "Export failed")
        }
    }

    fun exportDng() {
        val d = dev ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(exporting = true, exportMsg = null)
            val ok = withContext(Dispatchers.IO) {
                renderLock.withLock {
                    val base = File(path).name.substringBeforeLast('.')
                    val cache = File(getApplication<Application>().cacheDir, "$base.dng")
                    if (!d.exportDng(path, cache.absolutePath)) return@withLock false
                    val moved = publishImage(cache, "image/x-adobe-dng")
                    cache.delete()
                    moved
                }
            }
            _state.value = _state.value.copy(exporting = false, exportMsg = if (ok) "Saved DNG to Pictures/Storage Studio" else "DNG export failed (unsupported sensor?)")
        }
    }

    fun clearMessage() { _state.value = _state.value.copy(exportMsg = null) }

    private fun saveBitmapJpeg(bmp: Bitmap, quality: Int): Boolean = runCatching {
        val base = File(path).name.substringBeforeLast('.')
        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${base}_dev.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Storage Studio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) } ?: return false
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); resolver.update(uri, values, null, null)
        }
        true
    }.getOrDefault(false)

    private fun publishImage(src: File, mime: String): Boolean = runCatching {
        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, src.name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Storage Studio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Storage Studio").apply { mkdirs() }
                put(MediaStore.Images.Media.DATA, File(dir, src.name).absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: return false
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); resolver.update(uri, values, null, null)
        }
        true
    }.getOrDefault(false)

    override fun onCleared() {
        val d = dev; dev = null
        viewModelScope.launch(Dispatchers.IO) { d?.close() }
        _state.value.preview?.let { if (!it.isRecycled) it.recycle() }
    }
}
