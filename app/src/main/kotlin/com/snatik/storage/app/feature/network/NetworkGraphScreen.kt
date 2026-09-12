package com.snatik.storage.app.feature.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.util.fullDateTime
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.apps.AppNetworkUsage
import com.snatik.storage.core.apps.UsageBucket
import org.koin.androidx.compose.koinViewModel
import java.util.Calendar
import kotlin.math.roundToInt

private enum class Metric(val labelRes: Int) { TOTAL(R.string.net_metric_total), RX(R.string.net_metric_down), TX(R.string.net_metric_up) }
private enum class Split(val labelRes: Int) { OFF(R.string.net_split_off), STATE(R.string.net_metric_split) }
private enum class ChartType { AREA, BARS }
private enum class Measure(val labelRes: Int) { DATA(R.string.net_unit_data), PACKETS(R.string.net_unit_packets) }
private enum class Range(val labelRes: Int, val ms: Long) {
    H6(R.string.net_range_6h, 6 * 3_600_000L),
    D1(R.string.net_range_1d, 24 * 3_600_000L),
    D3(R.string.net_range_3d, 72 * 3_600_000L),
    ALL(R.string.net_range_all, Long.MAX_VALUE),
}

private const val SLOT_MS = 7_200_000L // netstats records uid history in 2-hour buckets

private fun UsageBucket.value(m: Metric, u: Measure): Long = when (u) {
    Measure.DATA -> when (m) { Metric.RX -> rxBytes; Metric.TX -> txBytes; else -> totalBytes }
    Measure.PACKETS -> when (m) { Metric.RX -> rxPackets; Metric.TX -> txPackets; else -> totalPackets }
}

