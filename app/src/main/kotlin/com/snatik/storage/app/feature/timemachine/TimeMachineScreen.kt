package com.snatik.storage.app.feature.timemachine

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.AppGrowth
import com.snatik.storage.core.apps.AppHistory
import com.snatik.storage.core.apps.DeviceTelemetry
import com.snatik.storage.core.apps.TelemetryRepository
import com.snatik.storage.core.apps.TimeMachineReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class TimeMachineViewModel(private val repo: TelemetryRepository) : ViewModel() {
    private val _report = MutableStateFlow<TimeMachineReport?>(null)
    val report: StateFlow<TimeMachineReport?> = _report.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _selected = MutableStateFlow<AppHistory?>(null)
    val selected: StateFlow<AppHistory?> = _selected.asStateFlow()
    val running: StateFlow<Boolean> = repo.running

    init {
        viewModelScope.launch {
            val first = repo.report()
            // Nothing recorded yet: take an opening snapshot so the tool isn't a blank slate.
            if (first.snapshots == 0) {
                _busy.value = true
                runCatching { repo.capture() }
                _report.value = repo.report()
                _busy.value = false
            } else {
                _report.value = first
            }
        }
    }

    fun refresh() { viewModelScope.launch { _report.value = repo.report() } }

    fun captureNow() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { repo.capture() }
            _report.value = repo.report()
            _busy.value = false
        }
    }

    fun selectApp(g: AppGrowth) { viewModelScope.launch { _selected.value = repo.appHistory(g.packageName) } }
    fun clearApp() { _selected.value = null }

    fun usageAccessIntent() = repo.usageAccessIntent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeMachineScreen(onBack: () -> Unit, viewModel: TimeMachineViewModel = koinViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The service captures in the background; pull the fresh series in when it starts/stops.
    androidx.compose.runtime.LaunchedEffect(running) { viewModel.refresh() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tm_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                CaptureCard(report, busy, viewModel::captureNow, running) { on ->
                    if (on) TelemetryRecorderService.start(context) else TelemetryRecorderService.stop(context)
                }
            }
            val r = report
            if (r != null && !r.hasUsageAccess) item {
                UsageAccessCard { runCatching { context.startActivity(viewModel.usageAccessIntent()) } }
            }
            when {
                r == null -> {}
                r.snapshots < 2 -> item {
                    EmptyState(Icons.Default.History, stringResource(R.string.tm_need_more), stringResource(R.string.tm_need_more_body), modifier = Modifier.height(220.dp))
                }
                else -> {
                    r.forecast?.let { item { ForecastHero(r) } }
                    item { ForecastChartCard(r) }
                    if (r.appCountLast > 0) item { InventoryCard(r) }
                    item { RamCard(r.device) }
                    if (r.topGrowers.isNotEmpty()) item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.tm_growers), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.tm_growers_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                r.topGrowers.forEach { g -> GrowerRow(g) { viewModel.selectApp(g) } }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { hist ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { viewModel.clearApp() }, sheetState = sheetState) {
            AppHistorySheet(hist)
        }
    }
}

/* ---------- capture / schedule ---------- */

@Composable
private fun CaptureCard(r: TimeMachineReport?, busy: Boolean, onCapture: () -> Unit, running: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.tm_schedule), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (running) Tag(stringResource(R.string.tools_recording), MaterialTheme.colorScheme.error)
                    }
                    Text(stringResource(R.string.tm_schedule_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = running, onCheckedChange = onToggle)
            }
            if (r != null && r.snapshots > 0) {
                val last = DateUtils.getRelativeTimeSpanString(r.lastTs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                val status = if (r.snapshots >= 2) stringResource(R.string.tm_status, r.snapshots, humanDuration(r.spanMs), last)
                    else stringResource(R.string.tm_status_one, last)
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onCapture, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) stringResource(R.string.tm_capturing) else stringResource(R.string.tm_capture_now))
            }
        }
    }
}

