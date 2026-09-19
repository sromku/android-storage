package com.snatik.storage.app.feature.media

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** RGB + luminance histograms, 256 bins each, already normalized to 0..1. */
class Histogram(val r: FloatArray, val g: FloatArray, val b: FloatArray, val lum: FloatArray)

suspend fun computeHistogram(context: Context, model: Any): Histogram? = withContext(Dispatchers.IO) {
    val loader = coil3.SingletonImageLoader.get(context)
    val request = ImageRequest.Builder(context)
        .data(model)
        .size(Size(360, 360))
        .allowHardware(false)
        .build()
    val result = runCatching { loader.execute(request) }.getOrNull() ?: return@withContext null
    val bmp = runCatching { result.image?.toBitmap() }.getOrNull() ?: return@withContext null

    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return@withContext null
    val pixels = IntArray(w * h)
    runCatching { bmp.getPixels(pixels, 0, w, 0, 0, w, h) }.getOrElse { return@withContext null }

    val r = IntArray(256); val g = IntArray(256); val b = IntArray(256); val lum = IntArray(256)
    for (p in pixels) {
        val rr = (p shr 16) and 0xFF
        val gg = (p shr 8) and 0xFF
        val bb = p and 0xFF
        r[rr]++; g[gg]++; b[bb]++
        lum[((rr * 299 + gg * 587 + bb * 114) / 1000).coerceIn(0, 255)]++
    }
    fun norm(a: IntArray): FloatArray {
        val max = (a.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        return FloatArray(256) { a[it] / max }
    }
    Histogram(norm(r), norm(g), norm(b), norm(lum))
}

@Composable
fun HistogramOverlay(histogram: Histogram, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(Color(0xAA000000))
            .padding(6.dp),
    ) {
        fun channel(values: FloatArray, color: Color, blend: BlendMode) {
            val stepX = size.width / 255f
            val path = Path().apply {
                moveTo(0f, size.height)
                for (i in 0..255) lineTo(i * stepX, size.height - values[i] * size.height)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, color = color, blendMode = blend)
        }
        channel(histogram.r, Color(0x99FF3B30), BlendMode.Screen)
        channel(histogram.g, Color(0x9934C759), BlendMode.Screen)
        channel(histogram.b, Color(0x990A84FF), BlendMode.Screen)
        drawLuma(histogram.lum)
    }
}

private fun DrawScope.drawLuma(values: FloatArray) {
    val stepX = size.width / 255f
    for (i in 1..255) {
        drawLine(
            color = Color(0xCCFFFFFF),
            start = Offset((i - 1) * stepX, size.height - values[i - 1] * size.height),
            end = Offset(i * stepX, size.height - values[i] * size.height),
            strokeWidth = 1.5f,
        )
    }
}
