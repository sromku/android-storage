package com.snatik.storage.app.feature.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.ExternalSink
import com.snatik.storage.core.apps.ProviderChange
import com.snatik.storage.core.apps.ProviderRecorderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

enum class PwSource { LIVE, RECORDED }
enum class PwView { OVERVIEW, TIMELINE, BY_PROVIDER, BY_OP }

class ProviderWatchViewModel(
    private val context: Context,
    private val watcher: ProviderWatcher,
    val store: ProviderRecorderStore,
    val sink: ExternalSink,
) : ViewModel() {

    private val _source = MutableStateFlow(PwSource.LIVE)
    val source: StateFlow<PwSource> = _source.asStateFlow()

    val watching: StateFlow<Set<String>> = watcher.watching
    val recording: StateFlow<Boolean> = store.running
    val recordedCount: StateFlow<Int> = store.count.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val oldest: StateFlow<Long?> = store.oldest.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val entries: StateFlow<List<ProviderChange>> =
        combine(_source, watcher.events, store.recent) { src, live, recorded ->
            if (src == PwSource.RECORDED) recorded else live
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasShell: Boolean get() = watcher.hasShell

    /** Curated well-known providers, from the shared catalogue (same as the Data tab). */
    val shortcuts: List<com.snatik.storage.core.data.ProviderShortcut> = watcher.shortcuts()

    init {
        watcher.ensureObservers()
        viewModelScope.launch {
            if (store.running.value || store.snapshot(1).isNotEmpty()) _source.value = PwSource.RECORDED
        }
    }

    fun setSource(s: PwSource) { _source.value = s }

    /** Toggle watching a URI; self-grants its permission via Shizuku when starting. */
    fun toggle(uri: String, label: String) {
        viewModelScope.launch {
            if (uri in watcher.watching.value) watcher.unwatch(uri) else watcher.watch(uri, label)
        }
    }

    fun addCustom(uri: String) {
        val u = uri.trim()
        if (u.startsWith("content://")) viewModelScope.launch { watcher.watch(u, u) }
    }

    fun unwatch(uri: String) = watcher.unwatch(uri)
    suspend fun providers() = watcher.providers()

    fun startRecording(capacity: Int) {
        store.setCapacity(capacity)
        viewModelScope.launch { store.applyCapacity() }
        ProviderRecorderService.start(context)
        _source.value = PwSource.RECORDED
    }

    fun stopRecording() = ProviderRecorderService.stop(context)
    fun clearRecording() { viewModelScope.launch { store.clear() } }
    fun clearLive() = watcher.clearLog()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderWatchScreen(onBack: () -> Unit, viewModel: ProviderWatchViewModel = koinViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val recordedCount by viewModel.recordedCount.collectAsStateWithLifecycle()
    val oldest by viewModel.oldest.collectAsStateWithLifecycle()
    val watching by viewModel.watching.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var opFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showRecord by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var view by rememberSaveable { mutableStateOf(PwView.OVERVIEW) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val filterActive = query.isNotBlank() || opFilter != null

    val visible = remember(entries, query, opFilter) {
        val q = query.trim().lowercase()
        entries.filter {
            (opFilter == null || it.op == opFilter) &&
                (q.isBlank() || it.uri.lowercase().contains(q) || it.target.lowercase().contains(q))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watch_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { showExport = true }, enabled = visible.isNotEmpty()) {
                        Icon(Icons.Default.IosShare, contentDescription = stringResource(R.string.pw_export))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.pw_menu), tint = if (filterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.pw_filter)) }, onClick = { showMenu = false; showFilters = true }, leadingIcon = { Icon(Icons.Default.FilterAlt, contentDescription = null) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.pw_add)) }, onClick = { showMenu = false; showAdd = true }, leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.pw_help)) }, onClick = { showMenu = false; showHelp = true }, leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SourceBar(source, recording, recordedCount, oldest, viewModel::setSource, { if (recording) viewModel.stopRecording() else showRecord = true }, viewModel::clearRecording, viewModel::clearLive)
            // Provider chips: quick-toggle the well-known providers from the shared catalogue.
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.shortcuts.forEach { s ->
                    FilterChip(selected = s.uri in watching, onClick = { viewModel.toggle(s.uri, s.title) }, label = { Text(s.title) })
                }
                // Any custom / discovered URIs the user added that aren't in the shortcut list.
                watching.filter { uri -> viewModel.shortcuts.none { it.uri == uri } }.forEach { uri ->
                    FilterChip(selected = true, onClick = { viewModel.unwatch(uri) }, label = { Text(uri.removePrefix("content://").take(24), maxLines = 1) })
                }
                FilterChip(selected = false, onClick = { showAdd = true }, label = { Text(stringResource(R.string.pw_add_short)) }, leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewChip(stringResource(R.string.pw_view_overview), view == PwView.OVERVIEW) { view = PwView.OVERVIEW }
                ViewChip(stringResource(R.string.pw_view_timeline), view == PwView.TIMELINE) { view = PwView.TIMELINE }
                ViewChip(stringResource(R.string.pw_view_providers), view == PwView.BY_PROVIDER) { view = PwView.BY_PROVIDER }
                ViewChip(stringResource(R.string.pw_view_ops), view == PwView.BY_OP) { view = PwView.BY_OP }
            }
            HorizontalDivider()
            Box(modifier = Modifier.fillMaxSize()) {
                if (visible.isEmpty()) {
                    val (title, body) = when {
                        watching.isEmpty() -> stringResource(R.string.watch_empty) to stringResource(R.string.watch_empty_body)
                        source == PwSource.RECORDED && recordedCount == 0 -> stringResource(R.string.pw_empty_recorded) to null
                        filterActive -> stringResource(R.string.pw_empty_filtered) to null
                        else -> stringResource(R.string.pw_empty_waiting) to stringResource(R.string.pw_empty_waiting_body)
                    }
                    EmptyState(if (filterActive) Icons.Default.FilterAlt else Icons.Default.Sensors, title, body)
                } else when (view) {
                    PwView.TIMELINE -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(visible, key = { it.key }) { e -> ChangeRow(e) { selectedKey = e.key } }
                    }
                    PwView.BY_PROVIDER -> ByProviderView(visible)
                    PwView.BY_OP -> ByOpView(visible)
                    PwView.OVERVIEW -> OverviewView(visible)
                }
            }
        }
    }

    val sel = selectedKey?.let { key -> entries.firstOrNull { it.key == key } }
    if (sel != null) ChangeDetailSheet(sel) { selectedKey = null }
    if (showFilters) FilterSheet(query, opFilter, { query = it }, { opFilter = it }) { showFilters = false }
    if (showRecord) RecordSheet(viewModel.store, viewModel.sink, watching.isEmpty(), { cap -> viewModel.startRecording(cap); showRecord = false }) { showRecord = false }
    if (showExport) ExportSheet(visible) { showExport = false }
    if (showAdd) AddProvidersSheet(viewModel, watching) { showAdd = false }
    if (showHelp) HelpSheet(viewModel.hasShell) { showHelp = false }
}

