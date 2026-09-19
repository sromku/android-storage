package com.snatik.storage.app.feature.media

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.snatik.storage.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private class RegionSource(val decoder: BitmapRegionDecoder, val width: Int, val height: Int)

private class Tile(val bitmap: android.graphics.Bitmap, val srcRect: Rect)

/**
 * Full-resolution pan/zoom viewer for very large images. Uses BitmapRegionDecoder to decode only
 * the visible region at a subsample matched to the current zoom, so a gigapixel image pans and
 * zooms with bounded memory instead of loading the whole bitmap and running out of heap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiledViewerScreen(path: String, onBack: () -> Unit) {
    val source by produceState<RegionSource?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { openRegionDecoder(path) }
    }

    androidx.compose.material3.Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(File(path).name, maxLines = 1, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up), tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f)),
            )
        },
    ) { padding ->
        val src = source
        if (src != null) {
            TiledCanvas(src, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun TiledCanvas(src: RegionSource, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        val fit = min(vw / src.width, vh / src.height)
        val maxScale = fit * 24f

        var scale by remember(src) { mutableFloatStateOf(fit) }
        var offset by remember(src) {
            mutableStateOf(Offset((vw - src.width * fit) / 2f, (vh - src.height * fit) / 2f))
        }
        var tile by remember(src) { mutableStateOf<Tile?>(null) }

        fun clampOffset(s: Float, o: Offset): Offset {
            val contentW = src.width * s
            val contentH = src.height * s
            val x = if (contentW <= vw) (vw - contentW) / 2f else o.x.coerceIn(vw - contentW, 0f)
            val y = if (contentH <= vh) (vh - contentH) / 2f else o.y.coerceIn(vh - contentH, 0f)
            return Offset(x, y)
        }

        // Decode the visible region whenever the view settles, cancelling stale decodes.
        androidx.compose.runtime.LaunchedEffect(src, vw, vh) {
            snapshotFlow { scale to offset }.collectLatest { (s, o) ->
                delay(60)
                tile = withContext(Dispatchers.IO) { decodeVisible(src, vw, vh, s, o) }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(src) {
                    detectTapGestures(onDoubleTap = { p ->
                        val target = if (scale > fit * 1.5f) fit else min(fit * 8f, maxScale)
                        // zoom toward the tapped point
                        val focus = (p - offset) / scale
                        scale = target
                        offset = clampOffset(target, p - focus * target)
                    })
                }
                .pointerInput(src) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(fit, maxScale)
                        val focus = (centroid - offset) / scale
                        val newOffset = clampOffset(newScale, centroid - focus * newScale + pan)
                        scale = newScale
                        offset = newOffset
                    }
                },
        ) {
            val t = tile ?: return@Canvas
            drawIntoCanvas {
                val dstLeft = (t.srcRect.left * scale + offset.x)
                val dstTop = (t.srcRect.top * scale + offset.y)
                val dstW = (t.srcRect.width() * scale)
                val dstH = (t.srcRect.height() * scale)
                it.nativeCanvas.drawBitmap(
                    t.bitmap,
                    null,
                    android.graphics.RectF(dstLeft, dstTop, dstLeft + dstW, dstTop + dstH),
                    null,
                )
            }
        }
    }
}

private fun decodeVisible(src: RegionSource, vw: Float, vh: Float, scale: Float, offset: Offset): Tile? {
    // Visible region in image coordinates.
    val left = ((-offset.x) / scale).toInt().coerceIn(0, src.width - 1)
    val top = ((-offset.y) / scale).toInt().coerceIn(0, src.height - 1)
    val right = ((vw - offset.x) / scale).roundToInt().coerceIn(left + 1, src.width)
    val bottom = ((vh - offset.y) / scale).roundToInt().coerceIn(top + 1, src.height)
    val rect = Rect(left, top, right, bottom)

    // One image pixel maps to `scale` screen pixels; subsample when many image px per screen px.
    var sample = 1
    while (sample * 2 <= max(1f, 1f / scale).toInt().coerceAtLeast(1)) sample *= 2

    return runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = src.decoder.decodeRegion(rect, opts) ?: return null
        Tile(bmp, rect)
    }.getOrNull()
}

private fun openRegionDecoder(path: String): RegionSource? = runCatching {
    val file = File(path)
    if (!file.isFile) return null
    @Suppress("DEPRECATION")
    val decoder = BitmapRegionDecoder.newInstance(path, false) ?: return null
    RegionSource(decoder, decoder.width, decoder.height)
}.getOrNull()

/** True for formats BitmapRegionDecoder can subsample and that are big enough to warrant tiling. */
fun supportsTiling(item: MediaItem): Boolean {
    if (item.isVideo || item.path.isBlank()) return false
    val mp = item.width.toLong() * item.height
    if (mp < 20_000_000) return false
    val mime = item.mime.lowercase()
    return mime in setOf("image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif")
}
