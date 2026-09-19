package com.snatik.storage.app.feature.media

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pixel-level analysis overlays for pixel-peeping a shot: blown/crushed clipping and focus peaking. */
enum class AnalysisMode { NONE, CLIPPING, PEAKING }

private const val ANALYSIS_EDGE = 1400 // longest-edge cap for the analysis bitmap

/**
 * Builds a transparent overlay bitmap matching the source aspect. Drawn with ContentScale.Fit over
 * the (un-zoomed) image it lines up pixel-for-pixel.
 *  - CLIPPING: highlight-clipped pixels turn red, shadow-clipped pixels turn blue.
 *  - PEAKING: high-contrast (in-focus) edges glow lime.
 */
suspend fun analysisOverlay(context: Context, model: Any, mode: AnalysisMode): Bitmap? =
    withContext(Dispatchers.IO) {
        if (mode == AnalysisMode.NONE) return@withContext null
        val loader = coil3.SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(Size(ANALYSIS_EDGE, ANALYSIS_EDGE))
            .allowHardware(false)
            .build()
        val src = runCatching { loader.execute(request).image?.toBitmap() }.getOrNull() ?: return@withContext null
        val w = src.width; val h = src.height
        if (w <= 2 || h <= 2) return@withContext null
        val px = IntArray(w * h)
        runCatching { src.getPixels(px, 0, w, 0, 0, w, h) }.getOrElse { return@withContext null }

        val out = IntArray(w * h) // transparent by default
        when (mode) {
            AnalysisMode.CLIPPING -> {
                val red = 0xE6FF2D55.toInt(); val blue = 0xE60A84FF.toInt()
                for (i in px.indices) {
                    val p = px[i]
                    val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                    if (r >= 250 || g >= 250 || b >= 250) out[i] = red
                    else if (r <= 4 && g <= 4 && b <= 4) out[i] = blue
                }
            }
            AnalysisMode.PEAKING -> {
                val lum = IntArray(w * h)
                for (i in px.indices) {
                    val p = px[i]
                    lum[i] = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                }
                val lime = 0xFFAEEA00.toInt()
                for (y in 1 until h - 1) {
                    val row = y * w
                    for (x in 1 until w - 1) {
                        val i = row + x
                        val gx = lum[i + 1] - lum[i - 1]
                        val gy = lum[i + w] - lum[i - w]
                        if (kotlin.math.abs(gx) + kotlin.math.abs(gy) > 42) out[i] = lime
                    }
                }
            }
            AnalysisMode.NONE -> {}
        }
        createBitmap(w, h).apply { setPixels(out, 0, w, 0, 0, w, h) }
    }
