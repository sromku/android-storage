package com.snatik.storage.app.feature.timemachine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.snatik.storage.core.apps.DeviceTelemetry
import com.snatik.storage.core.apps.TelemetryRepository
import com.snatik.storage.core.apps.TimeMachineReport
import androidx.compose.material.icons.filled.History
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

class TimeMachineViewModel(private val repo: TelemetryRepository) : ViewModel() {
    private val _report = MutableStateFlow<TimeMachineReport?>(null)
    val report: StateFlow<TimeMachineReport?> = _report.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    var scheduled by mutableStateOf(repo.enabled); private set

    init { refresh() }

    fun refresh() { viewModelScope.launch { _report.value = repo.report() } }

    fun captureNow() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { repo.capture() }
            _report.value = repo.report()
            _busy.value = false
        }
    }

    fun toggleScheduled(on: Boolean) { repo.setEnabled(on); scheduled = on }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeMachineScreen(onBack: () -> Unit, viewModel: TimeMachineViewModel = koinViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.tm_schedule), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.tm_schedule_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = viewModel.scheduled, onCheckedChange = viewModel::toggleScheduled)
                        }
                        Button(onClick = viewModel::captureNow, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) stringResource(R.string.tm_capturing) else stringResource(R.string.tm_capture_now))
                        }
                    }
                }
            }
            val r = report
            when {
                r == null -> {}
                r.snapshots < 2 -> item {
                    EmptyState(Icons.Default.History, stringResource(R.string.tm_need_more), stringResource(R.string.tm_need_more_body), modifier = Modifier.height(220.dp))
                }
                else -> {
                    item { FreeSpaceCard(r) }
                    r.forecast?.let { f ->
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.tm_forecast), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    val rate = f.bytesPerDay
                                    Text(
                                        if (rate > 0) stringResource(R.string.tm_losing, human(rate.roundToInt().toLong())) else stringResource(R.string.tm_gaining, human(abs(rate).roundToInt().toLong())),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    f.daysUntilFull?.let { d ->
                                        Text(stringResource(R.string.tm_full_in, d.roundToInt()), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (!f.confident) Text(stringResource(R.string.tm_low_confidence), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (r.cacheBytesPerDay != 0.0) Text(stringResource(R.string.tm_cache_velocity, human(r.cacheBytesPerDay.roundToInt().toLong())), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (r.topGrowers.isNotEmpty()) item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.tm_growers), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                r.topGrowers.forEach { GrowerRow(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeSpaceCard(r: TimeMachineReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tm_free_over_time, r.snapshots), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val line = MaterialTheme.colorScheme.primary
            val grid = MaterialTheme.colorScheme.outlineVariant
            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val data = r.device
                if (data.size < 2) return@Canvas
                val minY = data.minOf { it.freeBytes }.toFloat()
                val maxY = data.maxOf { it.freeBytes }.toFloat()
                val range = (maxY - minY).coerceAtLeast(1f)
                val pad = 8f
                // Even spacing by snapshot index so the line reads regardless of capture timing.
                fun px(i: Int) = pad + i.toFloat() / (data.size - 1).coerceAtLeast(1) * (size.width - 2 * pad)
                fun py(v: Long) = pad + (1f - (v.toFloat() - minY) / range) * (size.height - 2 * pad)
                // baseline grid
                drawLine(grid, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), strokeWidth = 1f)
                val path = Path().apply {
                    moveTo(px(0), py(data[0].freeBytes))
                    for (i in 1 until data.size) lineTo(px(i), py(data[i].freeBytes))
                }
                drawPath(path, line, style = Stroke(width = 4f))
                data.indices.forEach { i -> drawCircle(line, radius = 5f, center = Offset(px(i), py(data[i].freeBytes))) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.tm_free_now, human(r.device.last().freeBytes)), style = MonoStyle)
                Text(human(r.device.last().totalBytes), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GrowerRow(g: AppGrowth) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(g.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (g.versionChanged) Tag("updated", MaterialTheme.colorScheme.tertiary)
        }
        val sign = if (g.deltaBytes >= 0) "+" else "-"
        Text("$sign${human(abs(g.deltaBytes))}", style = MonoStyle, color = if (g.deltaBytes >= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
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