@Composable
private fun UsageAccessCard(onGrant: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tm_usage_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(stringResource(R.string.tm_usage_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            OutlinedButton(onClick = onGrant) { Text(stringResource(R.string.tm_usage_grant)) }
        }
    }
}

/* ---------- forecast hero ---------- */

@Composable
private fun ForecastHero(r: TimeMachineReport) {
    val f = r.forecast ?: return
    val losing = f.bytesPerDay > 0 && f.daysUntilFull != null
    val container = if (losing && (f.daysUntilFull ?: Double.MAX_VALUE) < 30) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (losing && (f.daysUntilFull ?: Double.MAX_VALUE) < 30) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning.takeIf { losing } ?: Icons.Default.History, contentDescription = null, tint = onContainer)
                Text(stringResource(R.string.tm_forecast), style = MaterialTheme.typography.titleMedium, color = onContainer)
            }
            if (losing) {
                val days = f.daysUntilFull!!.roundToInt()
                Text(stringResource(R.string.tm_full_in, days), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = onContainer)
                r.projectedFullTs?.let { Text(stringResource(R.string.tm_full_on, dateFmt(it)), style = MaterialTheme.typography.bodyMedium, color = onContainer) }
                Text(stringResource(R.string.tm_losing, human(f.bytesPerDay.roundToInt().toLong())), style = MaterialTheme.typography.bodyMedium, color = onContainer)
            } else {
                Text(stringResource(if (f.bytesPerDay < 0) R.string.tm_gaining_head else R.string.tm_stable_head), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onContainer)
                if (f.bytesPerDay < 0) Text(stringResource(R.string.tm_gaining, human(abs(f.bytesPerDay).roundToInt().toLong())), style = MaterialTheme.typography.bodyMedium, color = onContainer)
            }
            if (!f.confident) Text(stringResource(R.string.tm_low_confidence), style = MaterialTheme.typography.labelSmall, color = onContainer.copy(alpha = 0.8f))
            if (r.cacheBytesPerDay > 0) Text(stringResource(R.string.tm_cache_velocity, human(r.cacheBytesPerDay.roundToInt().toLong())), style = MaterialTheme.typography.bodyMedium, color = onContainer.copy(alpha = 0.9f))
        }
    }
}

/* ---------- time-axis free-space chart with projection ---------- */

