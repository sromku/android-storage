package com.snatik.storage.app.feature.media

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private class RegionSource(
    val decoder: BitmapRegionDecoder,
    val width: Int,
    val height: Int,
    /** A whole-image low-res layer, always drawn underneath so nothing is ever blank while decoding. */
    val base: android.graphics.Bitmap?,
)

private class Tile(val bitmap: android.graphics.Bitmap, val srcRect: Rect)

/**
 * Full-resolution pan/zoom viewer for very large images. Uses BitmapRegionDecoder to decode only
 * the visible region at a subsample matched to the current zoom, so a gigapixel image pans and
 * zooms with bounded memory instead of loading the whole bitmap and running out of heap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiledViewerScreen(path: String, onBack: () -> Unit, onEdit: (String) -> Unit = {}) {
    val source by produceState<RegionSource?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { openRegionDecoder(path) }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val editable = remember(path) { !isRawMedia(File(path).name, "") }

    androidx.compose.material3.Scaffold(
        containerColor = Color.Black,
        topBar = {
            val src = source
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text(File(path).name, maxLines = 1, color = Color.White)
                        if (src != null) {
                            val mp = src.width.toLong() * src.height / 1_000_000.0
                            Text(
                                "${src.width} × ${src.height}  ·  %.1f MP".format(mp),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up), tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions), tint = Color.White) }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (editable) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_crop)) },
                                leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null) },
                                onClick = { menu = false; onEdit(path) },
                            )
                        }
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share_send)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = { menu = false; com.snatik.storage.app.util.Intents.share(context, listOf(path)) },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_with)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                            onClick = { menu = false; com.snatik.storage.app.util.Intents.openWith(context, path) },
                        )
                    }
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
        // Allow deep digital zoom (up to 500% of actual pixels) so you can inspect past 1:1.
        val maxScale = maxOf(5f, fit * 4f)

        var scale by remember(src) { mutableFloatStateOf(fit) }
        var hudVisible by remember(src) { androidx.compose.runtime.mutableStateOf(true) }
        var offset by remember(src) {
            mutableStateOf(Offset((vw - src.width * fit) / 2f, (vh - src.height * fit) / 2f))
        }
        var tile by remember(src) { mutableStateOf<Tile?>(null) }
        val tileAlpha = remember(src) { Animatable(1f) }
        val tilePaint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }

        // Fade each freshly-decoded sharp region in over the low-res base.
        androidx.compose.runtime.LaunchedEffect(tile) {
            if (tile != null) {
                tileAlpha.snapTo(0f)
                tileAlpha.animateTo(1f, tween(220))
            }
        }

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

        // Show the zoom/quality readout on every zoom change, then fade it out when idle.
        androidx.compose.runtime.LaunchedEffect(scale) {
            hudVisible = true
            delay(1600)
            hudVisible = false
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(src) {
                    detectTapGestures(onDoubleTap = { p ->
                        // Toggle between fit and 1:1 actual pixels (the point of "best quality").
                        val target = if (scale > fit * 1.5f) fit else 1f.coerceAtMost(maxScale)
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
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                // 1) Whole-image low-res base - always present, so zoom/pan never shows black gaps.
                src.base?.let { base ->
                    val l = offset.x
                    val t = offset.y
                    native.drawBitmap(
                        base,
                        null,
                        android.graphics.RectF(l, t, l + src.width * scale, t + src.height * scale),
                        tilePaint,
                    )
                }
                // 2) Sharp visible region, faded in on top once decoded.
                tile?.let { tl ->
                    val dstLeft = tl.srcRect.left * scale + offset.x
                    val dstTop = tl.srcRect.top * scale + offset.y
                    tilePaint.alpha = (tileAlpha.value * 255f).toInt().coerceIn(0, 255)
                    native.drawBitmap(
                        tl.bitmap,
                        null,
                        android.graphics.RectF(dstLeft, dstTop, dstLeft + tl.srcRect.width() * scale, dstTop + tl.srcRect.height() * scale),
                        tilePaint,
                    )
                    tilePaint.alpha = 255
                }
            }
        }

        ZoomHud(
            scalePercent = (scale * 100f).roundToInt(),
            digital = scale > 1.02f,
            visible = hudVisible,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun ZoomHud(scalePercent: Int, digital: Boolean, visible: Boolean, modifier: Modifier) {
    val green = Color(0xFF34C759)
    val amber = Color(0xFFFFB300)
    val accent = if (digital) amber else green
    val label = when {
        digital -> stringResource(R.string.zoom_digital)
        scalePercent >= 98 -> stringResource(R.string.zoom_actual_pixels)
        else -> stringResource(R.string.zoom_fit_quality)
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        androidx.compose.foundation.layout.Row(
            Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(Color(0xE6000000))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(9.dp),
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(9.dp)) { drawCircle(accent) }
            Text("$scalePercent%", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text(label, color = accent, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
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
    val w = decoder.width
    val h = decoder.height
    // Low-res whole-image base: subsample so the long edge is ~1536px (a few MB, always resident).
    var baseSample = 1
    while (max(w, h) / baseSample > 1536) baseSample *= 2
    val base = runCatching {
        decoder.decodeRegion(Rect(0, 0, w, h), BitmapFactory.Options().apply { inSampleSize = baseSample })
    }.getOrNull()
    RegionSource(decoder, w, h, base)
}.getOrNull()

/**
 * True when a full-resolution tiled view is worthwhile. RAW files qualify - we extract their
 * full-size embedded JPEG first - as do large images in a format BitmapRegionDecoder can subsample.
 */
fun supportsTiling(item: MediaItem): Boolean {
    if (item.isVideo || item.path.isBlank()) return false
    if (isRawMedia(item.name, item.mime)) return true
    val mp = item.width.toLong() * item.height
    if (mp < 20_000_000) return false
    val mime = item.mime.lowercase()
    return mime in setOf("image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif")
}
