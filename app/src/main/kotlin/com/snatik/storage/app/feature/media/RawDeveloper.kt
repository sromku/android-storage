package com.snatik.storage.app.feature.media

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap

/** White-balance presets exposed in the develop UI. */
enum class RawWb(val mode: Int) { CAMERA(0), AUTO(1), CUSTOM(2) }

/** One set of develop controls. exposure in stops, wbTemp in [-1,1], bright multiplier. */
data class DevelopParams(
    val exposure: Float = 0f,
    val highlight: Int = 0,      // 0 clip, 1 unclip, 2 blend, 3 rebuild
    val wb: RawWb = RawWb.CAMERA,
    val wbTemp: Float = 0f,
    val bright: Float = 1f,
)

/**
 * JNI bridge to LibRaw. Open a RAW once (unpacks the sensor data), then re-develop
 * repeatedly with live params. Not thread-safe: confine one instance to one worker.
 */
class RawDeveloper private constructor(private var handle: Long) {

    val isOpen get() = handle != 0L

    /** Develop at [half] resolution and return an ARGB bitmap, or null on failure. */
    fun render(params: DevelopParams, half: Boolean, quality: Int): Bitmap? {
        if (handle == 0L) return null
        val q = if (half) 0 else quality // linear demosaic for fast half previews
        val packed = nativeRender(handle, if (half) 1 else 0, params.exposure, params.highlight, params.wb.mode, params.wbTemp, params.bright, q)
        if (packed == 0L) return null
        val w = (packed ushr 32).toInt()
        val h = (packed and 0xffffffffL).toInt()
        if (w <= 0 || h <= 0) return null
        val bmp = createBitmap(w, h)
        return if (nativeFill(handle, bmp)) bmp else null.also { bmp.recycle() }
    }

    /** Full-quality develop straight to a 16-bit TIFF at [path]. */
    fun exportTiff(path: String, params: DevelopParams): Boolean {
        if (handle == 0L) return false
        return nativeExportTiff(handle, path, params.exposure, params.highlight, params.wb.mode, params.wbTemp, params.bright)
    }

    fun close() {
        if (handle != 0L) { nativeClose(handle); handle = 0L }
    }

    private external fun nativeRender(handle: Long, half: Int, exposure: Float, highlight: Int, wbMode: Int, wbTemp: Float, bright: Float, quality: Int): Long
    private external fun nativeFill(handle: Long, bitmap: Bitmap): Boolean
    private external fun nativeExportTiff(handle: Long, path: String, exposure: Float, highlight: Int, wbMode: Int, wbTemp: Float, bright: Float): Boolean
    private external fun nativeClose(handle: Long)
    private external fun nativeOpen(path: String): Long

    companion object {
        @Volatile private var loaded = false
        private fun ensureLoaded(): Boolean = synchronized(this) {
            if (!loaded) loaded = runCatching { System.loadLibrary("rawdev") }.isSuccess
            loaded
        }

        /** Opens and unpacks the RAW at [path]; null if the library or file cannot load. */
        fun open(path: String): RawDeveloper? {
            if (!ensureLoaded()) return null
            val stub = RawDeveloper(0L)
            val h = runCatching { stub.nativeOpen(path) }.getOrDefault(0L)
            return if (h != 0L) RawDeveloper(h) else null
        }
    }
}
