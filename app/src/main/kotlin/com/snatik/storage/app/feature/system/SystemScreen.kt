package com.snatik.storage.app.feature.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.AppMem
import com.snatik.storage.core.apps.CpuInfo
import com.snatik.storage.core.apps.CpuMemSample
import com.snatik.storage.core.apps.DeviceInfo
import com.snatik.storage.core.apps.MemInfo
import com.snatik.storage.core.apps.Prop
import com.snatik.storage.core.apps.SystemInspector
import com.snatik.storage.core.apps.SystemReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

class SystemViewModel(private val inspector: SystemInspector) : ViewModel() {
    private val _report = MutableStateFlow<SystemReport?>(null)
    val report: StateFlow<SystemReport?> = _report.asStateFlow()
    private val _props = MutableStateFlow<List<Prop>?>(null)
    val props: StateFlow<List<Prop>?> = _props.asStateFlow()
    private val _live = MutableStateFlow(true)
    val live: StateFlow<Boolean> = _live.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // Live graphs: rolling history of CPU busy % and RAM used %, fed by a fast lightweight sampler.
    private val _sample = MutableStateFlow<CpuMemSample?>(null)
    val sample: StateFlow<CpuMemSample?> = _sample.asStateFlow()
    private val _cpuHistory = MutableStateFlow<List<Float>>(emptyList())
    val cpuHistory: StateFlow<List<Float>> = _cpuHistory.asStateFlow()
    private val _memHistory = MutableStateFlow<List<Float>>(emptyList())
    val memHistory: StateFlow<List<Float>> = _memHistory.asStateFlow()

    init {
        // Full report once (with progress), then refreshed on a slower cadence for the detailed tabs.
        viewModelScope.launch { _loading.value = true; _report.value = inspector.report(); _loading.value = false }
        viewModelScope.launch { while (isActive) { delay(3000); if (_live.value) _report.value = inspector.report() } }
        // Fast sampler that ticks the Overview graphs while the screen is open.
        viewModelScope.launch {
            while (isActive) {
                if (_live.value) {
                    val s = runCatching { inspector.sample() }.getOrNull()
                    if (s != null) {
                        _sample.value = s
                        _cpuHistory.update { (it + (s.cpuPercent?.toFloat() ?: it.lastOrNull() ?: 0f)).takeLast(HISTORY) }
                        _memHistory.update { (it + s.memUsedPercent.toFloat()).takeLast(HISTORY) }
                    }
                }
                delay(1500)
            }
        }
    }

    fun refresh() { viewModelScope.launch { _loading.value = true; _report.value = inspector.report(); _loading.value = false } }
    fun setLive(v: Boolean) { _live.value = v }
    fun loadProps() { if (_props.value == null) viewModelScope.launch { _props.value = inspector.properties() } }

    private companion object { const val HISTORY = 60 }
}

private enum class SysTab { OVERVIEW, CPU, MEMORY, STORAGE, PROPS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(onBack: () -> Unit, viewModel: SystemViewModel = koinViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(SysTab.OVERVIEW) }

