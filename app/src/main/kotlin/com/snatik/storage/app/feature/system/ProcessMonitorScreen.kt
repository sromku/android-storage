package com.snatik.storage.app.feature.system

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.core.apps.MemCategory
import com.snatik.storage.core.apps.ProcessInspector
import com.snatik.storage.core.apps.ProcessMem
import com.snatik.storage.core.apps.ProcessSample
import com.snatik.storage.core.apps.ProcessStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

enum class ProcMetric { CPU, MEM }

/** A process plus the resolved app it belongs to (label + package + whether it's an installed app). */
data class ProcRow(val sample: ProcessSample, val pkg: String?, val label: String, val isApp: Boolean)

/** Detail loaded lazily when a process is tapped. */
sealed interface DetailState {
    data object Loading : DetailState
    data object Gone : DetailState
    data class Loaded(val status: ProcessStatus?, val mem: ProcessMem?) : DetailState
}

class ProcessMonitorViewModel(
    focus: String,
    private val processes: ProcessInspector,
    private val system: com.snatik.storage.core.apps.SystemInspector,
    context: Context,
) : ViewModel() {
    private val pm: PackageManager = context.packageManager
    val cores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    private val _sort = MutableStateFlow(if (focus == "mem") ProcMetric.MEM else ProcMetric.CPU)
    val sort: StateFlow<ProcMetric> = _sort.asStateFlow()
    private val _live = MutableStateFlow(true)
    val live: StateFlow<Boolean> = _live.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _rows = MutableStateFlow<List<ProcRow>>(emptyList())
    val rows: StateFlow<List<ProcRow>> = _rows.asStateFlow()

    private val _sample = MutableStateFlow<com.snatik.storage.core.apps.CpuMemSample?>(null)
    val sample: StateFlow<com.snatik.storage.core.apps.CpuMemSample?> = _sample.asStateFlow()
    private val _cpuHistory = MutableStateFlow<List<Float>>(emptyList())
    val cpuHistory: StateFlow<List<Float>> = _cpuHistory.asStateFlow()
    private val _memHistory = MutableStateFlow<List<Float>>(emptyList())
    val memHistory: StateFlow<List<Float>> = _memHistory.asStateFlow()

    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail: StateFlow<DetailState?> = _detail.asStateFlow()
    private val _selected = MutableStateFlow<ProcRow?>(null)
    val selected: StateFlow<ProcRow?> = _selected.asStateFlow()

    // Resolved app label/package per process name, so we don't hit PackageManager every refresh.
    private val metaCache = HashMap<String, Triple<String?, String, Boolean>>()

    init {
        viewModelScope.launch {
            while (isActive) {
                if (_live.value) {
                    val list = runCatching { processes.processes() }.getOrDefault(emptyList())
                    if (list.isNotEmpty()) _rows.value = list.map { toRow(it) }
                    _loading.value = false
                }
                delay(2500)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                if (_live.value) {
                    runCatching { system.sample() }.getOrNull()?.let { s ->
                        _sample.value = s
                        _cpuHistory.update { (it + (s.cpuPercent?.toFloat() ?: it.lastOrNull() ?: 0f)).takeLast(HISTORY) }
                        _memHistory.update { (it + s.memUsedPercent.toFloat()).takeLast(HISTORY) }
                    }
                }
                delay(1500)
            }
        }
    }

    private fun toRow(s: ProcessSample): ProcRow {
        val meta = metaCache.getOrPut(s.name) {
            val pkg = s.name.substringBefore(':')
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            if (ai != null) {
                val base = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg)
                val suffix = s.name.substringAfter(':', "")
                Triple(pkg, if (suffix.isEmpty()) base else "$base:$suffix", true)
            } else {
                Triple(null, s.name.substringAfterLast('/'), false)
            }
        }
        return ProcRow(s, meta.first, meta.second, meta.third)
    }

    fun setSort(m: ProcMetric) { _sort.value = m }
    fun setLive(v: Boolean) { _live.value = v }

    fun select(row: ProcRow) {
        _selected.value = row
        _detail.value = DetailState.Loading
        viewModelScope.launch {
            val status = runCatching { processes.status(row.sample.pid) }.getOrNull()
            val mem = runCatching { processes.memDetail(row.sample.pid) }.getOrNull()
            _detail.value = if (status == null && mem == null) DetailState.Gone else DetailState.Loaded(status, mem)
        }
    }

    fun clearDetail() { _detail.value = null; _selected.value = null }

    fun forceStop(row: ProcRow, onResult: (Boolean) -> Unit) {
        val pkg = row.pkg ?: return onResult(false)
        viewModelScope.launch {
            val ok = processes.forceStop(pkg)
            if (ok) metaCache.remove(row.sample.name)
            onResult(ok)
        }
    }

    private companion object { const val HISTORY = 60 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessMonitorScreen(
    focus: String,
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
    viewModel: ProcessMonitorViewModel = koinViewModel { parametersOf(focus) },
) {
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val sample by viewModel.sample.collectAsStateWithLifecycle()
    val cpuHistory by viewModel.cpuHistory.collectAsStateWithLifecycle()
    val memHistory by viewModel.memHistory.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val cpuColor = MaterialTheme.colorScheme.primary
    val memColor = MaterialTheme.colorScheme.tertiary
    val accent = if (sort == ProcMetric.CPU) cpuColor else memColor
    val memTotalKb = sample?.memTotalKb ?: 0L

    val filtered = remember(rows, sort, query) {
        val q = query.trim().lowercase()
        rows.asSequence()
            .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.sample.name.lowercase().contains(q) }
            .sortedByDescending { if (sort == ProcMetric.CPU) it.sample.cpuPercent else it.sample.rssKb.toDouble() }
            .toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (sort == ProcMetric.CPU) R.string.proc_cpu_title else R.string.proc_mem_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    LivePillP(live) { viewModel.setLive(!live) }
                    IconButton(onClick = { viewModel.setLive(true) }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh)) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    if (sort == ProcMetric.CPU) {
                        LiveGraphP(stringResource(R.string.proc_cpu_title), pctP(sample?.cpuPercent), cpuHistory, cpuColor)
                    } else {
                        LiveGraphP(stringResource(R.string.proc_mem_title), pctP(sample?.memUsedPercent), memHistory, memColor)
                    }
                }
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = sort == ProcMetric.CPU,
                            onClick = { viewModel.setSort(ProcMetric.CPU) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text(stringResource(R.string.proc_sort_cpu)) }
                        SegmentedButton(
                            selected = sort == ProcMetric.MEM,
                            onClick = { viewModel.setSort(ProcMetric.MEM) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text(stringResource(R.string.proc_sort_mem)) }
                    }
                }
                item {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.proc_search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
                item {
                    Text(
                        stringResource(R.string.proc_processes, filtered.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
                items(filtered, key = { it.sample.pid }) { row ->
                    ProcRowItem(row, sort, accent, viewModel.cores, memTotalKb) { viewModel.select(row) }
                }
            }
        }
    }

    val d = detail
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    if (d != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Re-read the selected process from the live list so its CPU metric stays fresh in the sheet.
        val liveRow = selected?.let { sel -> rows.firstOrNull { it.sample.pid == sel.sample.pid } } ?: selected
        ModalBottomSheet(onDismissRequest = { viewModel.clearDetail() }, sheetState = sheetState) {
            ProcDetailSheet(
                state = d,
                row = liveRow,
                cores = viewModel.cores,
                memTotalKb = memTotalKb,
                onOpenApp = onOpenApp,
                onForceStop = { r, cb -> viewModel.forceStop(r, cb) },
                onDismiss = { viewModel.clearDetail() },
            )
        }
    }
}

