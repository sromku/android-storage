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

/** The 8 HSL colour bands, in the native/Lightroom order. */
enum class HslBand(val label: String) {
    RED("Red"), ORANGE("Orange"), YELLOW("Yellow"), GREEN("Green"),
    AQUA("Aqua"), BLUE("Blue"), PURPLE("Purple"), MAGENTA("Magenta")
}

/** Tone curves: a composite RGB curve plus optional per-channel curves. Points are (x,y) in 0..1. */
data class ToneCurve(
    val rgb: List<Pair<Float, Float>> = IDENTITY,
    val r: List<Pair<Float, Float>> = IDENTITY,
    val g: List<Pair<Float, Float>> = IDENTITY,
    val b: List<Pair<Float, Float>> = IDENTITY,
) {
    val isIdentity get() = rgb == IDENTITY && r == IDENTITY && g == IDENTITY && b == IDENTITY

    /** 3x256 LUT (R,G,B) = rgbCurve(channelCurve(x)), for the native post-process. */
    fun toLut(): FloatArray {
        val out = FloatArray(768)
        val chans = listOf(r, g, b)
        for (k in 0..2) {
            val ch = chans[k]
            for (i in 0..255) {
                val x = i / 255f
                out[k * 256 + i] = eval(rgb, eval(ch, x)).coerceIn(0f, 1f)
            }
        }
        return out
    }

    companion object {
        val IDENTITY = listOf(0f to 0f, 1f to 1f)

        /** Linear interpolation across sorted control points, clamped at the ends. */
        fun eval(points: List<Pair<Float, Float>>, x: Float): Float {
            if (points.size < 2) return x
            if (x <= points.first().first) return points.first().second
            if (x >= points.last().first) return points.last().second
            for (i in 0 until points.size - 1) {
                val (x0, y0) = points[i]; val (x1, y1) = points[i + 1]
                if (x in x0..x1) {
                    val t = if (x1 > x0) (x - x0) / (x1 - x0) else 0f
                    return y0 + (y1 - y0) * t
                }
            }
            return x
        }
    }
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
    // Presence + sharpening (spatial)
    val texture: Float = 0f,        // -100..100
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val sharpen: Float = 0f,        // 0..100
    val sharpenRadius: Float = 1f,  // 1..3 px
    val sharpenMask: Float = 0f,    // 0..100 edge masking
    // Effects + lens
    val vignette: Float = 0f,       // -100..100
    val grain: Float = 0f,          // 0..100
    val caRed: Float = 0f,          // -100..100 red/cyan fringe
    val caBlue: Float = 0f,         // -100..100 blue/yellow fringe
    // Color mixer: 8 bands x (hue, sat, lum) in -100..100
    val hsl: List<Float> = List(24) { 0f },
    val curve: ToneCurve = ToneCurve(),
) {
    fun hslArray(): FloatArray? = if (hsl.all { it == 0f }) null else hsl.toFloatArray()
    fun curveArray(): FloatArray? = if (curve.isIdentity) null else curve.toLut()

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
        texture,                     // 21 P_TEXTURE
        clarity,                     // 22 P_CLARITY
        dehaze,                      // 23 P_DEHAZE
        sharpen,                     // 24 P_SHARPEN
        sharpenRadius,               // 25 P_SHARPEN_RADIUS
        sharpenMask,                 // 26 P_SHARPEN_MASK
        vignette,                    // 27 P_VIGNETTE
        grain,                       // 28 P_GRAIN
        caRed,                       // 29 P_CA_RED
        caBlue,                      // 30 P_CA_BLUE
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
        val packed = nativeRender(handle, if (half) 1 else 0, params.toArray(), params.hslArray(), params.curveArray())
        if (packed == 0L) return null
        val w = (packed ushr 32).toInt()
        val h = (packed and 0xffffffffL).toInt()
        if (w <= 0 || h <= 0) return null
        val bmp = createBitmap(w, h)
        return if (nativeFill(handle, bmp)) bmp else null.also { bmp.recycle() }
    }

    fun exportTiff(path: String, params: DevelopParams): Boolean {
        if (handle == 0L) return false
        return nativeExportTiff(handle, path, params.toArray(), params.hslArray(), params.curveArray())
    }

    /** Writes a raw (mosaiced) DNG preserving the sensor data + camera color metadata. */
    fun exportDng(srcPath: String, outPath: String): Boolean {
        if (handle == 0L) return false
        return nativeExportDng(srcPath, outPath)
    }

    fun close() { if (handle != 0L) { nativeClose(handle); handle = 0L } }

    private external fun nativeRender(handle: Long, half: Int, params: FloatArray, hsl: FloatArray?, curve: FloatArray?): Long
    private external fun nativeFill(handle: Long, bitmap: Bitmap): Boolean
    private external fun nativeExportTiff(handle: Long, path: String, params: FloatArray, hsl: FloatArray?, curve: FloatArray?): Boolean
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