    LaunchedEffect(tab) { if (tab == SysTab.PROPS) viewModel.loadProps() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    LivePill(live) { viewModel.setLive(!live) }
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh)) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading && report != null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip(stringResource(R.string.sys_tab_overview), tab == SysTab.OVERVIEW) { tab = SysTab.OVERVIEW }
                TabChip(stringResource(R.string.sys_tab_cpu), tab == SysTab.CPU) { tab = SysTab.CPU }
                TabChip(stringResource(R.string.sys_tab_memory), tab == SysTab.MEMORY) { tab = SysTab.MEMORY }
                TabChip(stringResource(R.string.sys_tab_storage), tab == SysTab.STORAGE) { tab = SysTab.STORAGE }
                TabChip(stringResource(R.string.sys_tab_props), tab == SysTab.PROPS) { tab = SysTab.PROPS }
            }
            HorizontalDivider()
            val r = report
            if (r == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else when (tab) {
                SysTab.OVERVIEW -> OverviewTab(r, viewModel)
                SysTab.CPU -> CpuTab(r.cpu)
                SysTab.MEMORY -> MemoryTab(r)
                SysTab.STORAGE -> StorageTab(r)
                SysTab.PROPS -> PropsTab(viewModel)
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) = FilterChip(selected, onClick, label = { Text(label) })

@Composable
private fun LivePill(live: Boolean, onToggle: () -> Unit) {
    val color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.padding(end = 4.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Text(stringResource(if (live) R.string.sys_live else R.string.sys_paused), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** A live line+area chart of a 0–100 history that ticks as new samples arrive. */
@Composable
private fun LiveGraph(title: String, current: String, history: List<Float>, color: Color) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(current, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            }
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(modifier = Modifier.fillMaxWidth().height(72.dp).padding(top = 8.dp)) {
                // baseline
                drawLine(track, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 2f)
                if (history.size < 2) return@Canvas
                val stepX = size.width / (history.size - 1)
                fun y(v: Float) = size.height - (v.coerceIn(0f, 100f) / 100f) * size.height
                val line = Path()
                val fill = Path().apply { moveTo(0f, size.height) }
                history.forEachIndexed { i, v ->
                    val x = i * stepX
                    if (i == 0) line.moveTo(x, y(v)) else line.lineTo(x, y(v))
                    fill.lineTo(x, y(v))
                }
                fill.lineTo((history.size - 1) * stepX, size.height); fill.close()
                drawPath(fill, color.copy(alpha = 0.15f))
                drawPath(line, color, style = Stroke(width = 3f))
            }
        }
    }
}

/* ---------- Overview ---------- */

@Composable
private fun OverviewTab(r: SystemReport, viewModel: SystemViewModel) {
    val d = r.device
    val sample by viewModel.sample.collectAsStateWithLifecycle()
    val cpuHistory by viewModel.cpuHistory.collectAsStateWithLifecycle()
    val memHistory by viewModel.memHistory.collectAsStateWithLifecycle()
    val cpuPct = sample?.cpuPercent ?: r.cpu.usagePercent
    val memPct = sample?.memUsedPercent ?: (r.mem.totalKb - r.mem.availableKb).let { if (r.mem.totalKb > 0) it.toDouble() / r.mem.totalKb * 100 else 0.0 }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(pct(cpuPct), stringResource(R.string.sys_cpu_usage), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                StatTile(pct(memPct), stringResource(R.string.sys_mem_used), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                StatTile(uptime(d.uptimeSec), stringResource(R.string.sys_uptime), Modifier.weight(1f))
            }
        }
        item { LiveGraph(stringResource(R.string.sys_cpu_usage), pct(cpuPct), cpuHistory, MaterialTheme.colorScheme.primary) }
        item { LiveGraph(stringResource(R.string.sys_mem_used), pct(memPct), memHistory, MaterialTheme.colorScheme.tertiary) }
        item {
            SectionCard(stringResource(R.string.sys_device)) {
                KeyVal(stringResource(R.string.sys_model), "${d.manufacturer} ${d.model}")
                KeyVal(stringResource(R.string.sys_android), d.androidVersion)
                KeyVal(stringResource(R.string.sys_patch), d.securityPatch)
                KeyVal(stringResource(R.string.sys_selinux), d.selinux)
                KeyVal(stringResource(R.string.sys_board), d.board)
                KeyVal(stringResource(R.string.sys_bootloader), d.bootloader)
                SelectionContainer { KeyVal(stringResource(R.string.sys_fingerprint), d.fingerprint, wrap = true) }
                SelectionContainer { KeyVal(stringResource(R.string.sys_kernel), d.kernel, wrap = true) }
            }
        }
        item {
            SectionCard(stringResource(R.string.sys_load)) {
                KeyVal(stringResource(R.string.sys_loadavg), "${fmt2(d.load1)}  ${fmt2(d.load5)}  ${fmt2(d.load15)}")
                KeyVal(stringResource(R.string.sys_procs), "${d.procsRunning} / ${d.procsTotal}")
                KeyVal(stringResource(R.string.sys_uptime), uptimeLong(d.uptimeSec))
            }
        }
    }
}

/* ---------- CPU ---------- */