/* ---------- row ---------- */

@Composable
private fun ProcRowItem(row: ProcRow, sort: ProcMetric, accent: Color, cores: Int, memTotalKb: Long, onClick: () -> Unit) {
    val fraction: Float
    val value: String
    if (sort == ProcMetric.CPU) {
        fraction = (row.sample.cpuPercent / (100.0 * cores)).toFloat().coerceIn(0f, 1f)
        value = pctP(row.sample.cpuPercent / cores)
    } else {
        fraction = if (memTotalKb > 0) (row.sample.rssKb.toDouble() / memTotalKb).toFloat().coerceIn(0f, 1f) else 0f
        value = humanKbP(row.sample.rssKb)
    }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProcAvatar(row, 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${row.sample.name}  ·  ${stringResource(R.string.proc_pid, row.sample.pid)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
            }
            Spacer(Modifier.height(8.dp))
            MeterBar(fraction, accent)
        }
    }
}

@Composable
private fun MeterBar(fraction: Float, color: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(fraction).height(6.dp)
                .clip(RoundedCornerShape(50)).background(color),
        )
    }
}

@Composable
private fun ProcAvatar(row: ProcRow, size: androidx.compose.ui.unit.Dp) {
    if (row.isApp && row.pkg != null) {
        AppIcon(packageName = row.pkg, size = size)
    } else {
        Box(
            modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/* ---------- detail sheet ---------- */

@Composable
private fun ProcDetailSheet(
    state: DetailState,
    row: ProcRow?,
    cores: Int,
    memTotalKb: Long,
    onOpenApp: (String) -> Unit,
    onForceStop: (ProcRow, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var confirmStop by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (row != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProcAvatar(row, 52.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${row.sample.name}  ·  ${stringResource(R.string.proc_pid, row.sample.pid)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        when (state) {
            DetailState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.proc_loading_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DetailState.Gone -> Text(stringResource(R.string.proc_gone), color = MaterialTheme.colorScheme.error)
            is DetailState.Loaded -> {
                // Quick metric tiles
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row?.let { StatTileP(pctP(it.sample.cpuPercent / cores), stringResource(R.string.sys_cpu_usage), Modifier.weight(1f), MaterialTheme.colorScheme.primary) }
                    state.mem?.let { StatTileP(humanKbP(it.totalPssKb), stringResource(R.string.proc_total_pss), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary) }
                    state.status?.let { StatTileP(it.threads.toString(), stringResource(R.string.proc_threads), Modifier.weight(1f)) }
                }

                state.mem?.let { mem ->
                    MemComposition(mem, memTotalKb)
                }

                state.status?.let { st ->
                    SectionCardP(stringResource(R.string.sys_device)) {
                        KeyValP(stringResource(R.string.proc_state), st.state)
                        KeyValP(stringResource(R.string.proc_uid), st.uid.toString())
                        KeyValP(stringResource(R.string.proc_ppid), st.ppid.toString())
                        KeyValP(stringResource(R.string.proc_oom), st.oomScoreAdj?.toString() ?: "—")
                        KeyValP(stringResource(R.string.proc_vmrss), humanKbP(st.vmRssKb))
                        KeyValP(stringResource(R.string.proc_vmpeak), humanKbP(st.vmPeakKb))
                        if (st.cmdline.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.proc_cmdline), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SelectionContainer {
                                Text(st.cmdline, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row?.isApp == true && row.pkg != null) {
                        OutlinedButton(onClick = { onOpenApp(row.pkg) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.proc_open_app))
                        }
                    }
                    state.status?.let { st ->
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(st.cmdline.ifBlank { row?.sample?.name ?: "" })) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.proc_copy))
                        }
                    }
                }
                if (row?.isApp == true && row.pkg != null) {
                    OutlinedButton(
                        onClick = { confirmStop = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.proc_force_stop), color = MaterialTheme.colorScheme.error)
                    }
                }
                toast?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    if (confirmStop && row?.pkg != null) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.proc_force_stop_title)) },
            text = { Text(stringResource(R.string.proc_force_stop_msg, row.label)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    onForceStop(row) { ok ->
                        toast = null
                        if (ok) onDismiss()
                    }
                }) { Text(stringResource(R.string.proc_force_stop), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MemComposition(mem: ProcessMem, memTotalKb: Long) {
    val cats = mem.categories.filter { it.pssKb > 0 }.sortedByDescending { it.pssKb }
    val total = cats.sumOf { it.pssKb }.coerceAtLeast(1)
    val palette = memPalette()
    SectionCardP(stringResource(R.string.proc_mem_composition)) {
        // Stacked bar
        Row(modifier = Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(6.dp))) {
            cats.forEachIndexed { i, c ->
                Box(modifier = Modifier.weight((c.pssKb.toDouble() / total).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(palette[i % palette.size]))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row {
            Text(stringResource(R.string.proc_total_pss), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(humanKbP(mem.totalPssKb), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Row {
            Text(stringResource(R.string.proc_total_rss), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(humanKbP(mem.totalRssKb), style = MaterialTheme.typography.labelMedium)
        }
        if (mem.totalSwapPssKb > 0) Row {
            Text(stringResource(R.string.proc_swap), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(humanKbP(mem.totalSwapPssKb), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))
        cats.forEachIndexed { i, c ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(palette[i % palette.size]))
                Spacer(Modifier.width(8.dp))
                Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(humanKbP(c.pssKb), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun memPalette(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.error,
    MaterialTheme.colorScheme.primaryContainer,
    MaterialTheme.colorScheme.tertiaryContainer,
    MaterialTheme.colorScheme.secondaryContainer,
    MaterialTheme.colorScheme.outline,
)

/* ---------- shared bits (local to this screen) ---------- */

@Composable
private fun LivePillP(live: Boolean, onToggle: () -> Unit) {
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

@Composable
private fun LiveGraphP(title: String, current: String, history: List<Float>, color: Color) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(current, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            }
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(modifier = Modifier.fillMaxWidth().height(96.dp).padding(top = 8.dp)) {
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

@Composable
private fun StatTileP(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionCardP(title: String, content: @Composable () -> Unit) {
    // Flat section (no card): its background would match the sheet anyway, and a card's inset would
    // push this content further in than the header/tiles above it. Align flush at the sheet padding.
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun KeyValP(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

private fun pctP(v: Double?): String = if (v == null) "—" else "${v.coerceIn(0.0, 100.0).let { (it * 10).toInt() / 10.0 }}%"

private fun humanKbP(kb: Long): String {
    if (kb <= 0) return "0"
    val mb = kb / 1024.0
    return when {
        mb >= 1024 -> "${(mb / 1024.0).let { (it * 100).toInt() / 100.0 }} GB"
        mb >= 1 -> "${mb.toInt()} MB"
        else -> "$kb KB"
    }
}
