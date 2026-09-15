package com.snatik.storage.app.feature.history

import android.text.format.DateUtils
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.apps.AppEvent
import com.snatik.storage.core.apps.AppEventLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

data class AppHistoryUiState(val events: List<AppEvent> = emptyList(), val loading: Boolean = true)

class AppHistoryViewModel(private val log: AppEventLog) : ViewModel() {
    private val _state = MutableStateFlow(AppHistoryUiState())
    val state: StateFlow<AppHistoryUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            if (_state.value.events.isEmpty()) _state.update { it.copy(loading = true) }
            runCatching { log.reconcile() }
            val events = runCatching { log.history() }.getOrDefault(emptyList())
            _state.update { AppHistoryUiState(events, loading = false) }
        }
    }

    fun clear() {
        viewModelScope.launch { log.clear(); refresh() }
    }
}

private enum class HistView { OVERVIEW, TIMELINE, BY_APP }
private enum class HistType { ALL, INSTALLED, UPDATED, UNINSTALLED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHistoryScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit, viewModel: AppHistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    var view by rememberSaveable { mutableStateOf(HistView.OVERVIEW) }
    var type by rememberSaveable { mutableStateOf(HistType.ALL) }
    var hideSystem by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var perAppPkg by rememberSaveable { mutableStateOf<String?>(null) }

    val visible = remember(state.events, type, hideSystem, query) {
        val q = query.trim().lowercase()
        state.events.filter {
            (!hideSystem || !it.system) &&
                (type == HistType.ALL || it.type == type.name.lowercase()) &&
                (q.isBlank() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_history_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.hist_export)) }, enabled = visible.isNotEmpty(), onClick = { menu = false; showExport = true }, leadingIcon = { Icon(Icons.Default.IosShare, contentDescription = null) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.history_clear)) }, onClick = { menu = false; viewModel.clear() }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.hist_about)) }, onClick = { menu = false; showHelp = true }, leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) })
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewChip(stringResource(R.string.hist_view_overview), view == HistView.OVERVIEW) { view = HistView.OVERVIEW }
                ViewChip(stringResource(R.string.hist_view_timeline), view == HistView.TIMELINE) { view = HistView.TIMELINE }
                ViewChip(stringResource(R.string.hist_view_apps), view == HistView.BY_APP) { view = HistView.BY_APP }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                singleLine = true, placeholder = { Text(stringResource(R.string.history_search)) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip(stringResource(R.string.hist_all), type == HistType.ALL) { type = HistType.ALL }
                TypeChip(stringResource(R.string.ev_installed), type == HistType.INSTALLED) { type = HistType.INSTALLED }
                TypeChip(stringResource(R.string.ev_updated), type == HistType.UPDATED) { type = HistType.UPDATED }
                TypeChip(stringResource(R.string.ev_uninstalled), type == HistType.UNINSTALLED) { type = HistType.UNINSTALLED }
                FilterChip(selected = hideSystem, onClick = { hideSystem = !hideSystem }, label = { Text(stringResource(R.string.hist_hide_system)) })
            }
            HorizontalDivider()
            Box(modifier = Modifier.fillMaxSize()) {
                if (!state.loading && visible.isEmpty()) {
                    EmptyState(Icons.Default.History, stringResource(R.string.history_empty), null)
                } else when (view) {
                    HistView.OVERVIEW -> OverviewView(visible)
                    HistView.TIMELINE -> TimelineView(visible) { selectedId = it.id }
                    HistView.BY_APP -> ByAppView(visible) { perAppPkg = it }
                }
            }
        }
    }

    val sel = selectedId?.let { id -> state.events.firstOrNull { it.id == id } }
    if (sel != null) EventDetailSheet(sel, onOpenApp = { onOpenApp(sel.packageName); selectedId = null }, onAppHistory = { perAppPkg = sel.packageName; selectedId = null }) { selectedId = null }
    perAppPkg?.let { pkg ->
        PerAppSheet(state.events.filter { it.packageName == pkg }, onOpenApp = { onOpenApp(pkg); perAppPkg = null }) { perAppPkg = null }
    }
    if (showExport) ExportSheet(visible) { showExport = false }
    if (showHelp) HelpSheet { showHelp = false }
}

@Composable
private fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit) = FilterChip(selected, onClick, label = { Text(label) })

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) = FilterChip(selected, onClick, label = { Text(label) })

/* ---------- Timeline ---------- */

@Composable
private fun TimelineView(events: List<AppEvent>, onClick: (AppEvent) -> Unit) {
    val groups = remember(events) { events.groupBy { dayStart(it.ts) } }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        groups.forEach { (dayTs, list) ->
            item(key = "d:$dayTs") { DayHeader(DateUtils.getRelativeTimeSpanString(dayTs, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()) }
            items(list, key = { it.id }) { event -> EventRow(event) { onClick(event) } }
        }
    }
}

private fun dayStart(ts: Long): Long = ts - (ts % DateUtils.DAY_IN_MILLIS)

@Composable
private fun DayHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))
}