@Composable
private fun ForecastChartCard(r: TimeMachineReport) {
    val data = r.device
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tm_free_over_time, r.snapshots), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val line = MaterialTheme.colorScheme.primary
            val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            val grid = MaterialTheme.colorScheme.outlineVariant
            val proj = MaterialTheme.colorScheme.error
            val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
            val density = LocalDensity.current
            val total = data.last().totalBytes.toFloat().coerceAtLeast(1f)
            val firstTs = data.first().ts
            val lastTs = data.last().ts
            val dayMs = 24.0 * 3600 * 1000
            // Draw the projection to full, but cap the visible horizon at +90 days for readability.
            val fullTs = r.projectedFullTs
            val capTs = lastTs + (90 * dayMs).toLong()
            val drawEndTs = fullTs?.coerceAtMost(capTs) ?: lastTs
            val xMinTs = firstTs
            val xMaxTs = drawEndTs.coerceAtLeast(lastTs + 1)

            Canvas(modifier = Modifier.fillMaxWidth().height(190.dp)) {
                val padL = 8f; val padR = 8f; val padT = 10f; val padB = with(density) { 22.dp.toPx() }
                val w = size.width - padL - padR
                val h = size.height - padT - padB
                fun x(ts: Long) = padL + ((ts - xMinTs).toFloat() / (xMaxTs - xMinTs).toFloat()) * w
                fun y(v: Float) = padT + (1f - (v / total)) * h  // 0..total, 0 = full at the bottom

                // floor = "full"
                drawLine(grid, Offset(padL, y(0f)), Offset(size.width - padR, y(0f)), strokeWidth = 2f)

                // historical area + line
                val area = Path().apply { moveTo(x(firstTs), y(0f)) }
                val hist = Path()
                data.forEachIndexed { i, d ->
                    val px = x(d.ts); val py = y(d.freeBytes.toFloat())
                    if (i == 0) hist.moveTo(px, py) else hist.lineTo(px, py)
                    area.lineTo(px, py)
                }
                area.lineTo(x(lastTs), y(0f)); area.close()
                drawPath(area, fillColor)
                drawPath(hist, line, style = Stroke(width = 4f))
                data.forEach { d -> drawCircle(line, radius = 4f, center = Offset(x(d.ts), y(d.freeBytes.toFloat()))) }

                // dashed projection from the last point toward full
                if (fullTs != null) {
                    val lastFree = data.last().freeBytes.toFloat()
                    val endFree = (lastFree - (f_bytesPerDay(r)) * ((drawEndTs - lastTs) / dayMs)).toFloat().coerceAtLeast(0f)
                    // faint vertical divider at "now" so measured vs projected reads clearly
                    drawLine(grid, Offset(x(lastTs), padT), Offset(x(lastTs), y(0f)), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                    val dash = Path().apply { moveTo(x(lastTs), y(lastFree)); lineTo(x(drawEndTs), y(endFree)) }
                    drawPath(dash, proj, style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
                    if (fullTs <= capTs) drawCircle(proj, radius = 6f, center = Offset(x(fullTs), y(0f)))
                }

                // axis labels
                val paint = android.graphics.Paint().apply { color = labelArgb; textSize = with(density) { 10.dp.toPx() }; isAntiAlias = true }
                drawContext.canvas.nativeCanvas.drawText(dateFmt(firstTs), padL, size.height - 4f, paint)
                val rightLabel = if (fullTs != null && fullTs <= capTs) dateFmt(fullTs) else dateFmt(xMaxTs)
                paint.textAlign = android.graphics.Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(rightLabel, size.width - padR, size.height - 4f, paint)
                paint.textAlign = android.graphics.Paint.Align.LEFT
                drawContext.canvas.nativeCanvas.drawText(human(total.toLong()), padL, padT + with(density) { 9.dp.toPx() }, paint)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.tm_free_now, human(data.last().freeBytes)), style = MonoStyle)
                Text(human(data.last().totalBytes), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun f_bytesPerDay(r: TimeMachineReport): Double = r.forecast?.bytesPerDay ?: 0.0

/* ---------- inventory (app count + total app storage over time) ---------- */

@Composable
private fun InventoryCard(r: TimeMachineReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.tm_inventory), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InventoryRow(stringResource(R.string.tm_inv_apps), r.appCountLast.toString(), if (r.appCountDelta != 0) signed(r.appCountDelta.toLong(), false) else null)
            InventoryRow(stringResource(R.string.tm_inv_app_storage), human(r.appTotalLast), if (r.appTotalDelta != 0L) signed(r.appTotalDelta, true) else null)
        }
    }
}

@Composable
private fun InventoryRow(label: String, value: String, delta: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        delta?.let {
            val up = it.startsWith("+")
            Text(it, style = MaterialTheme.typography.labelMedium, color = if (up) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
        }
        Text(value, style = MonoStyle, fontWeight = FontWeight.Medium)
    }
}

private fun signed(v: Long, bytes: Boolean): String {
    val sign = if (v >= 0) "+" else "-"
    return sign + if (bytes) human(abs(v)) else abs(v).toString()
}

/* ---------- RAM ---------- */