@Composable
private fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SourceBar(
    source: PwSource, recording: Boolean, recordedCount: Int, oldest: Long?,
    onSource: (PwSource) -> Unit, onRecordClick: () -> Unit, onClearRecorded: () -> Unit, onClearLive: () -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(selected = source == PwSource.LIVE, onClick = { onSource(PwSource.LIVE) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.pw_source_live)) }
                SegmentedButton(selected = source == PwSource.RECORDED, onClick = { onSource(PwSource.RECORDED) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.pw_source_recorded)) }
            }
            val recordMod = Modifier.widthIn(min = 116.dp)
            if (recording) {
                FilledTonalButton(onClick = onRecordClick, modifier = recordMod, colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer), contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.recorder_stop), modifier = Modifier.padding(start = 6.dp))
                }
            } else {
                FilledTonalButton(onClick = onRecordClick, modifier = recordMod, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Outlined.Circle, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.recorder_record), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (source == PwSource.RECORDED && recordedCount > 0) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val since = oldest?.let { humanAgo((System.currentTimeMillis() - it).coerceAtLeast(0)) }
                Text(if (since != null) stringResource(R.string.pw_recorded_stats, recordedCount, since) else stringResource(R.string.pw_recorded_count, recordedCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onClearRecorded) { Text(stringResource(R.string.pw_clear), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ChangeRow(e: ProviderChange, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(e.target, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                OpBadge(e.op)
            }
            Text(e.uri, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(humanClock(e.atMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OpBadge(op: String) {
    val color = opColor(op)
    Text(op.lowercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = color), modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp), maxLines = 1)
}

@Composable
private fun opColor(op: String): Color = when (op) {
    "INSERT" -> MaterialTheme.colorScheme.primary
    "UPDATE" -> MaterialTheme.colorScheme.secondary
    "DELETE" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/* ---------- Detail ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeDetailSheet(e: ProviderChange, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(e.target, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        OpBadge(e.op)
                    }
                    HorizontalDivider()
                    SelectionContainer { DetailRow(stringResource(R.string.pw_detail_uri), e.uri) }
                    DetailRow(stringResource(R.string.pw_detail_op), opLabel(e.op))
                    DetailRow(stringResource(R.string.pw_detail_when), humanClock(e.atMs))
                    SelectionContainer { DetailRow(stringResource(R.string.pw_detail_exact), absTime(e.atMs)) }
                    Spacer(Modifier.size(4.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).widthIn(min = 72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/* ---------- Aggregate views ---------- */

private data class TargetAgg(val target: String, val total: Int, val inserts: Int, val updates: Int, val deletes: Int)

@Composable
private fun ByProviderView(rows: List<ProviderChange>) {
    val aggs = remember(rows) {
        rows.groupBy { it.target }.map { (t, l) -> TargetAgg(t, l.size, l.count { it.op == "INSERT" }, l.count { it.op == "UPDATE" }, l.count { it.op == "DELETE" }) }.sortedByDescending { it.total }
    }
    val max = remember(aggs) { (aggs.firstOrNull()?.total ?: 1).coerceAtLeast(1) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(aggs, key = { it.target }) { a ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(a.target, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(a.total.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(2.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(a.total.toFloat() / max).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                }
                FlowRow(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (a.inserts > 0) MiniBadge("+${a.inserts}", MaterialTheme.colorScheme.primary)
                    if (a.updates > 0) MiniBadge("~${a.updates}", MaterialTheme.colorScheme.secondary)
                    if (a.deletes > 0) MiniBadge("-${a.deletes}", MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun ByOpView(rows: List<ProviderChange>) {
    val aggs = remember(rows) { rows.groupBy { it.op }.map { (op, l) -> op to l.size }.sortedByDescending { it.second } }
    val max = remember(aggs) { (aggs.firstOrNull()?.second ?: 1).coerceAtLeast(1) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(aggs, key = { it.first }) { (op, count) ->
            val color = opColor(op)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(opLabel(op), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = color)
                }
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 6.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(2.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(count.toFloat() / max).height(4.dp).background(color, RoundedCornerShape(2.dp)))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun OverviewView(rows: List<ProviderChange>) {
    val total = rows.size
    val providers = remember(rows) { rows.map { it.target }.distinct().size }
    val inserts = remember(rows) { rows.count { it.op == "INSERT" } }
    val deletes = remember(rows) { rows.count { it.op == "DELETE" } }
    val topProviders = remember(rows) { rows.groupBy { it.target }.map { (t, l) -> t to l.size }.sortedByDescending { it.second }.take(6) }
    val buckets = remember(rows) { hourlyBuckets(rows) }
    val provMax = (topProviders.firstOrNull()?.second ?: 1).coerceAtLeast(1)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(total.toString(), stringResource(R.string.pw_stat_changes), Modifier.weight(1f))
                StatTile(providers.toString(), stringResource(R.string.pw_stat_providers), Modifier.weight(1f))
                StatTile(inserts.toString(), stringResource(R.string.pw_stat_inserts), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                StatTile(deletes.toString(), stringResource(R.string.pw_stat_deletes), Modifier.weight(1f), MaterialTheme.colorScheme.error)
            }
        }
        if (buckets.any { it > 0 }) {
            item { SectionHeader(stringResource(R.string.pw_over_time)) }
            item { HourlyChart(buckets, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        item { SectionHeader(stringResource(R.string.pw_top_providers)) }
        items(topProviders, key = { "p:" + it.first }) { (t, count) ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(t, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 4.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(2.dp))) {
                        Box(modifier = Modifier.fillMaxWidth(count.toFloat() / provMax).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                    }
                }
                Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun hourlyBuckets(rows: List<ProviderChange>): IntArray {
    val out = IntArray(24)
    val now = System.currentTimeMillis()
    for (r in rows) {
        val h = ((now - r.atMs).coerceAtLeast(0) / 3_600_000L).toInt()
        if (h in 0..23) out[23 - h]++
    }
    return out
}

@Composable
private fun HourlyChart(buckets: IntArray, modifier: Modifier = Modifier) {
    val max = (buckets.maxOrNull() ?: 1).coerceAtLeast(1)
    val color = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            buckets.forEach { v ->
                Box(modifier = Modifier.weight(1f).fillMaxHeight(if (max == 0) 0f else (v.toFloat() / max).coerceAtLeast(if (v > 0) 0.04f else 0f)).background(if (v > 0) color else color.copy(alpha = 0.12f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.pw_chart_24h), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.pw_chart_now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
}

/* ---------- Sheets ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(query: String, opFilter: String?, onQuery: (String) -> Unit, onOp: (String?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.pw_filter_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.pw_search_hint)) })
            Text(stringResource(R.string.pw_filter_op), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StateChip(stringResource(R.string.pw_filter_any), opFilter == null) { onOp(null) }
                listOf("INSERT", "UPDATE", "DELETE", "UNKNOWN").forEach { op ->
                    StateChip(opLabel(op), opFilter == op) { onOp(op) }
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), selectedLabelColor = MaterialTheme.colorScheme.primary))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordSheet(store: ProviderRecorderStore, sink: ExternalSink, noProviders: Boolean, onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    val savedCap by store.capacity.collectAsStateWithLifecycle()
    var cap by remember { mutableStateOf(savedCap) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.pw_record_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.pw_record_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (noProviders) Text(stringResource(R.string.pw_record_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.recorder_capacity), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ProviderRecorderStore.CAPACITIES.forEachIndexed { i, c ->
                    SegmentedButton(selected = cap == c, onClick = { cap = c }, shape = SegmentedButtonDefaults.itemShape(i, ProviderRecorderStore.CAPACITIES.size)) { Text("%,d".format(c)) }
                }
            }
            com.snatik.storage.app.ui.components.ExternalSinkOption(sink)
            Button(onClick = { onStart(cap) }, modifier = Modifier.fillMaxWidth(), enabled = !noProviders) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.recorder_start_button), modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProvidersSheet(viewModel: ProviderWatchViewModel, watching: Set<String>, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    var discovered by remember { mutableStateOf<List<com.snatik.storage.core.data.ProviderEntry>?>(null) }
    var qsearch by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val shown = remember(discovered, qsearch) {
        val q = qsearch.trim().lowercase()
        discovered.orEmpty().filter { q.isBlank() || it.authority.lowercase().contains(q) || it.appLabel.lowercase().contains(q) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.pw_add_title), style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = custom, onValueChange = { custom = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("content://authority/path") }, textStyle = MonoStyle)
                    Button(onClick = { viewModel.addCustom(custom); custom = "" }, enabled = custom.trim().startsWith("content://")) { Text(stringResource(R.string.pw_add_watch)) }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pw_discover), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        if (discovered == null) TextButton(onClick = { scope.launch { discovered = viewModel.providers() } }) { Text(stringResource(R.string.pw_discover_load)) }
                    }
                    if (discovered != null) OutlinedTextField(value = qsearch, onValueChange = { qsearch = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.pw_discover_search)) })
                }
            }
            items(shown, key = { it.authority }) { p ->
                val uri = p.uri
                Row(modifier = Modifier.fillMaxWidth().clickable { if (uri in watching) viewModel.unwatch(uri) else viewModel.addCustom(uri) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.appLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.authority, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                    if (p.queryableNow) MiniBadge(stringResource(R.string.pw_readable), MaterialTheme.colorScheme.primary)
                    else if (p.readPermission != null) MiniBadge(stringResource(R.string.pw_needs_perm), MaterialTheme.colorScheme.onSurfaceVariant)
                    if (uri in watching) MiniBadge(stringResource(R.string.pw_watching), MaterialTheme.colorScheme.tertiary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
            item { Spacer(Modifier.size(8.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(rows: List<ProviderChange>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun export(kind: String) {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(System.currentTimeMillis())
                    val file = java.io.File(context.cacheDir, "providers-$stamp.${if (kind == "json") "jsonl" else "csv"}")
                    file.writeText(if (kind == "json") rowsToJsonl(rows) else rowsToCsv(rows))
                    file.absolutePath
                }.getOrNull()
            }
            if (path != null) com.snatik.storage.app.util.Intents.share(context, listOf(path))
            onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pw_export_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.pw_export_body, rows.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExportRow("CSV", stringResource(R.string.pw_export_csv)) { export("csv") }
            ExportRow("JSON", stringResource(R.string.pw_export_json)) { export("json") }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun ExportRow(title: String, sub: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.IosShare, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(hasShell: Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.pw_help_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.pw_help_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.pw_help_ops_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.pw_help_ops), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.pw_help_perms_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(if (hasShell) R.string.pw_help_perms_shell else R.string.pw_help_perms_noshell), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
        }
    }
}

/* ---------- export + helpers ---------- */

private fun csvCell(s: String?): String {
    val v = s.orEmpty()
    return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

private fun rowsToCsv(rows: List<ProviderChange>): String = buildString {
    appendLine("provider,uri,op,at_iso")
    for (r in rows) { append(csvCell(r.target)); append(','); append(csvCell(r.uri)); append(','); append(r.op); append(','); appendLine(absTime(r.atMs)) }
}

private fun rowsToJsonl(rows: List<ProviderChange>): String = buildString {
    for (r in rows) { append(org.json.JSONObject().put("provider", r.target).put("uri", r.uri).put("op", r.op).put("at_ms", r.atMs).toString()); append('\n') }
}

private fun opLabel(op: String): String = when (op) {
    "INSERT" -> "Insert"; "UPDATE" -> "Update"; "DELETE" -> "Delete"; else -> "Change"
}

private val absFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
private fun absTime(ms: Long): String = absFmt.format(ms)

private fun humanClock(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when { s < 5 -> "now"; s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60}m ago"; s < 86400 -> "${s / 3600}h ago"; else -> "${s / 86400}d ago" }
}

private fun humanAgo(ms: Long): String {
    val s = ms / 1000
    return when { s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60}m ago"; s < 86400 -> "${s / 3600}h ago"; else -> "${s / 86400}d ago" }
}
