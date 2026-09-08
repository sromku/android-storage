package com.snatik.storage.app.feature.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.SystemInspector
import com.snatik.storage.core.apps.SystemReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

class SystemViewModel(private val inspector: SystemInspector) : ViewModel() {
    private val _report = MutableStateFlow<SystemReport?>(null)
    val report: StateFlow<SystemReport?> = _report.asStateFlow()
    init { viewModelScope.launch { _report.value = inspector.report() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(onBack: () -> Unit, viewModel: SystemViewModel = koinViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val r = report
            if (r == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    r.zram?.let { z ->
                        item {
                            SectionCard(stringResource(R.string.system_zram)) {
                                KeyVal(stringResource(R.string.system_zram_disksize), human(z.disksizeBytes))
                                KeyVal(stringResource(R.string.system_zram_original), human(z.originalBytes))
                                KeyVal(stringResource(R.string.system_zram_compressed), human(z.compressedBytes))
                                KeyVal(stringResource(R.string.system_zram_mem), human(z.memUsedBytes))
                                if (z.ratio > 0) KeyVal(stringResource(R.string.system_zram_ratio), "${(z.ratio * 100).roundToInt() / 100.0}x")
                            }
                        }
                    }
                    r.smaps?.let { m ->
                        item {
                            SectionCard(stringResource(R.string.system_smaps)) {
                                KeyVal("RSS", "${m.rssKb} kB")
                                KeyVal("PSS", "${m.pssKb} kB")
                                KeyVal(stringResource(R.string.system_private_dirty), "${m.privateDirtyKb} kB")
                                KeyVal("Swap", "${m.swapKb} kB")
                                KeyVal(stringResource(R.string.system_regions), m.regions.toString())
                            }
                        }
                    }
                    if (r.swaps.isNotEmpty()) item {
                        SectionCard(stringResource(R.string.system_swaps)) {
                            r.swaps.forEach { s ->
                                KeyVal(s.name.substringAfterLast('/'), "${human(s.usedKb * 1024)} / ${human(s.sizeKb * 1024)}")
                            }
                        }
                    }
                    if (r.partitions.isNotEmpty()) item {
                        SectionCard(stringResource(R.string.system_partitions)) {
                            r.partitions.take(24).forEach { p -> KeyVal(p.name, human(p.bytes)) }
                        }
                    }
                    item {
                        SectionCard(stringResource(R.string.system_mounts, r.mounts.size)) {
                            r.mounts.forEach { mnt ->
                                Column(modifier = Modifier.padding(vertical = 3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(mnt.mountPoint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(mnt.type, style = MonoStyle, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(mnt.device, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            content()
        }
    }
}

@Composable
private fun KeyVal(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MonoStyle)
    }
}

private fun human(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return "${(v * 10).roundToInt() / 10.0} ${units[i]}"
}