@Composable
private fun RamCard(data: List<DeviceTelemetry>) {
    val last = data.last()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(stringResource(R.string.tm_ram), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.tm_ram_now, human(last.ramFreeBytes), human(last.ramTotalBytes)), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val color = MaterialTheme.colorScheme.tertiary
            val grid = MaterialTheme.colorScheme.outlineVariant
            Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                if (data.size < 2) return@Canvas
                drawLine(grid, Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f), strokeWidth = 1f)
                val stepX = size.width / (data.size - 1)
                fun usedPct(d: DeviceTelemetry) = if (d.ramTotalBytes > 0) (1f - d.ramFreeBytes.toFloat() / d.ramTotalBytes) else 0f
                val path = Path()
                data.forEachIndexed { i, d ->
                    val px = i * stepX; val py = size.height - usedPct(d).coerceIn(0f, 1f) * size.height
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color, style = Stroke(width = 3f))
            }
        }
    }
}

/* ---------- growers + drill-down ---------- */

@Composable
private fun GrowerRow(g: AppGrowth, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(g.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (g.versionChanged) Tag("updated", MaterialTheme.colorScheme.tertiary)
        }
        val sign = if (g.deltaBytes >= 0) "+" else "-"
        Text("$sign${human(abs(g.deltaBytes))}", style = MonoStyle, color = if (g.deltaBytes >= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AppHistorySheet(hist: AppHistory) {
    val rows = hist.rows
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(hist.label, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(hist.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (rows.size < 2) {
            Text(stringResource(R.string.tm_app_need_more), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val first = rows.first(); val last = rows.last()
            fun total(r: com.snatik.storage.core.apps.AppTelemetry) = r.appBytes + r.dataBytes + r.cacheBytes
            val delta = total(last) - total(first)
            Text(
                stringResource(if (delta >= 0) R.string.tm_app_grew else R.string.tm_app_shrank, human(abs(delta)), humanDuration(last.ts - first.ts)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (delta >= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            // total-over-time line
            val color = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val maxV = rows.maxOf { total(it) }.toFloat().coerceAtLeast(1f)
                val stepX = size.width / (rows.size - 1)
                val path = Path()
                rows.forEachIndexed { i, r ->
                    val px = i * stepX; val py = size.height - (total(r).toFloat() / maxV) * size.height
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color, style = Stroke(width = 4f))
                rows.forEachIndexed { i, r -> drawCircle(color, 4f, Offset(i * stepX, size.height - (total(r).toFloat() / maxV) * size.height)) }
            }
            // latest composition split
            Text(stringResource(R.string.tm_app_split), style = MaterialTheme.typography.titleSmall)
            SplitBar(
                listOf(
                    Triple(stringResource(R.string.tm_app_apk), last.appBytes, MaterialTheme.colorScheme.primary),
                    Triple(stringResource(R.string.tm_app_data), last.dataBytes, MaterialTheme.colorScheme.tertiary),
                    Triple(stringResource(R.string.tm_app_cache), last.cacheBytes, MaterialTheme.colorScheme.secondary),
                )
            )
        }
    }
}

@Composable
private fun SplitBar(parts: List<Triple<String, Long, Color>>) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(5.dp))) {
            parts.forEach { (_, v, c) -> if (v > 0) Box(modifier = Modifier.weight((v.toDouble() / total).toFloat().coerceAtLeast(0.003f)).fillMaxHeight().background(c)) }
        }
        parts.forEach { (label, v, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(human(v), style = MonoStyle)
            }
        }
    }
}

/* ---------- helpers ---------- */

private val DATE_FMT = SimpleDateFormat("MMM d", Locale.getDefault())
private fun dateFmt(ts: Long): String = DATE_FMT.format(Date(ts))

private fun humanDuration(ms: Long): String {
    val days = ms / (24.0 * 3600 * 1000)
    return when {
        days >= 1 -> "${days.roundToInt()}d"
        else -> "${(ms / (3600.0 * 1000)).roundToInt()}h"
    }
}

private fun human(bytes: Long): String {
    if (abs(bytes) < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (abs(v) >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return "${(v * 10).roundToInt() / 10.0} ${units[i]}"
}
