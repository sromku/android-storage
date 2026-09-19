package com.snatik.storage.app.feature.media

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap

/** White-balance mode. CUSTOM = temp/tint; GRAYPOINT = multipliers from a tapped neutral. */
enum class RawWb { CAMERA, AUTO, CUSTOM, GRAYPOINT }

/** Demosaic algorithm (LibRaw user_qual). */
enum class Demosaic(val q: Int, val label: String) {
    LINEAR(0, "Linear"), VNG(1, "VNG"), PPG(2, "PPG"), AHD(3, "AHD"), DCB(4, "DCB"), DHT(11, "DHT")
}

/** Output color space (LibRaw output_color). */
enum class ColorSpace(val code: Int, val label: String) {
    SRGB(1, "sRGB"), ADOBE(2, "Adobe RGB"), WIDE(3, "Wide"), PROPHOTO(4, "ProPhoto"), P3(6, "Display P3")
}

/** Full develop control set. Values are user-facing; toArray() maps to the native P_* layout. */
data class DevelopParams(
    // Light
    val exposure: Float = 0f,       // stops
    val bright: Float = 1f,
    val highlight: Int = 0,         // 0 clip, 1 unclip, 2 blend, 3 rebuild
    val highlightLevel: Int = 5,    // rebuild strength 3..9
    // Tone (post)
    val contrast: Float = 0f,       // -100..100
    val shadows: Float = 0f,
    val blacks: Float = 0f,
    val highlightsTone: Float = 0f,
    val whites: Float = 0f,
    // White balance
    val wb: RawWb = RawWb.CAMERA,
    val temp: Float = 5500f,        // Kelvin
    val tint: Float = 0f,           // -100..100 (green..magenta)
    val grayMul: Triple<Float, Float, Float>? = null,
    // Color & detail
    val saturation: Float = 0f,     // -100..100
    val vibrance: Float = 0f,
    val demosaic: Demosaic = Demosaic.AHD,
    val fbdd: Int = 0,              // 0 off, 1 light, 2 full
    val threshold: Float = 0f,      // wavelet NR
    val colorSpace: ColorSpace = ColorSpace.SRGB,
) {
    fun toArray(): FloatArray = floatArrayOf(
        exposure,                    // 0 P_EXPOSURE
        highlight.toFloat(),         // 1 P_HL_MODE
        highlightLevel.toFloat(),    // 2 P_HL_LEVEL
        wb.ordinal.toFloat(),        // 3 P_WB_MODE
        temp,                        // 4 P_WB_TEMP
        tint,                        // 5 P_WB_TINT
        bright,                      // 6 P_BRIGHT
        demosaic.q.toFloat(),        // 7 P_DEMOSAIC
        fbdd.toFloat(),              // 8 P_FBDD
        threshold,                   // 9 P_THRESHOLD
        colorSpace.code.toFloat(),   // 10 P_COLORSPACE
        contrast,                    // 11 P_CONTRAST
        saturation,                  // 12 P_SATURATION
        vibrance,                    // 13 P_VIBRANCE
        shadows,                     // 14 P_SHADOWS
        blacks,                      // 15 P_BLACKS
        highlightsTone,              // 16 P_HIGHLIGHTS
        whites,                      // 17 P_WHITES
        grayMul?.first ?: 1f,        // 18 P_WB_R
        grayMul?.second ?: 1f,       // 19 P_WB_G
        grayMul?.third ?: 1f,        // 20 P_WB_B
    )
}

/**
 * JNI bridge to LibRaw. Open a RAW once (unpacks the sensor data), then re-develop
 * repeatedly with live params. Not thread-safe: confine one instance to one worker.
 */
class RawDeveloper private constructor(private var handle: Long) {

    val isOpen get() = handle != 0L

    /** As-shot camera WB multipliers [r,g,b], for seeding a neutral temperature. */
    fun camMul(): Triple<Float, Float, Float>? {
        if (handle == 0L) return null
        val m = runCatching { nativeCamMul(handle) }.getOrNull() ?: return null
        return if (m.size >= 3 && m[0] > 0) Triple(m[0], m[1], m[2]) else null
    }

    fun render(params: DevelopParams, half: Boolean): Bitmap? {
        if (handle == 0L) return null
        val packed = nativeRender(handle, if (half) 1 else 0, params.toArray())
        if (packed == 0L) return null
        val w = (packed ushr 32).toInt()
        val h = (packed and 0xffffffffL).toInt()
        if (w <= 0 || h <= 0) return null
        val bmp = createBitmap(w, h)
        return if (nativeFill(handle, bmp)) bmp else null.also { bmp.recycle() }
    }

    fun exportTiff(path: String, params: DevelopParams): Boolean {
        if (handle == 0L) return false
        return nativeExportTiff(handle, path, params.toArray())
    }

    /** Writes a raw (mosaiced) DNG preserving the sensor data + camera color metadata. */
    fun exportDng(srcPath: String, outPath: String): Boolean {
        if (handle == 0L) return false
        return nativeExportDng(srcPath, outPath)
    }

    fun close() { if (handle != 0L) { nativeClose(handle); handle = 0L } }

    private external fun nativeRender(handle: Long, half: Int, params: FloatArray): Long
    private external fun nativeFill(handle: Long, bitmap: Bitmap): Boolean
    private external fun nativeExportTiff(handle: Long, path: String, params: FloatArray): Boolean
    private external fun nativeExportDng(srcPath: String, outPath: String): Boolean
    private external fun nativeCamMul(handle: Long): FloatArray
    private external fun nativeClose(handle: Long)
    private external fun nativeOpen(path: String): Long

    companion object {
        @Volatile private var loaded = false
        private fun ensureLoaded(): Boolean = synchronized(this) {
            if (!loaded) loaded = runCatching { System.loadLibrary("rawdev") }.isSuccess
            loaded
        }

        fun open(path: String): RawDeveloper? {
            if (!ensureLoaded()) return null
            val stub = RawDeveloper(0L)
            val h = runCatching { stub.nativeOpen(path) }.getOrDefault(0L)
            return if (h != 0L) RawDeveloper(h) else null
        }
    }
}
