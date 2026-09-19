package com.snatik.storage.app.feature.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Tone-curve editor: channel selector + a draggable point curve on a grid. */
@Composable
fun CurveEditor(curve: ToneCurve, onChange: (ToneCurve) -> Unit) {
    var channel by remember { mutableIntStateOf(0) } // 0 RGB, 1 R, 2 G, 3 B
    val labels = listOf("RGB", "Red", "Green", "Blue")
    val lineColor = when (channel) {
        1 -> Color(0xFFFF5A5A); 2 -> Color(0xFF57D977); 3 -> Color(0xFF5AA9FF); else -> OnDark
    }
    fun channelPoints(c: ToneCurve) = when (channel) { 1 -> c.r; 2 -> c.g; 3 -> c.b; else -> c.rgb }
    fun withPoints(c: ToneCurve, pts: List<Pair<Float, Float>>) = when (channel) {
        1 -> c.copy(r = pts); 2 -> c.copy(g = pts); 3 -> c.copy(b = pts); else -> c.copy(rgb = pts)
    }
    val points = channelPoints(curve)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l -> DarkChip(selected = channel == i, label = l) { channel = i } }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0E0F11))
                .pointerInput(channel, points.size) {
                    detectDragGestures(
                        onDragStart = {},
                        onDrag = { change, _ ->
                            change.consume()
                            val px = (change.position.x / size.width).coerceIn(0f, 1f)
                            val py = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            val pts = channelPoints(curve).toMutableList()
                            // nearest point to the finger
                            var idx = 0; var best = Float.MAX_VALUE
                            pts.forEachIndexed { i, p ->
                                val d = (p.first - px) * (p.first - px) + (p.second - py) * (p.second - py)
                                if (d < best) { best = d; idx = i }
                            }
                            val minX = if (idx == 0) 0f else pts[idx - 1].first + 0.02f
                            val maxX = if (idx == pts.lastIndex) 1f else pts[idx + 1].first - 0.02f
                            val nx = if (idx == 0 || idx == pts.lastIndex) pts[idx].first else px.coerceIn(minX, maxX)
                            pts[idx] = nx to py
                            onChange(withPoints(curve, pts))
                        },
                    )
                }
                .pointerInput(channel) {
                    detectTapGestures(
                        onTap = { pos ->
                            val px = (pos.x / size.width).coerceIn(0f, 1f)
                            val py = (1f - pos.y / size.height).coerceIn(0f, 1f)
                            val pts = channelPoints(curve).toMutableList()
                            if (pts.none { kotlin.math.abs(it.first - px) < 0.04f } && pts.size < 8) {
                                pts.add(px to py); pts.sortBy { it.first }
                                onChange(withPoints(curve, pts))
                            }
                        },
                        onDoubleTap = { pos ->
                            val px = pos.x / size.width
                            val pts = channelPoints(curve).toMutableList()
                            val idx = pts.indices.minByOrNull { kotlin.math.abs(pts[it].first - px) } ?: return@detectTapGestures
                            if (idx != 0 && idx != pts.lastIndex) { pts.removeAt(idx); onChange(withPoints(curve, pts)) }
                        },
                    )
                },
        ) {
            val w = size.width; val h = size.height
            // grid
            val grid = Color(0x22FFFFFF)
            for (i in 1..3) {
                val gx = w * i / 4f; val gy = h * i / 4f
                drawLine(grid, Offset(gx, 0f), Offset(gx, h), 1f)
                drawLine(grid, Offset(0f, gy), Offset(w, gy), 1f)
            }
            drawLine(Color(0x33FFFFFF), Offset(0f, h), Offset(w, 0f), 1f) // identity diagonal
            // curve as a dense polyline through the LUT
            val path = Path()
            for (i in 0..64) {
                val x = i / 64f
                val y = ToneCurve.eval(points, x)
                val sx = x * w; val sy = (1f - y) * h
                if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
            }
            drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            // control points
            points.forEach { (x, y) ->
                drawCircle(lineColor, radius = 9f, center = Offset(x * w, (1f - y) * h))
                drawCircle(Color(0xFF0E0F11), radius = 4f, center = Offset(x * w, (1f - y) * h))
            }
        }
        androidx.compose.material3.Text(
            "Drag points  ·  tap to add  ·  double-tap to remove",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = OnDarkDim,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** HSL / colour mixer: pick a band, then adjust its hue, saturation and luminance. */
@Composable
fun HslEditor(hsl: List<Float>, onChange: (List<Float>) -> Unit) {
    var band by remember { mutableIntStateOf(0) }
    val bandScroll = rememberScrollState()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp).horizontalScroll(bandScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HslBand.entries.forEachIndexed { i, b -> DarkChip(selected = band == i, label = b.label) { band = i } }
        }
        fun set(offset: Int, v: Float) {
            val next = hsl.toMutableList(); next[band * 3 + offset] = v; onChange(next)
        }
        SliderRow("Hue", sign(hsl[band * 3]), hsl[band * 3], -100f..100f) { set(0, it) }
        SliderRow("Saturation", sign(hsl[band * 3 + 1]), hsl[band * 3 + 1], -100f..100f) { set(1, it) }
        SliderRow("Luminance", sign(hsl[band * 3 + 2]), hsl[band * 3 + 2], -100f..100f) { set(2, it) }
    }
}