private fun fmt(v: Long, u: Measure): String = if (u == Measure.DATA) v.readableSize() else "%,d".format(v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkGraphScreen(packageName: String, onBack: () -> Unit, viewModel: NetworkViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = state.forPackage(packageName)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(app?.label ?: stringResource(R.string.net_title), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(stringResource(R.string.net_over_time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                app == null && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                app == null -> Text(stringResource(R.string.net_no_history), modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Graph(app)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Graph(app: AppNetworkUsage) {
    val ctx = LocalContext.current
    var range by remember { mutableStateOf(Range.D1) }
    var metric by remember { mutableStateOf(Metric.TOTAL) }
    var split by remember { mutableStateOf(Split.OFF) }
    var unit by remember { mutableStateOf(Measure.DATA) }
    // Open as bars, matching the mini "Data over time" chart the user tapped.
    var chartType by remember { mutableStateOf(ChartType.BARS) }
    var scrub by remember { mutableStateOf<Int?>(null) }

    val now = remember { System.currentTimeMillis() }
    val byStart = remember(app) { app.buckets.associateBy { it.startMs } }
    // Build a zero-filled 2h timeline across the selected range so gaps read as real lulls.
    val span = if (range == Range.ALL) (now - app.firstMs).coerceAtLeast(SLOT_MS) else range.ms
    val startAligned = ((now - span) / SLOT_MS) * SLOT_MS
    val slots = remember(range, app, now) {
        buildList { var t = startAligned; while (t < now && size < 512) { add(t); t += SLOT_MS } }
    }
    val stacked = split == Split.STATE
    val cells = slots.map { byStart[it] }
    // Foreground / background value of the chosen direction and unit — every combination is valid.
    fun UsageBucket.fgVal(m: Metric, u: Measure) = when (u) {
        Measure.DATA -> when (m) { Metric.RX -> fgRxBytes; Metric.TX -> fgTxBytes; else -> foregroundBytes }
        Measure.PACKETS -> when (m) { Metric.RX -> fgRxPackets; Metric.TX -> fgTxPackets; else -> foregroundPackets }
    }
    fun UsageBucket.bgVal(m: Metric, u: Measure) = when (u) {
        Measure.DATA -> when (m) { Metric.RX -> bgRxBytes; Metric.TX -> bgTxBytes; else -> backgroundBytes }
        Measure.PACKETS -> when (m) { Metric.RX -> bgRxPackets; Metric.TX -> bgTxPackets; else -> backgroundPackets }
    }
    val fgSeries = cells.map { it?.fgVal(metric, unit) ?: 0L }
    val bgSeries = cells.map { it?.bgVal(metric, unit) ?: 0L }
    val values = if (stacked) fgSeries.indices.map { fgSeries[it] + bgSeries[it] } else cells.map { it?.value(metric, unit) ?: 0L }
    val inRange = app.buckets.filter { it.startMs >= startAligned }

    val windowTotal = if (stacked) inRange.sumOf { it.fgVal(metric, unit) + it.bgVal(metric, unit) } else inRange.sumOf { it.value(metric, unit) }
    val peakIdx = values.indices.maxByOrNull { values[it] }?.takeIf { values.isNotEmpty() && values[it] > 0 }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Headline — the scrubbed point, or the window total at rest.
        val scrubbed = scrub?.let { it to values.getOrElse(it) { 0L } }
        Column {
            Text(
                if (scrubbed != null) stringResource(metric.labelRes) else stringResource(R.string.net_window_total),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
            Text(
                fmt(scrubbed?.second ?: windowTotal, unit),
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
            )
            Text(
                scrubbed?.let { s ->
                    val base = slotLabel(slots[s.first], ctx)
                    if (stacked) "$base · ${stringResource(R.string.net_foreground)} ${fmt(fgSeries[s.first], unit)} · ${stringResource(R.string.net_background)} ${fmt(bgSeries[s.first], unit)}" else base
                } ?: rangeSubtitle(range, startAligned, ctx),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The chart
        val fgColor = MaterialTheme.colorScheme.primary
        val bgColor = MaterialTheme.colorScheme.secondary
        val bands = if (stacked)
            listOf(Band(fgSeries, fgColor, stringResource(R.string.net_foreground)), Band(bgSeries, bgColor, stringResource(R.string.net_background)))
        else listOf(Band(values, fgColor, ""))
        UsageGraph(bands = bands, type = chartType, onType = { chartType = it }, scrub = scrub, onScrub = { scrub = it })

        // Range chips
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Range.entries.forEachIndexed { i, r ->
                SegmentedButton(
                    selected = range == r,
                    onClick = { range = r; scrub = null },
                    shape = SegmentedButtonDefaults.itemShape(i, Range.entries.size),
                ) { Text(stringResource(r.labelRes)) }
            }
        }
        // Metric
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Metric.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = metric == m,
                    onClick = { metric = m; scrub = null },
                    shape = SegmentedButtonDefaults.itemShape(i, Metric.entries.size),
                ) { Text(stringResource(m.labelRes)) }
            }
        }
        // Split (state) and unit share a row — both are independent of the direction above.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                Split.entries.forEachIndexed { i, sp ->
                    SegmentedButton(
                        selected = split == sp,
                        onClick = { split = sp; scrub = null },
                        shape = SegmentedButtonDefaults.itemShape(i, Split.entries.size),
                    ) { Text(stringResource(sp.labelRes), maxLines = 1) }
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                Measure.entries.forEachIndexed { i, u ->
                    SegmentedButton(
                        selected = unit == u,
                        onClick = { unit = u; scrub = null },
                        shape = SegmentedButtonDefaults.itemShape(i, Measure.entries.size),
                    ) { Text(stringResource(u.labelRes), maxLines = 1) }
                }
            }
        }

        if (inRange.none { it.totalBytes > 0 }) {
            Text(stringResource(R.string.net_no_range_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        // Window stats
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.net_peak), peakIdx?.let { fmt(values[it], unit) } ?: "—", peakIdx?.let { slots[it].relativeTime(ctx) }, Modifier.weight(1f))
            val avg = if (slots.isNotEmpty()) windowTotal / slots.size else 0L
            StatCard(stringResource(R.string.net_average), fmt(avg, unit), null, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val rx = inRange.sumOf { it.rxBytes }; val tx = inRange.sumOf { it.txBytes }
            val tot = (rx + tx).coerceAtLeast(1)
            val down = (rx * 100 / tot).toInt()
            StatCard(stringResource(R.string.net_ratio), "$down% : ${100 - down}%", null, Modifier.weight(1f))
            val bytes = inRange.sumOf { it.totalBytes }; val pkts = inRange.sumOf { it.totalPackets }.coerceAtLeast(1)
            StatCard(stringResource(R.string.net_avg_packet), (bytes / pkts).readableSize(), null, Modifier.weight(1f))
        }

        // Composition
        SectionTitle(stringResource(R.string.net_composition))
        SplitBar(stringResource(R.string.net_wifi), inRange.sumOf { it.wifiBytes }, MaterialTheme.colorScheme.primary, stringResource(R.string.net_mobile), inRange.sumOf { it.mobileBytes }, MaterialTheme.colorScheme.tertiary)
        SplitBar(stringResource(R.string.net_foreground), inRange.sumOf { it.foregroundBytes }, MaterialTheme.colorScheme.primary, stringResource(R.string.net_background), inRange.sumOf { it.backgroundBytes }, MaterialTheme.colorScheme.secondary)

        // Hour-of-day pattern
        SectionTitle(stringResource(R.string.net_by_hour))
        HourStrip(inRange)

        Spacer(Modifier.height(8.dp))
    }
}

private data class Band(val values: List<Long>, val color: Color, val label: String)

/** Robinhood-style chart with press-and-drag scrubbing. Renders as a smooth area or as columns;
 *  a single band fills a gradient, multiple bands stack (fg + bg). The area/bars switch sits in
 *  the chart's top-right corner. */
@Composable
private fun UsageGraph(bands: List<Band>, type: ChartType, onType: (ChartType) -> Unit, scrub: Int?, onScrub: (Int?) -> Unit) {
    val grid = MaterialTheme.colorScheme.surfaceContainerHighest
    val crosshair = MaterialTheme.colorScheme.onSurfaceVariant
    var widthPx by remember { mutableStateOf(1f) }
    val n = bands.firstOrNull()?.values?.size ?: 0
    val stacked = bands.size > 1
    val totals = List(n) { i -> bands.sumOf { it.values[i] } }
    val maxV = (totals.maxOrNull() ?: 1L).coerceAtLeast(1L)

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Box {
            Column {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(top = 18.dp, bottom = 10.dp)
                        .onSizeChanged { widthPx = it.width.toFloat() }
                        .pointerInput(n) {
                            if (n < 2) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun idx(x: Float) = ((x / widthPx) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                                onScrub(idx(down.position.x))
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val c = e.changes.first()
                                    if (!c.pressed) break
                                    onScrub(idx(c.position.x))
                                    c.consume()
                                }
                                onScrub(null)
                            }
                        },
                ) {
                    if (n < 2) return@Canvas
                    val h = size.height
                    val w = size.width
                    val top = h * 0.06f
                    fun x(i: Int) = w * i / (n - 1)
                    fun y(v: Long) = h - (v.toFloat() / maxV) * (h - top)

                    for (g in 0..2) {
                        val gy = top + (h - top) * g / 2f
                        drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                    }

                    if (type == ChartType.BARS) {
                        val gap = (w / n * 0.28f).coerceIn(1.5f, 8f)
                        val bw = ((w - gap * (n - 1)) / n).coerceAtLeast(1f)
                        for (i in 0 until n) {
                            val x0 = i * (bw + gap)
                            var base = h
                            for (b in bands) {
                                val bh = (b.values[i].toFloat() / maxV) * (h - top)
                                if (bh > 0.5f) {
                                    drawRect(b.color, topLeft = Offset(x0, base - bh), size = androidx.compose.ui.geometry.Size(bw, bh))
                                    base -= bh
                                }
                            }
                        }
                        scrub?.let { s ->
                            val si = s.coerceIn(0, n - 1)
                            val cx = si * (bw + gap) + bw / 2
                            drawLine(crosshair, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                        }
                    } else {
                        fun smooth(pts: List<Offset>) = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (i in 0 until pts.size - 1) {
                                val cx = (pts[i].x + pts[i + 1].x) / 2
                                cubicTo(cx, pts[i].y, cx, pts[i + 1].y, pts[i + 1].x, pts[i + 1].y)
                            }
                        }
                        // cumulative upper edge per band; draw largest first so lower bands paint on top.
                        val cum = MutableList(n) { 0L }
                        val edges = bands.map { b -> List(n) { i -> cum[i] += b.values[i]; cum[i] } }
                        for (bi in bands.indices.reversed()) {
                            val topLine = smooth(List(n) { Offset(x(it), y(edges[bi][it])) })
                            val area = Path().apply { addPath(topLine); lineTo(w, h); lineTo(0f, h); close() }
                            if (stacked) {
                                drawPath(area, color = bands[bi].color.copy(alpha = 0.85f))
                            } else {
                                drawPath(area, Brush.verticalGradient(listOf(bands[bi].color.copy(alpha = 0.28f), bands[bi].color.copy(alpha = 0.02f)), startY = top, endY = h))
                                drawPath(topLine, color = bands[bi].color, style = Stroke(width = 3f))
                            }
                        }
                        if (stacked) drawPath(smooth(List(n) { Offset(x(it), y(edges.last()[it])) }), color = bands.first().color, style = Stroke(width = 2f))
                        scrub?.let { s ->
                            val si = s.coerceIn(0, n - 1)
                            val sx = x(si)
                            val sy = y(totals[si])
                            drawLine(crosshair, Offset(sx, 0f), Offset(sx, h), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                            drawCircle(bands.first().color, radius = 7f, center = Offset(sx, sy))
                            drawCircle(Color.White, radius = 3f, center = Offset(sx, sy))
                        }
                    }
                }
                if (stacked) {
                    Row(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        bands.forEach { b ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(b.color))
                                Text(b.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            // chart-type switch, top-right corner
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Row {
                    ChartTypeButton(Icons.Default.ShowChart, type == ChartType.AREA) { onType(ChartType.AREA) }
                    ChartTypeButton(Icons.Default.BarChart, type == ChartType.BARS) { onType(ChartType.BARS) }
                }
            }
        }
    }
}

@Composable
private fun ChartTypeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SplitBar(aLabel: String, aBytes: Long, aColor: Color, bLabel: String, bBytes: Long, bColor: Color) {
    val total = (aBytes + bBytes).coerceAtLeast(1L)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(track)) {
                val aw = aBytes.toFloat() / total
                val bw = bBytes.toFloat() / total
                if (aw > 0f) Box(modifier = Modifier.fillMaxSize().weight(aw.coerceAtLeast(0.001f)).background(aColor))
                if (bw > 0f) Box(modifier = Modifier.fillMaxSize().weight(bw.coerceAtLeast(0.001f)).background(bColor))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Legend(aColor, aLabel, aBytes)
                Legend(bColor, bLabel, bBytes)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, bytes: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(bytes.readableSize(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 12 two-hour bins (0..22h) showing when this app typically transfers. */
@Composable
private fun HourStrip(buckets: List<UsageBucket>) {
    val bins = LongArray(12)
    val cal = Calendar.getInstance()
    for (b in buckets) {
        cal.timeInMillis = b.startMs
        bins[cal.get(Calendar.HOUR_OF_DAY) / 2] += b.totalBytes
    }
    val max = (bins.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                bins.forEach { v ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / max).coerceIn(0.02f, 1f)).align(Alignment.BottomCenter).clip(RoundedCornerShape(3.dp)).background(if (v > 0) bar else track))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0", "6", "12", "18", "24").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private fun slotLabel(startMs: Long, ctx: android.content.Context): String = startMs.fullDateTime(ctx)

@Composable
private fun rangeSubtitle(range: Range, startMs: Long, ctx: android.content.Context): String =
    when (range) {
        Range.ALL -> stringResource(R.string.net_first_seen, startMs.relativeTime(ctx))
        else -> stringResource(range.labelRes) + " · " + startMs.relativeTime(ctx)
    }
