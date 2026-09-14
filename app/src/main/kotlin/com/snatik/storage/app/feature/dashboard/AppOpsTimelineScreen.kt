package com.snatik.storage.app.feature.dashboard

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import android.content.Context
import com.snatik.storage.core.apps.AppOpAccess
import com.snatik.storage.core.apps.AppOpState
import com.snatik.storage.core.apps.AppOpsRecorderStore
import com.snatik.storage.core.apps.AppOpsTimeline
import com.snatik.storage.core.apps.ExternalSink
import com.snatik.storage.core.shell.PrivilegeManager
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

/** Where the timeline reads from: a one-shot dumpsys snapshot, or the persisted recorder history. */
enum class TimelineSource { LIVE, RECORDED }

/** How the accesses are presented. */
enum class TimelineView { OVERVIEW, TIMELINE, BY_APP, BY_OP }

class AppOpsTimelineViewModel(
    private val context: Context,
    private val timeline: AppOpsTimeline,
    val store: AppOpsRecorderStore,
    val sink: ExternalSink,
    private val privilege: PrivilegeManager,
) : ViewModel() {

    private val _live = MutableStateFlow<List<AppOpAccess>?>(null)
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _source = MutableStateFlow(TimelineSource.LIVE)
    val source: StateFlow<TimelineSource> = _source.asStateFlow()

    val recording: StateFlow<Boolean> = store.running
    val recordedCount: StateFlow<Int> = store.count.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val oldest: StateFlow<Long?> = store.oldest.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The rows the screen shows, switched by [source]. Null = still loading the live snapshot. */
    val entries: StateFlow<List<AppOpAccess>?> =
        combine(_source, _live, store.recent) { src, live, recorded ->
            if (src == TimelineSource.RECORDED) recorded else live
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val shell get() = privilege.executor.value != null

    init {
        loadLive()
        // If a recording already holds data, open on it.
        viewModelScope.launch {
            if (store.snapshot(1).isNotEmpty()) _source.value = TimelineSource.RECORDED
        }
    }

    fun loadLive() {
        viewModelScope.launch {
            _loading.value = true
            _live.value = timeline.recent()
            _loading.value = false
        }
    }

    fun setSource(s: TimelineSource) {
        _source.value = s
        if (s == TimelineSource.LIVE && _live.value == null) loadLive()
    }

    fun refresh() {
        if (_source.value == TimelineSource.LIVE) loadLive()
        // recorded source is a live Room Flow — nothing to pull.
    }

    fun startRecording(capacity: Int, intervalSec: Int) {
        store.setCapacity(capacity)
        store.setInterval(intervalSec)
        viewModelScope.launch { store.applyCapacity() }
        AppOpsRecorderService.start(context)
        _source.value = TimelineSource.RECORDED
    }

    fun stopRecording() = AppOpsRecorderService.stop(context)

    fun clearRecording() {
        viewModelScope.launch { store.clear() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOpsTimelineScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit, viewModel: AppOpsTimelineViewModel = koinViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val recordedCount by viewModel.recordedCount.collectAsStateWithLifecycle()
    val oldest by viewModel.oldest.collectAsStateWithLifecycle()
    var sensitiveOnly by rememberSaveable { mutableStateOf(true) }
    var stateFilter by rememberSaveable { mutableStateOf<AppOpState?>(null) }
    var deniedFilter by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showRecord by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var view by rememberSaveable { mutableStateOf(TimelineView.OVERVIEW) }
    val filterActive = sensitiveOnly || stateFilter != null || deniedFilter != null
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    // Filtered rows, shared by every view and the export sheet. Null while the live snapshot loads.
    val visible = remember(entries, sensitiveOnly, stateFilter, deniedFilter) {
        entries?.filter {
            (!sensitiveOnly || it.sensitive) &&
                (stateFilter == null || it.state == stateFilter) &&
                (deniedFilter == null || it.denied == deniedFilter)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timeline_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    if (source == TimelineSource.LIVE) {
                        IconButton(onClick = { viewModel.refresh() }, enabled = !loading) {
                            if (loading && entries != null) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.timeline_refresh))
                        }
                    }
                    IconButton(onClick = { showExport = true }, enabled = visible?.isNotEmpty() == true) {
                        Icon(Icons.Default.IosShare, contentDescription = stringResource(R.string.timeline_export))
                    }
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Default.FilterAlt, contentDescription = stringResource(R.string.timeline_filter), tint = if (filterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showHelp = true }) { Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.timeline_help)) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SourceBar(
                source = source,
                recording = recording,
                recordedCount = recordedCount,
                oldest = oldest,
                onSource = viewModel::setSource,
                onRecordClick = { if (recording) viewModel.stopRecording() else showRecord = true },
                onClear = viewModel::clearRecording,
            )
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewChip(stringResource(R.string.timeline_view_overview), view == TimelineView.OVERVIEW) { view = TimelineView.OVERVIEW }
                ViewChip(stringResource(R.string.timeline_view_timeline), view == TimelineView.TIMELINE) { view = TimelineView.TIMELINE }
                ViewChip(stringResource(R.string.timeline_view_apps), view == TimelineView.BY_APP) { view = TimelineView.BY_APP }
                ViewChip(stringResource(R.string.timeline_view_ops), view == TimelineView.BY_OP) { view = TimelineView.BY_OP }
            }
            HorizontalDivider()
            Box(modifier = Modifier.fillMaxSize()) {
                val list = entries
                when {
                    !viewModel.shell && list == null -> EmptyState(Icons.Default.Terminal, stringResource(R.string.timeline_needs_shell), null)
                    list == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    else -> {
                        val shown = visible.orEmpty()
                        if (shown.isEmpty()) {
                            EmptyState(Icons.Default.FilterAlt, stringResource(if (source == TimelineSource.RECORDED && recordedCount == 0) R.string.timeline_empty_recorded else R.string.timeline_empty), null)
                        } else when (view) {
                            TimelineView.TIMELINE -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                                items(shown, key = { it.key }) { e -> Entry(e) { selectedKey = e.key } }
                            }
                            TimelineView.BY_APP -> ByAppView(shown, onOpenApp)
                            TimelineView.BY_OP -> ByOpView(shown)
                            TimelineView.OVERVIEW -> OverviewView(shown, onOpenApp)
                        }
                    }
                }
            }
        }
    }

    val sel = selectedKey?.let { key -> entries?.firstOrNull { it.key == key } }
    if (sel != null) {
        AccessDetailSheet(sel, onOpenApp = { onOpenApp(it) }, onDismiss = { selectedKey = null })
    }
    if (showFilters) {
        FilterSheet(
            sensitiveOnly = sensitiveOnly,
            stateFilter = stateFilter,
            deniedFilter = deniedFilter,
            onSensitiveChange = { sensitiveOnly = it },
            onStateChange = { stateFilter = it },
            onDeniedChange = { deniedFilter = it },
            onDismiss = { showFilters = false },
        )
    }
    if (showRecord) {
        RecordSheet(
            store = viewModel.store,
            sink = viewModel.sink,
            onStart = { cap, interval -> viewModel.startRecording(cap, interval); showRecord = false },
            onDismiss = { showRecord = false },
        )
    }
    if (showExport) {
        ExportSheet(rows = visible.orEmpty(), onDismiss = { showExport = false })
    }
    if (showHelp) TimelineHelpSheet(onDismiss = { showHelp = false })
}

