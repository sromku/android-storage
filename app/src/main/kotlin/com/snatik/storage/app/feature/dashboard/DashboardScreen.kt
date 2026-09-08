package com.snatik.storage.app.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.apps.DeviceStats
import com.snatik.storage.core.apps.DeviceStatsRepository
import com.snatik.storage.core.apps.Wakelock
import com.snatik.storage.core.fs.Volume
import com.snatik.storage.core.fs.VolumeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class DashboardState(val stats: DeviceStats? = null, val volumes: List<Volume> = emptyList(), val loading: Boolean = true)

class DashboardViewModel(private val statsRepo: DeviceStatsRepository, private val volumes: VolumeRepository) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            val vols = volumes.volumes()
            _state.update { it.copy(volumes = vols) }
            _state.update { it.copy(stats = statsRepo.stats(), loading = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onBack: () -> Unit, viewModel: DashboardViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val s = state.stats
            if (s == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(2) }) { Header(s) }
                    item { Stat(stringResource(R.string.dash_ram), "${(s.totalRamBytes - s.availRamBytes).readableSize()} / ${s.totalRamBytes.readableSize()}", ((s.totalRamBytes - s.availRamBytes).toFloat() / s.totalRamBytes)) }
                    item { Stat(stringResource(R.string.dash_battery), "${s.batteryPercent}% · ${s.batteryStatus}", s.batteryPercent / 100f, if (s.batteryTempC > 0) "%.1f°C".format(s.batteryTempC) else null) }
                    items(state.volumes.filter { it.totalBytes > 0 }) { v -> Stat(volumeLabel(v), "${v.usedBytes.readableSize()} / ${v.totalBytes.readableSize()}", v.usedFraction) }
                    if (s.zramTotalBytes > 0) item { Stat(stringResource(R.string.dash_zram), "${s.zramUsedBytes.readableSize()} / ${s.zramTotalBytes.readableSize()}", (s.zramUsedBytes.toFloat() / s.zramTotalBytes)) }
                    item { Info(stringResource(R.string.dash_selinux), s.selinux) }
                    item { Info(stringResource(R.string.dash_patch), s.securityPatch) }
                    item { Info(stringResource(R.string.dash_uptime), formatUptime(s.uptimeMs)) }
                    item { Info(stringResource(R.string.dash_kernel), s.kernel) }
                    if (s.wakelocks.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) { Text(stringResource(R.string.dash_wakelocks), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
                        items(s.wakelocks, span = { GridItemSpan(2) }) { WakelockRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(s: DeviceStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(s.model, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Android ${s.androidRelease} · API ${s.sdk} · ${s.buildId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, fraction: Float, extra: String? = null) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant, gapSize = 0.dp, drawStopIndicator = {})
            extra?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WakelockRow(wl: Wakelock) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(wl.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
        Text("${wl.heldMs / 1000}s · ${wl.count}×", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun volumeLabel(v: Volume): String = v.systemLabel.ifEmpty {
    when (v.kind) {
        com.snatik.storage.core.fs.VolumeKind.SHARED -> "Shared storage"
        com.snatik.storage.core.fs.VolumeKind.SD_CARD -> "SD card"
        com.snatik.storage.core.fs.VolumeKind.USB -> "USB"
        com.snatik.storage.core.fs.VolumeKind.SYSTEM_ROOT -> "System"
        else -> v.path.substringAfterLast('/').ifEmpty { "Storage" }
    }
}

private fun formatUptime(ms: Long): String {
    val s = ms / 1000
    return "%dd %dh %dm".format(s / 86400, (s % 86400) / 3600, (s % 3600) / 60)
}