@Composable
private fun EventRow(event: AppEvent, onClick: () -> Unit) {
    val context = LocalContext.current
    val (icon, tint) = eventVisual(event.type)
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(38.dp).background(tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(event.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                if (event.system) MiniBadge(stringResource(R.string.hist_system), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(event.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            val seeded = if (event.seeded) "  ·  " + stringResource(R.string.hist_seeded) else ""
            Text(eventLabel(event.type) + versionSuffix(event) + "  ·  " + event.ts.relativeTime(context) + seeded, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ---------- By app ---------- */

private data class AppAgg(val pkg: String, val label: String, val count: Int, val lastTs: Long, val installed: Boolean, val system: Boolean, val updates: Int)

@Composable
private fun ByAppView(events: List<AppEvent>, onOpenAppHistory: (String) -> Unit) {
    val context = LocalContext.current
    val aggs = remember(events) {
        events.groupBy { it.packageName }.map { (pkg, list) ->
            val newest = list.maxByOrNull { it.ts }!!
            AppAgg(pkg, newest.label, list.size, newest.ts, newest.type != "uninstalled", newest.system, list.count { it.type == "updated" })
        }.sortedByDescending { it.lastTs }
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(aggs, key = { it.pkg }) { a ->
            Row(modifier = Modifier.fillMaxWidth().clickable { onOpenAppHistory(a.pkg) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(a.pkg, size = 36.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(a.pkg, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                        if (!a.installed) MiniBadge(stringResource(R.string.ev_uninstalled), MaterialTheme.colorScheme.error)
                        if (a.updates > 0) MiniBadge(stringResource(R.string.hist_updates_n, a.updates), MaterialTheme.colorScheme.tertiary)
                        if (a.system) MiniBadge(stringResource(R.string.hist_system), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(a.count.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(a.lastTs.relativeTime(context), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

/* ---------- Overview ---------- */

@Composable
private fun OverviewView(events: List<AppEvent>) {
    val installs = remember(events) { events.count { it.type == "installed" } }
    val updates = remember(events) { events.count { it.type == "updated" } }
    val removals = remember(events) { events.count { it.type == "uninstalled" } }
    val apps = remember(events) { events.map { it.packageName }.distinct().size }
    val buckets = remember(events) { dailyBuckets(events, 30) }
    val topUpdated = remember(events) {
        events.filter { it.type == "updated" }.groupBy { it.packageName }
            .map { (p, l) -> Triple(p, l.first().label, l.size) }.sortedByDescending { it.third }.take(6)
    }
    val updMax = (topUpdated.firstOrNull()?.third ?: 1).coerceAtLeast(1)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(installs.toString(), stringResource(R.string.hist_stat_installs), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                StatTile(updates.toString(), stringResource(R.string.hist_stat_updates), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                StatTile(removals.toString(), stringResource(R.string.hist_stat_removals), Modifier.weight(1f), MaterialTheme.colorScheme.error)
                StatTile(apps.toString(), stringResource(R.string.hist_stat_apps), Modifier.weight(1f))
            }
        }
        if (buckets.any { it > 0 }) {
            item { SectionHeader(stringResource(R.string.hist_over_time)) }
            item { DailyChart(buckets, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        if (topUpdated.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.hist_most_updated)) }
            items(topUpdated, key = { "u:" + it.first }) { (pkg, label, n) ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(pkg, size = 28.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 4.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(2.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(n.toFloat() / updMax).height(4.dp).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(2.dp)))
                        }
                    }
                    Text(n.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

/** [days] one-day buckets ending today; value = events that day. */
private fun dailyBuckets(events: List<AppEvent>, days: Int): IntArray {
    val out = IntArray(days)
    val today = dayStart(System.currentTimeMillis())
    for (e in events) {
        val d = ((today - dayStart(e.ts)) / DateUtils.DAY_IN_MILLIS).toInt()
        if (d in 0 until days) out[days - 1 - d]++
    }
    return out
}

@Composable
private fun DailyChart(buckets: IntArray, modifier: Modifier = Modifier) {
    val max = (buckets.maxOrNull() ?: 1).coerceAtLeast(1)
    val color = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            buckets.forEach { v ->
                Box(modifier = Modifier.weight(1f).fillMaxHeight(if (max == 0) 0f else (v.toFloat() / max).coerceAtLeast(if (v > 0) 0.04f else 0f)).background(if (v > 0) color else color.copy(alpha = 0.12f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.hist_chart_30d), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.hist_chart_today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ---------- Detail + per-app sheets ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(e: AppEvent, onOpenApp: () -> Unit, onAppHistory: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val (icon, tint) = eventVisual(e.type)
                        Box(modifier = Modifier.size(40.dp).background(tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(e.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(eventLabel(e.type), style = MaterialTheme.typography.bodySmall, color = tint)
                        }
                        if (e.system) MiniBadge(stringResource(R.string.hist_system), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    SelectionContainer { DetailRow(stringResource(R.string.hist_detail_package), e.packageName) }
                    versionDetail(e)?.let { DetailRow(stringResource(R.string.hist_detail_version), it) }
                    DetailRow(stringResource(R.string.hist_detail_when), e.ts.relativeTime(context))
                    SelectionContainer { DetailRow(stringResource(R.string.hist_detail_exact), absTime(e.ts)) }
                    if (e.seeded) DetailRow(stringResource(R.string.hist_detail_source), stringResource(R.string.hist_seeded))
                    HorizontalDivider()
                    if (e.type != "uninstalled") {
                        SheetAction(stringResource(R.string.hist_open_app), onOpenApp)
                    }
                    SheetAction(stringResource(R.string.hist_app_history), onAppHistory)
                    Spacer(Modifier.size(4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerAppSheet(events: List<AppEvent>, onOpenApp: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val chrono = remember(events) { events.sortedBy { it.ts } }  // oldest first = lifecycle order
    val installed = events.maxByOrNull { it.ts }?.type != "uninstalled"
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    AppIcon(events.firstOrNull()?.packageName.orEmpty(), size = 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(events.firstOrNull()?.label.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.hist_events_n, events.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (installed) MiniBadge(stringResource(R.string.hist_installed_now), MaterialTheme.colorScheme.primary) else MiniBadge(stringResource(R.string.ev_uninstalled), MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
            }
            items(chrono, key = { it.id }) { e ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val (icon, tint) = eventVisual(e.type)
                    Box(modifier = Modifier.size(32.dp).background(tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(eventLabel(e.type) + versionSuffix(e), style = MaterialTheme.typography.bodyMedium)
                        Text(absTime(e.ts) + if (e.seeded) "  ·  " + stringResource(R.string.hist_seeded) else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                HorizontalDivider()
                if (installed) SheetAction(stringResource(R.string.hist_open_app), onOpenApp)
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun SheetAction(text: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).widthIn(min = 72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/* ---------- Export + About ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(rows: List<AppEvent>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun export(kind: String) {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(System.currentTimeMillis())
                    val file = java.io.File(context.cacheDir, "app-history-$stamp.${if (kind == "json") "jsonl" else "csv"}")
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
            Text(stringResource(R.string.hist_export_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.hist_export_body, rows.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExportRow("CSV") { export("csv") }
            ExportRow("JSON") { export("json") }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun ExportRow(title: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.IosShare, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.hist_help_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.hist_help_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.hist_help_seed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
        }
    }
}

/* ---------- shared bits ---------- */

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

@Composable
private fun eventVisual(type: String): Pair<ImageVector, Color> = when (type) {
    "installed" -> Icons.Default.Download to MaterialTheme.colorScheme.primary
    "updated" -> Icons.Default.Update to MaterialTheme.colorScheme.tertiary
    else -> Icons.Default.Delete to MaterialTheme.colorScheme.error
}

@Composable
private fun eventLabel(type: String): String = stringResource(
    when (type) { "installed" -> R.string.ev_installed; "updated" -> R.string.ev_updated; else -> R.string.ev_uninstalled },
)

/** " · v1.2" for a simple version, or " · v1.2 → v1.3" for an update with a known previous. */
private fun versionSuffix(e: AppEvent): String = when {
    e.type == "updated" && e.fromVersionCode > 0 -> "  ·  v${e.fromVersionName ?: e.fromVersionCode} → v${e.versionName ?: e.versionCode}"
    e.versionName != null -> "  ·  v${e.versionName}"
    else -> ""
}

private fun versionDetail(e: AppEvent): String? = when {
    e.type == "updated" && e.fromVersionCode > 0 -> "v${e.fromVersionName ?: e.fromVersionCode} → v${e.versionName ?: e.versionCode}"
    e.versionName != null -> "v${e.versionName} (${e.versionCode})"
    else -> null
}

private fun csvCell(s: String?): String {
    val v = s.orEmpty()
    return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

private fun rowsToCsv(rows: List<AppEvent>): String = buildString {
    appendLine("app,package,event,version,from_version,system,seeded,at_iso")
    for (r in rows) {
        append(csvCell(r.label)); append(','); append(csvCell(r.packageName)); append(','); append(r.type); append(',')
        append(csvCell(r.versionName ?: r.versionCode.toString())); append(','); append(csvCell(if (r.fromVersionCode > 0) (r.fromVersionName ?: r.fromVersionCode.toString()) else "")); append(',')
        append(if (r.system) "yes" else "no"); append(','); append(if (r.seeded) "yes" else "no"); append(','); appendLine(absTime(r.ts))
    }
}

private fun rowsToJsonl(rows: List<AppEvent>): String = buildString {
    for (r in rows) {
        append(org.json.JSONObject().put("app", r.label).put("package", r.packageName).put("event", r.type)
            .put("version", r.versionName ?: org.json.JSONObject.NULL).put("version_code", r.versionCode)
            .put("from_version", r.fromVersionName ?: org.json.JSONObject.NULL).put("from_version_code", r.fromVersionCode)
            .put("system", r.system).put("seeded", r.seeded).put("at_ms", r.ts).toString())
        append('\n')
    }
}

private val absFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
private fun absTime(ms: Long): String = absFmt.format(ms)