@Composable
private fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SourceBar(
    source: TimelineSource,
    recording: Boolean,
    recordedCount: Int,
    oldest: Long?,
    onSource: (TimelineSource) -> Unit,
    onRecordClick: () -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(selected = source == TimelineSource.LIVE, onClick = { onSource(TimelineSource.LIVE) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(stringResource(R.string.timeline_source_live))
                }
                SegmentedButton(selected = source == TimelineSource.RECORDED, onClick = { onSource(TimelineSource.RECORDED) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(stringResource(R.string.timeline_source_recorded))
                }
            }
            // Same button footprint in both states so switching never resizes the toggle.
            val recordMod = Modifier.widthIn(min = 116.dp)
            if (recording) {
                FilledTonalButton(
                    onClick = onRecordClick,
                    modifier = recordMod,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
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
        if (source == TimelineSource.RECORDED && recordedCount > 0) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val since = oldest?.let { humanAgo((System.currentTimeMillis() - it).coerceAtLeast(0)) }
                Text(
                    if (since != null) stringResource(R.string.timeline_recorded_stats, recordedCount, since) else stringResource(R.string.timeline_recorded_count, recordedCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.timeline_clear), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun Entry(e: AppOpAccess, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(e.packageName, size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(e.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (e.denied) {
                    Text(
                        stringResource(R.string.timeline_result_denied),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error),
                        modifier = Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                OpTag(e)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StateLabel(e.state)
                Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(humanAgo(e.agoMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.durationMs?.let {
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.timeline_held, humanDuration(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun OpTag(e: AppOpAccess) {
    val color = if (e.sensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        e.op.lowercase(),
        style = MonoStyle.copy(color = color),
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
    )
}

@Composable
private fun StateLabel(state: AppOpState) {
    val (labelRes, color) = when (state) {
        AppOpState.BACKGROUND -> R.string.timeline_state_background to MaterialTheme.colorScheme.error
        AppOpState.FOREGROUND -> R.string.timeline_state_foreground to MaterialTheme.colorScheme.onSurfaceVariant
        AppOpState.FOREGROUND_SERVICE -> R.string.timeline_state_fgservice to MaterialTheme.colorScheme.onSurfaceVariant
        AppOpState.PERSISTENT -> R.string.timeline_state_system to MaterialTheme.colorScheme.onSurfaceVariant
        AppOpState.UNKNOWN -> R.string.timeline_state_unknown to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bg = state == AppOpState.BACKGROUND
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (bg) FontWeight.SemiBold else FontWeight.Normal),
        color = color,
    )
}

/* ---------- Detail sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessDetailSheet(e: AppOpAccess, onOpenApp: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(e.packageName, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(e.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.packageName, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
            }
            HorizontalDivider()
            DetailRow(stringResource(R.string.timeline_detail_op), e.op)
            DetailRow(stringResource(R.string.timeline_detail_state), stringResource(stateLabelRes(e.state)), if (e.background) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            DetailRow(stringResource(R.string.timeline_detail_when), humanAgo(e.agoMs))
            SelectionContainer { DetailRow(stringResource(R.string.timeline_detail_exact), e.absTime) }
            e.durationMs?.let { DetailRow(stringResource(R.string.timeline_detail_duration), humanDuration(it)) }
            if (e.background) {
                Text(stringResource(R.string.timeline_bg_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenApp(e.packageName) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.timeline_open_app, e.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(1f))
    }
}

/* ---------- Aggregate views ---------- */

private data class AppAgg(val pkg: String, val label: String, val total: Int, val background: Int, val denied: Int, val sensitive: Int)
private data class OpAgg(val op: String, val total: Int, val apps: Int, val background: Int, val denied: Int, val sensitive: Boolean)

@Composable
private fun ByAppView(rows: List<AppOpAccess>, onOpenApp: (String) -> Unit) {
    val apps = remember(rows) {
        rows.groupBy { it.packageName }.map { (pkg, list) ->
            AppAgg(pkg, list.first().label, list.size, list.count { it.background }, list.count { it.denied }, list.count { it.sensitive })
        }.sortedByDescending { it.total }
    }
    val max = remember(apps) { (apps.firstOrNull()?.total ?: 1).coerceAtLeast(1) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(apps, key = { it.pkg }) { a ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onOpenApp(a.pkg) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(a.pkg, size = 32.dp)
                    Text(a.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(a.total.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 6.dp, start = 44.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(2.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(a.total.toFloat() / max).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                }
                FlowRow(modifier = Modifier.padding(start = 44.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (a.background > 0) MiniBadge(stringResource(R.string.timeline_agg_background, a.background), MaterialTheme.colorScheme.error)
                    if (a.denied > 0) MiniBadge(stringResource(R.string.timeline_agg_denied, a.denied), MaterialTheme.colorScheme.error)
                    if (a.sensitive > 0) MiniBadge(stringResource(R.string.timeline_agg_sensitive, a.sensitive), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun ByOpView(rows: List<AppOpAccess>) {
    val ops = remember(rows) {
        rows.groupBy { it.op }.map { (op, list) ->
            OpAgg(op, list.size, list.map { it.packageName }.distinct().size, list.count { it.background }, list.count { it.denied }, list.first().sensitive)
        }.sortedByDescending { it.total }
    }
    val max = remember(ops) { (ops.firstOrNull()?.total ?: 1).coerceAtLeast(1) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(ops, key = { it.op }) { o ->
            val color = if (o.sensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(o.op.lowercase(), style = MonoStyle.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.timeline_agg_apps, o.apps), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(o.total.toString(), style = MaterialTheme.typography.titleMedium, color = color)
                }
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 6.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(2.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(o.total.toFloat() / max).height(4.dp).background(color, RoundedCornerShape(2.dp)))
                }
                if (o.background > 0 || o.denied > 0) {
                    FlowRow(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (o.background > 0) MiniBadge(stringResource(R.string.timeline_agg_background, o.background), MaterialTheme.colorScheme.error)
                        if (o.denied > 0) MiniBadge(stringResource(R.string.timeline_agg_denied, o.denied), MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun OverviewView(rows: List<AppOpAccess>, onOpenApp: (String) -> Unit) {
    val total = rows.size
    val apps = remember(rows) { rows.map { it.packageName }.distinct().size }
    val background = remember(rows) { rows.count { it.background } }
    val denied = remember(rows) { rows.count { it.denied } }
    val topApps = remember(rows) {
        rows.groupBy { it.packageName }.map { (p, l) -> AppAgg(p, l.first().label, l.size, l.count { it.background }, l.count { it.denied }, l.count { it.sensitive }) }
            .sortedByDescending { it.total }.take(5)
    }
    val topDenied = remember(rows) {
        rows.filter { it.denied }.groupBy { it.packageName }.map { (p, l) -> AppAgg(p, l.first().label, l.size, 0, l.size, 0) }
            .sortedByDescending { it.total }.take(5)
    }
    val buckets = remember(rows) { hourlyBuckets(rows) }
    val appMax = (topApps.firstOrNull()?.total ?: 1).coerceAtLeast(1)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(total.toString(), stringResource(R.string.timeline_stat_accesses), Modifier.weight(1f))
                StatTile(apps.toString(), stringResource(R.string.timeline_stat_apps), Modifier.weight(1f))
                StatTile(background.toString(), stringResource(R.string.timeline_stat_background), Modifier.weight(1f), MaterialTheme.colorScheme.error)
                StatTile(denied.toString(), stringResource(R.string.timeline_stat_denied), Modifier.weight(1f), MaterialTheme.colorScheme.error)
            }
        }
        if (buckets.any { it > 0 }) {
            item { SectionHeader(stringResource(R.string.timeline_over_time)) }
            item { HourlyChart(buckets, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        item { SectionHeader(stringResource(R.string.timeline_top_apps)) }
        items(topApps, key = { "a:" + it.pkg }) { a ->
            Row(modifier = Modifier.fillMaxWidth().clickable { onOpenApp(a.pkg) }.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(a.pkg, size = 28.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 4.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(2.dp))) {
                        Box(modifier = Modifier.fillMaxWidth(a.total.toFloat() / appMax).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                    }
                }
                Text(a.total.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (topDenied.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.timeline_top_denied)) }
            items(topDenied, key = { "d:" + it.pkg }) { a ->
                Row(modifier = Modifier.fillMaxWidth().clickable { onOpenApp(a.pkg) }.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(a.pkg, size = 28.dp)
                    Text(a.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.denied.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 24 one-hour buckets ending now; value = number of accesses in that hour. */
private fun hourlyBuckets(rows: List<AppOpAccess>): IntArray {
    val out = IntArray(24)
    val hourMs = 3_600_000L
    for (r in rows) {
        val h = (r.agoMs / hourMs).toInt()
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
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(if (max == 0) 0f else (v.toFloat() / max).coerceAtLeast(if (v > 0) 0.04f else 0f))
                        .background(if (v > 0) color else color.copy(alpha = 0.12f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.timeline_chart_24h), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.timeline_chart_now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

/* ---------- Export sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(rows: List<AppOpAccess>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun export(kind: String) {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(System.currentTimeMillis())
                    val now = System.currentTimeMillis()
                    val file = java.io.File(context.cacheDir, "appops-$stamp.${if (kind == "json") "jsonl" else if (kind == "csv") "csv" else "txt"}")
                    file.writeText(when (kind) { "csv" -> rowsToCsv(rows, now); "json" -> rowsToJsonl(rows, now); else -> rowsToReport(rows) })
                    file.absolutePath
                }.getOrNull()
            }
            if (path != null) com.snatik.storage.app.util.Intents.share(context, listOf(path))
            onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.timeline_export_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.timeline_export_body, rows.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExportRow("CSV", stringResource(R.string.timeline_export_csv)) { export("csv") }
            ExportRow("JSON", stringResource(R.string.timeline_export_json)) { export("json") }
            ExportRow(stringResource(R.string.timeline_export_report_label), stringResource(R.string.timeline_export_report)) { export("report") }
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

private fun csvCell(s: String?): String {
    val v = s.orEmpty()
    return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

private fun rowsToCsv(rows: List<AppOpAccess>, now: Long): String = buildString {
    appendLine("app,package,op,category,state,denied,duration_ms,at_iso")
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
    for (r in rows) {
        append(csvCell(r.label)); append(',')
        append(csvCell(r.packageName)); append(',')
        append(csvCell(r.op)); append(',')
        append(if (r.sensitive) "sensitive" else "other"); append(',')
        append(r.state.name.lowercase()); append(',')
        append(if (r.denied) "denied" else "allowed"); append(',')
        append(r.durationMs?.toString().orEmpty()); append(',')
        appendLine(fmt.format(now - r.agoMs))
    }
}

private fun rowsToJsonl(rows: List<AppOpAccess>, now: Long): String = buildString {
    for (r in rows) {
        append(
            org.json.JSONObject()
                .put("app", r.label).put("package", r.packageName).put("op", r.op)
                .put("sensitive", r.sensitive).put("state", r.state.name.lowercase())
                .put("denied", r.denied).put("duration_ms", r.durationMs ?: org.json.JSONObject.NULL)
                .put("at_ms", now - r.agoMs).toString(),
        )
        append('\n')
    }
}

private fun rowsToReport(rows: List<AppOpAccess>): String = buildString {
    appendLine("App-ops report — ${rows.size} accesses")
    appendLine("Denied: ${rows.count { it.denied }}   Background: ${rows.count { it.background }}   Apps: ${rows.map { it.packageName }.distinct().size}")
    appendLine()
    appendLine("Top apps:")
    rows.groupBy { it.packageName }.map { (p, l) -> l.first().label to l.size }.sortedByDescending { it.second }.take(15).forEach {
        appendLine("  ${it.second.toString().padStart(5)}  ${it.first}")
    }
    appendLine()
    appendLine("Top operations:")
    rows.groupBy { it.op }.map { it.key to it.value.size }.sortedByDescending { it.second }.take(15).forEach {
        appendLine("  ${it.second.toString().padStart(5)}  ${it.first.lowercase()}")
    }
}

/* ---------- Filter sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    sensitiveOnly: Boolean,
    stateFilter: AppOpState?,
    deniedFilter: Boolean?,
    onSensitiveChange: (Boolean) -> Unit,
    onStateChange: (AppOpState?) -> Unit,
    onDeniedChange: (Boolean?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.timeline_filter_title), style = MaterialTheme.typography.titleLarge)

            // Scope: sensitive vs all
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.timeline_sensitive_only), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.timeline_sensitive_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = sensitiveOnly, onCheckedChange = onSensitiveChange)
            }

            HorizontalDivider()

            // App state
            Text(stringResource(R.string.timeline_filter_state), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StateChip(stringResource(R.string.timeline_filter_any), stateFilter == null) { onStateChange(null) }
                StateChip(stringResource(R.string.timeline_state_background), stateFilter == AppOpState.BACKGROUND, MaterialTheme.colorScheme.error) { onStateChange(AppOpState.BACKGROUND) }
                StateChip(stringResource(R.string.timeline_state_foreground), stateFilter == AppOpState.FOREGROUND) { onStateChange(AppOpState.FOREGROUND) }
                StateChip(stringResource(R.string.timeline_state_fgservice), stateFilter == AppOpState.FOREGROUND_SERVICE) { onStateChange(AppOpState.FOREGROUND_SERVICE) }
                StateChip(stringResource(R.string.timeline_state_system), stateFilter == AppOpState.PERSISTENT) { onStateChange(AppOpState.PERSISTENT) }
            }

            HorizontalDivider()

            // Result: allowed vs denied
            Text(stringResource(R.string.timeline_filter_result), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StateChip(stringResource(R.string.timeline_filter_any), deniedFilter == null) { onDeniedChange(null) }
                StateChip(stringResource(R.string.timeline_result_allowed), deniedFilter == false) { onDeniedChange(false) }
                StateChip(stringResource(R.string.timeline_result_denied), deniedFilter == true, MaterialTheme.colorScheme.error) { onDeniedChange(true) }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

/* ---------- Record start sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordSheet(store: AppOpsRecorderStore, sink: ExternalSink, onStart: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val savedCap by store.capacity.collectAsStateWithLifecycle()
    val savedInterval by store.intervalSec.collectAsStateWithLifecycle()
    var cap by remember { mutableStateOf(savedCap) }
    var interval by remember { mutableStateOf(savedInterval) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.recorder_start_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.recorder_start_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.recorder_interval), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppOpsRecorderStore.INTERVALS.forEachIndexed { i, sec ->
                    SegmentedButton(selected = interval == sec, onClick = { interval = sec }, shape = SegmentedButtonDefaults.itemShape(i, AppOpsRecorderStore.INTERVALS.size)) {
                        Text(humanInterval(sec))
                    }
                }
            }

            Text(stringResource(R.string.recorder_capacity), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppOpsRecorderStore.CAPACITIES.forEachIndexed { i, c ->
                    SegmentedButton(selected = cap == c, onClick = { cap = c }, shape = SegmentedButtonDefaults.itemShape(i, AppOpsRecorderStore.CAPACITIES.size)) {
                        Text("%,d".format(c))
                    }
                }
            }
            com.snatik.storage.app.ui.components.ExternalSinkOption(sink)

            Button(onClick = { onStart(cap, interval) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.recorder_start_button), modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

private fun humanInterval(sec: Int): String = when {
    sec < 60 -> "${sec}s"
    sec % 60 == 0 -> "${sec / 60}m"
    else -> "${sec}s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StateChip(label: String, selected: Boolean, accent: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.16f),
            selectedLabelColor = accent,
        ),
    )
}

/* ---------- Help sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.timeline_help_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.timeline_help_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.timeline_help_states_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            HelpState(R.string.timeline_state_background, R.string.timeline_help_background, MaterialTheme.colorScheme.error)
            HelpState(R.string.timeline_state_foreground, R.string.timeline_help_foreground, MaterialTheme.colorScheme.onSurfaceVariant)
            HelpState(R.string.timeline_state_fgservice, R.string.timeline_help_fgservice, MaterialTheme.colorScheme.onSurfaceVariant)
            HelpState(R.string.timeline_state_system, R.string.timeline_help_system, MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.timeline_help_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun HelpState(labelRes: Int, bodyRes: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(stringResource(bodyRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(top = 2.dp))
    }
}

private fun stateLabelRes(state: AppOpState): Int = when (state) {
    AppOpState.BACKGROUND -> R.string.timeline_state_background
    AppOpState.FOREGROUND -> R.string.timeline_state_foreground
    AppOpState.FOREGROUND_SERVICE -> R.string.timeline_state_fgservice
    AppOpState.PERSISTENT -> R.string.timeline_state_system
    AppOpState.UNKNOWN -> R.string.timeline_state_unknown
}

private fun humanAgo(ms: Long): String {
    val s = ms / 1000
    return when { s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60}m ago"; s < 86400 -> "${s / 3600}h ago"; else -> "${s / 86400}d ago" }
}

private fun humanDuration(ms: Long): String {
    val s = ms / 1000
    return when { ms < 1000 -> "${ms}ms"; s < 60 -> "${s}s"; s < 3600 -> "${s / 60}m ${s % 60}s"; else -> "${s / 3600}h ${(s % 3600) / 60}m" }
}
