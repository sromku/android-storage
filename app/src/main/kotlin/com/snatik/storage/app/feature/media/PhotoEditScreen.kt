package com.snatik.storage.app.feature.media

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import com.snatik.storage.app.util.readableSize
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class Aspect(val labelRes: Int, val ratio: Float?) // ratio = w/h, null = free

private val ASPECTS = listOf(
    Aspect(R.string.crop_free, null),
    Aspect(R.string.crop_square, 1f),
    Aspect(R.string.crop_portrait, 4f / 5f),
    Aspect(R.string.crop_story, 9f / 16f),
    Aspect(R.string.crop_landscape, 3f / 2f),
    Aspect(R.string.crop_wide, 16f / 9f),
)

/** Normalized crop rect [l,t,r,b] in 0..1 of the (oriented) image. */
private class CropState(l: Float, t: Float, r: Float, b: Float) {
    var l by mutableStateOf(l); var t by mutableStateOf(t); var r by mutableStateOf(r); var b by mutableStateOf(b)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditScreen(path: String, name: String, onBack: () -> Unit, onSaved: () -> Unit = onBack) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview by produceState<Bitmap?>(initialValue = null, path) { value = PhotoEditor.loadPreview(path) }
    val srcSize = remember(path) { PhotoEditor.sourceSize(path) }

    val crop = remember { CropState(0.05f, 0.05f, 0.95f, 0.95f) }
    var aspectIndex by remember { mutableStateOf(0) }
    var showExport by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    val imgW = srcSize?.width ?: 1
    val imgH = srcSize?.height ?: 1