@Composable
private fun CpuTab(cpu: CpuInfo) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard(stringResource(R.string.sys_processor)) {
                if (cpu.model.isNotEmpty()) KeyVal(stringResource(R.string.sys_model), cpu.model)
                if (cpu.hardware.isNotEmpty()) KeyVal(stringResource(R.string.sys_hardware), cpu.hardware)
                KeyVal(stringResource(R.string.sys_cores), cpu.cores.toString())
                KeyVal(stringResource(R.string.sys_abi), cpu.abi)
                cpu.usagePercent?.let { KeyVal(stringResource(R.string.sys_cpu_usage), pct(it)) }
            }
        }
        item { SectionHeader(stringResource(R.string.sys_cores_title)) }
        items(cpu.perCore, key = { it.index }) { c ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("cpu${c.index}", style = MonoStyle, modifier = Modifier.width(56.dp), color = if (c.online) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(if (c.online) mhz(c.curKhz) else stringResource(R.string.sys_offline), style = MaterialTheme.typography.bodyMedium, color = if (c.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Text(c.governor, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val frac = if (c.maxKhz > 0 && c.online) (c.curKhz.toFloat() / c.maxKhz).coerceIn(0f, 1f) else 0f
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 3.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(2.dp))) {
                        Box(modifier = Modifier.fillMaxWidth(frac).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                    }
                    Text("${mhz(c.minKhz)} – ${mhz(c.maxKhz)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (cpu.temps.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sys_temps)) }
            items(cpu.temps, key = { it.type }) { t ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t.type, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${(t.celsius * 10).roundToInt() / 10.0}°C", style = MonoStyle, color = tempColor(t.celsius))
                }
            }
        }
    }
}

/* ---------- Memory ---------- */

@Composable
private fun MemoryTab(r: SystemReport) {
    val m = r.mem
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard(stringResource(R.string.sys_ram)) {
                val used = m.totalKb - m.availableKb
                KeyVal(stringResource(R.string.sys_total), humanKb(m.totalKb))
                KeyVal(stringResource(R.string.sys_used), "${humanKb(used)}  (${pctOf(used, m.totalKb)})")
                KeyVal(stringResource(R.string.sys_available), humanKb(m.availableKb))
                KeyVal(stringResource(R.string.sys_cached), humanKb(m.cachedKb))
                // used (primary) | cached (secondary) | free (track)
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))) {
                    Row(Modifier.fillMaxWidth()) {
                        val realUsed = (used - m.cachedKb).coerceAtLeast(0)
                        Box(modifier = Modifier.fillMaxWidth(fracOf(realUsed, m.totalKb)).height(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
                        Box(modifier = Modifier.fillMaxWidth(fracOf(m.cachedKb, m.totalKb - realUsed)).height(8.dp).background(MaterialTheme.colorScheme.tertiary))
                    }
                }
            }
        }
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
        if (r.swaps.isNotEmpty()) item {
            SectionCard(stringResource(R.string.system_swaps)) {
                r.swaps.forEach { s -> KeyVal(s.name.substringAfterLast('/'), "${human(s.usedKb * 1024)} / ${human(s.sizeKb * 1024)}") }
            }
        }
        r.appMem?.let { a ->
            item {
                SectionCard(stringResource(R.string.sys_app_mem)) {
                    KeyVal(stringResource(R.string.sys_total_pss), humanKb(a.totalPssKb))
                    KeyVal(stringResource(R.string.sys_java_heap), humanKb(a.javaHeapKb))
                    KeyVal(stringResource(R.string.sys_native_heap), humanKb(a.nativeHeapKb))
                    KeyVal(stringResource(R.string.sys_code), humanKb(a.codeKb))
                    KeyVal(stringResource(R.string.sys_graphics), humanKb(a.graphicsKb))
                    KeyVal(stringResource(R.string.sys_stack), humanKb(a.stackKb))
                    KeyVal(stringResource(R.string.sys_other), humanKb(a.otherKb))
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.sys_meminfo)) {
                m.fields.forEach { (k, kb) -> KeyVal(k, humanKb(kb)) }
            }
        }
    }
}