    fun applyAspect(ratio: Float?) {
        if (ratio == null) return
        // Maximal centered rect of the given aspect, in normalized image space.
        val imgAspect = imgW.toFloat() / imgH
        val (nw, nh) = if (imgAspect > ratio) {
            val h = 1f; val w = (ratio / imgAspect); w to h
        } else {
            val w = 1f; val h = (imgAspect / ratio); w to h
        }
        val cx = 0.5f; val cy = 0.5f
        crop.l = cx - nw / 2; crop.r = cx + nw / 2
        crop.t = cy - nh / 2; crop.b = cy + nh / 2
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_photo_title), color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up), tint = Color.White) } },
                actions = {
                    TextButton(onClick = { showExport = true }, enabled = preview != null && !exporting) {
                        Text(stringResource(R.string.crop_export), color = if (preview != null) MaterialTheme.colorScheme.primary else Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bmp = preview
                if (bmp == null) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    CropCanvas(bmp, imgW, imgH, crop, ASPECTS[aspectIndex].ratio)
                }
            }
            // Aspect presets
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ASPECTS.forEachIndexed { i, a ->
                    FilterChip(
                        selected = aspectIndex == i,
                        onClick = { aspectIndex = i; applyAspect(a.ratio) },
                        label = { Text(stringResource(a.labelRes)) },
                    )
                }
            }
        }
    }

    if (showExport && srcSize != null) {
        ExportSheet(
            cropW = ((crop.r - crop.l) * imgW).toInt().coerceAtLeast(1),
            cropH = ((crop.b - crop.t) * imgH).toInt().coerceAtLeast(1),
            onDismiss = { showExport = false },
            onExport = { targetLongEdge ->
                showExport = false
                exporting = true
                scope.launch {
                    val uri = PhotoEditor.export(
                        context, path, name,
                        floatArrayOf(crop.l, crop.t, crop.r, crop.b),
                        targetLongEdge,
                    )
                    exporting = false
                    if (uri != null) {
                        Toast.makeText(context, R.string.crop_saved, Toast.LENGTH_SHORT).show()
                        onSaved()
                    } else {
                        Toast.makeText(context, R.string.crop_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

@Composable
private fun CropCanvas(bmp: Bitmap, imgW: Int, imgH: Int, crop: CropState, ratio: Float?) {
    val handlePx = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .pointerInput(imgW, imgH, ratio) {
                    // Displayed image rect (letterboxed) inside this canvas.
                    fun dispRect(): Rect {
                        val cw = size.width.toFloat(); val ch = size.height.toFloat()
                        val ia = imgW.toFloat() / imgH
                        val ca = cw / ch
                        return if (ia > ca) {
                            val h = cw / ia; Rect(0f, (ch - h) / 2f, cw, (ch + h) / 2f)
                        } else {
                            val w = ch * ia; Rect((cw - w) / 2f, 0f, (cw + w) / 2f, ch)
                        }
                    }
                    var grab = 0 // 0 none, 1 tl, 2 tr, 3 bl, 4 br, 5 move
                    detectDragGestures(
                        onDragStart = { pos ->
                            val d = dispRect()
                            val sx = d.left + crop.l * d.width; val ex = d.left + crop.r * d.width
                            val sy = d.top + crop.t * d.height; val ey = d.top + crop.b * d.height
                            grab = when {
                                abs(pos.x - sx) < handlePx && abs(pos.y - sy) < handlePx -> 1
                                abs(pos.x - ex) < handlePx && abs(pos.y - sy) < handlePx -> 2
                                abs(pos.x - sx) < handlePx && abs(pos.y - ey) < handlePx -> 3
                                abs(pos.x - ex) < handlePx && abs(pos.y - ey) < handlePx -> 4
                                pos.x in sx..ex && pos.y in sy..ey -> 5
                                else -> 0
                            }
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val d = dispRect()
                            val dxN = drag.x / d.width
                            val dyN = drag.y / d.height
                            val minSize = 0.08f
                            when (grab) {
                                5 -> {
                                    val w = crop.r - crop.l; val h = crop.b - crop.t
                                    var nl = (crop.l + dxN).coerceIn(0f, 1f - w)
                                    var nt = (crop.t + dyN).coerceIn(0f, 1f - h)
                                    crop.l = nl; crop.r = nl + w; crop.t = nt; crop.b = nt + h
                                }
                                in 1..4 -> {
                                    // Move the grabbed corner; opposite corner is the anchor.
                                    var nl = crop.l; var nt = crop.t; var nr = crop.r; var nb = crop.b
                                    when (grab) {
                                        1 -> { nl = (crop.l + dxN).coerceIn(0f, crop.r - minSize); nt = (crop.t + dyN).coerceIn(0f, crop.b - minSize) }
                                        2 -> { nr = (crop.r + dxN).coerceIn(crop.l + minSize, 1f); nt = (crop.t + dyN).coerceIn(0f, crop.b - minSize) }
                                        3 -> { nl = (crop.l + dxN).coerceIn(0f, crop.r - minSize); nb = (crop.b + dyN).coerceIn(crop.t + minSize, 1f) }
                                        4 -> { nr = (crop.r + dxN).coerceIn(crop.l + minSize, 1f); nb = (crop.b + dyN).coerceIn(crop.t + minSize, 1f) }
                                    }
                                    if (ratio != null) {
                                        // Enforce aspect: derive height from width in normalized *pixel* space.
                                        val wN = (nr - nl)
                                        val targetHpx = (wN * imgW) / ratio
                                        val hN = (targetHpx / imgH)
                                        when (grab) {
                                            1 -> nt = (nb - hN)
                                            2 -> nt = (nb - hN)
                                            3 -> nb = (nt + hN)
                                            4 -> nb = (nt + hN)
                                        }
                                        // If aspect pushed out of bounds, clamp by re-deriving width from height.
                                        if (nt < 0f || nb > 1f) {
                                            nt = nt.coerceIn(0f, 1f); nb = nb.coerceIn(0f, 1f)
                                            val hN2 = (nb - nt)
                                            val wPx = (hN2 * imgH) * ratio
                                            val wN2 = wPx / imgW
                                            when (grab) { 1, 3 -> nl = nr - wN2; 2, 4 -> nr = nl + wN2 }
                                        }
                                    }
                                    crop.l = nl.coerceIn(0f, 1f); crop.t = nt.coerceIn(0f, 1f)
                                    crop.r = nr.coerceIn(0f, 1f); crop.b = nb.coerceIn(0f, 1f)
                                }
                            }
                        },
                        onDragEnd = { grab = 0 },
                    )
                },
        ) {
            val cw = size.width; val ch = size.height
            val ia = imgW.toFloat() / imgH; val ca = cw / ch
            val d = if (ia > ca) {
                val h = cw / ia; Rect(0f, (ch - h) / 2f, cw, (ch + h) / 2f)
            } else {
                val w = ch * ia; Rect((cw - w) / 2f, 0f, (cw + w) / 2f, ch)
            }
            val sx = d.left + crop.l * d.width; val ex = d.left + crop.r * d.width
            val sy = d.top + crop.t * d.height; val ey = d.top + crop.b * d.height

            // Dim outside the crop.
            val dim = Color(0x99000000)
            drawRect(dim, topLeft = Offset(d.left, d.top), size = Size(d.width, sy - d.top))
            drawRect(dim, topLeft = Offset(d.left, ey), size = Size(d.width, d.bottom - ey))
            drawRect(dim, topLeft = Offset(d.left, sy), size = Size(sx - d.left, ey - sy))
            drawRect(dim, topLeft = Offset(ex, sy), size = Size(d.right - ex, ey - sy))

            // Thirds grid + border.
            val white = Color.White
            drawRect(white.copy(alpha = 0.9f), topLeft = Offset(sx, sy), size = Size(ex - sx, ey - sy), style = Stroke(width = 2f))
            val grid = white.copy(alpha = 0.4f)
            for (i in 1..2) {
                val gx = sx + (ex - sx) * i / 3f; val gy = sy + (ey - sy) * i / 3f
                drawLine(grid, Offset(gx, sy), Offset(gx, ey), 1f)
                drawLine(grid, Offset(sx, gy), Offset(ex, gy), 1f)
            }
            // Corner handles.
            val hl = 22f
            listOf(Offset(sx, sy), Offset(ex, sy), Offset(sx, ey), Offset(ex, ey)).forEach { c ->
                drawCircle(white, radius = 9f, center = c)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(cropW: Int, cropH: Int, onDismiss: () -> Unit, onExport: (Int?) -> Unit) {
    val cropLong = maxOf(cropW, cropH)
    // Recommended long-edge targets (only those that actually downscale, plus Original).
    val options = buildList {
        add(Triple(R.string.crop_size_original, null as Int?, cropLong))
        if (cropLong > 2048) add(Triple(R.string.crop_size_large, 2048, 2048))
        if (cropLong > 1080) add(Triple(R.string.crop_size_social, 1080, 1080))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(stringResource(R.string.crop_export_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
            Text(
                stringResource(R.string.crop_export_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            options.forEach { (labelRes, target, longEdge) ->
                val scale = longEdge.toFloat() / cropLong
                val w = (cropW * scale).toInt().coerceAtLeast(1)
                val h = (cropH * scale).toInt().coerceAtLeast(1)
                val bytes = PhotoEditor.estimateBytes(w, h, 95)
                Surface(
                    onClick = { onExport(target) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("$w × $h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("~${bytes.readableSize()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