/* ---------- Storage ---------- */

@Composable
private fun StorageTab(r: SystemReport) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (r.blocks.isNotEmpty()) item {
            SectionCard(stringResource(R.string.sys_block_devices)) {
                r.blocks.forEach { b ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(b.name, style = MonoStyle, modifier = Modifier.weight(1f))
                            if (!b.rotational) MiniBadge("SSD", MaterialTheme.colorScheme.primary) else MiniBadge("HDD", MaterialTheme.colorScheme.onSurfaceVariant)
                            if (b.readOnly) MiniBadge("RO", MaterialTheme.colorScheme.error)
                            if (b.removable) MiniBadge(stringResource(R.string.sys_removable), MaterialTheme.colorScheme.tertiary)
                            Text(human(b.sizeBytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        val meta = listOfNotNull(b.model.ifBlank { null }, b.scheduler.ifBlank { null }?.let { "sched: $it" }).joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (r.diskstats.isNotEmpty()) item {
            SectionCard(stringResource(R.string.sys_disk_io)) {
                r.diskstats.take(16).forEach { s -> KeyVal(s.name, "↓${human(s.readBytes)}  ↑${human(s.writeBytes)}") }
            }
        }
        val usable = r.mounts.filter { it.totalBytes > 0 }
        if (usable.isNotEmpty()) item {
            SectionCard(stringResource(R.string.sys_filesystems)) {
                usable.sortedByDescending { it.totalBytes }.forEach { mnt ->
                    val used = mnt.totalBytes - mnt.freeBytes
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(mnt.mountPoint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(mnt.type, style = MonoStyle, color = MaterialTheme.colorScheme.primary)
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 3.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(2.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(fracOf(used, mnt.totalBytes)).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                        }
                        Text("${human(used)} / ${human(mnt.totalBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
                        Text(mnt.device, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                }
            }
        }
    }
}

/* ---------- Properties ---------- */

@Composable
private fun PropsTab(viewModel: SystemViewModel) {
    val props by viewModel.props.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(12.dp), singleLine = true, placeholder = { Text(stringResource(R.string.sys_props_search)) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) })
        val list = props
        if (list == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val q = query.trim().lowercase()
            val shown = if (q.isBlank()) list else list.filter { it.key.lowercase().contains(q) || it.value.lowercase().contains(q) }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(shown, key = { it.key }) { p ->
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(p.key, style = MonoStyle.copy(color = MaterialTheme.colorScheme.primary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.value.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

/* ---------- shared ---------- */

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
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
private fun KeyVal(key: String, value: String, wrap: Boolean = false) {
    if (wrap) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MonoStyle)
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MonoStyle, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
private fun tempColor(c: Double): Color = when {
    c >= 80 -> MaterialTheme.colorScheme.error
    c >= 60 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurface
}

/* ---------- format ---------- */

private fun pct(v: Double?): String = if (v == null) "—" else "${v.roundToInt()}%"
private fun pctOf(part: Long, total: Long): String = if (total <= 0) "—" else "${(part.toDouble() / total * 100).roundToInt()}%"
private fun fracOf(part: Long, total: Long): Float = if (total <= 0) 0f else (part.toFloat() / total).coerceIn(0f, 1f)
private fun fmt2(v: Double): String = ((v * 100).roundToInt() / 100.0).toString()
private fun mhz(khz: Long): String = if (khz <= 0) "—" else "${khz / 1000} MHz"

private fun uptime(sec: Long): String {
    val d = sec / 86400; val h = (sec % 86400) / 3600; val m = (sec % 3600) / 60
    return when { d > 0 -> "${d}d ${h}h"; h > 0 -> "${h}h ${m}m"; else -> "${m}m" }
}
private fun uptimeLong(sec: Long): String {
    val d = sec / 86400; val h = (sec % 86400) / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return buildString { if (d > 0) append("${d}d "); if (d > 0 || h > 0) append("${h}h "); append("${m}m ${s}s") }
}

private fun human(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024; var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return "${(v * 10).roundToInt() / 10.0} ${units[i]}"
}
private fun humanKb(kb: Long): String = human(kb * 1024)
