package com.snatik.storage.app.feature.monitor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.snatik.storage.core.apps.ExternalSink
import com.snatik.storage.core.apps.NotificationRecord
import com.snatik.storage.core.apps.NotificationRecorderStore
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

/** Where the monitor reads from: the live in-memory feed, or the persisted recorder history. */
enum class NotifSource { LIVE, RECORDED }

/** How the notifications are presented. */
enum class NotifView { OVERVIEW, TIMELINE, BY_APP, BY_CATEGORY }

class NotificationMonitorViewModel(
    private val context: Context,
    val store: NotificationRecorderStore,
    val sink: ExternalSink,
) : ViewModel() {

    private val _source = MutableStateFlow(NotifSource.LIVE)
    val source: StateFlow<NotifSource> = _source.asStateFlow()

    val recording: StateFlow<Boolean> = store.running
    val connected: StateFlow<Boolean> = NotificationLog.connected
    val recordedCount: StateFlow<Int> = store.count.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val oldest: StateFlow<Long?> = store.oldest.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The rows the screen shows, switched by [source]. */
    val entries: StateFlow<List<NotificationRecord>> =
        combine(_source, NotificationLog.entries, store.recent) { src, live, recorded ->
            if (src == NotifSource.RECORDED) recorded else live
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // If a recording is running or already holds data, open on it.
        viewModelScope.launch {
            if (store.running.value || store.snapshot(1).isNotEmpty()) _source.value = NotifSource.RECORDED
        }
    }

    fun setSource(s: NotifSource) { _source.value = s }

    fun startRecording(capacity: Int) {
        store.setCapacity(capacity)
        viewModelScope.launch { store.applyCapacity() }
        store.setRunning(true)
        _source.value = NotifSource.RECORDED
    }

    fun stopRecording() = store.setRunning(false)

    fun clearRecording() { viewModelScope.launch { store.clear() } }

    fun hasAccess(context: Context) = NotificationLog.hasAccess(context)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationMonitorScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit, viewModel: NotificationMonitorViewModel = koinViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val recordedCount by viewModel.recordedCount.collectAsStateWithLifecycle()
    val oldest by viewModel.oldest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasAccess = remember { viewModel.hasAccess(context) }

    var query by rememberSaveable { mutableStateOf("") }
    var ongoingOnly by rememberSaveable { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showRecord by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var view by rememberSaveable { mutableStateOf(NotifView.OVERVIEW) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val filterActive = query.isNotBlank() || ongoingOnly || categoryFilter != null

    val visible = remember(entries, query, ongoingOnly, categoryFilter) {
        val q = query.trim().lowercase()
        entries.filter {
            (!ongoingOnly || it.ongoing) &&
                (categoryFilter == null || (it.category ?: UNCATEGORIZED) == categoryFilter) &&
                (q.isBlank() || it.title.lowercase().contains(q) || it.text.lowercase().contains(q) ||
                    it.packageName.lowercase().contains(q) || it.label.lowercase().contains(q))
        }
    }
    val categories = remember(entries) { entries.map { it.category ?: UNCATEGORIZED }.distinct().sorted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notif_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { showExport = true }, enabled = visible.isNotEmpty()) {
                        Icon(Icons.Default.IosShare, contentDescription = stringResource(R.string.notif_export))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.notif_menu), tint = if (filterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notif_filter)) },
                                onClick = { showMenu = false; showFilters = true },
                                leadingIcon = { Icon(Icons.Default.FilterAlt, contentDescription = null, tint = if (filterActive) MaterialTheme.colorScheme.primary else LocalContentColor.current) },
                                trailingIcon = if (filterActive) { { Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))) } } else null,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notif_test)) },
                                onClick = {
                                    showMenu = false
                                    TestNotification.send(context)
                                    viewModel.setSource(NotifSource.LIVE)
                                    view = NotifView.TIMELINE
                                    android.widget.Toast.makeText(context, context.getString(R.string.notif_test_sent), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = { Icon(Icons.Outlined.NotificationAdd, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notif_help)) },
                                onClick = { showMenu = false; showHelp = true },
                                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!hasAccess) {
                EmptyState(
                    Icons.Default.NotificationsActive,
                    stringResource(R.string.notif_needs_access),
                    stringResource(R.string.notif_needs_access_body),
                    action = { Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text(stringResource(R.string.notif_grant)) } },
                )
                return@Column
            }
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
                ViewChip(stringResource(R.string.notif_view_overview), view == NotifView.OVERVIEW) { view = NotifView.OVERVIEW }
                ViewChip(stringResource(R.string.notif_view_timeline), view == NotifView.TIMELINE) { view = NotifView.TIMELINE }
                ViewChip(stringResource(R.string.notif_view_apps), view == NotifView.BY_APP) { view = NotifView.BY_APP }
                ViewChip(stringResource(R.string.notif_view_categories), view == NotifView.BY_CATEGORY) { view = NotifView.BY_CATEGORY }
            }
            HorizontalDivider()
            Box(modifier = Modifier.fillMaxSize()) {
                if (visible.isEmpty()) {
                    val (title, body) = when {
                        source == NotifSource.RECORDED && recordedCount == 0 -> stringResource(R.string.notif_empty_recorded) to null
                        filterActive -> stringResource(R.string.notif_empty_filtered) to null
                        else -> stringResource(R.string.notif_empty) to stringResource(R.string.notif_empty_body)
                    }
                    EmptyState(if (filterActive) Icons.Default.FilterAlt else Icons.Default.NotificationsActive, title, body)
                } else when (view) {
                    NotifView.TIMELINE -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(visible, key = { it.key }) { e -> NotificationRow(e) { selectedKey = e.key } }
                    }
                    NotifView.BY_APP -> ByAppView(visible, onOpenApp)
                    NotifView.BY_CATEGORY -> ByCategoryView(visible)
                    NotifView.OVERVIEW -> OverviewView(visible, onOpenApp)
                }
            }
        }
    }

    val sel = selectedKey?.let { key -> entries.firstOrNull { it.key == key } }
    if (sel != null) {
        NotificationDetailSheet(sel, onOpenApp = { onOpenApp(it) }, onDismiss = { selectedKey = null })
    }
    if (showFilters) {
        FilterSheet(
            query = query,
            ongoingOnly = ongoingOnly,
            categoryFilter = categoryFilter,
            categories = categories,
            onQuery = { query = it },
            onOngoingChange = { ongoingOnly = it },
            onCategoryChange = { categoryFilter = it },
            onDismiss = { showFilters = false },
        )
    }
    if (showRecord) {
        RecordSheet(
            store = viewModel.store,
            sink = viewModel.sink,
            onStart = { cap -> viewModel.startRecording(cap); showRecord = false },
            onDismiss = { showRecord = false },
        )
    }
    if (showExport) ExportSheet(rows = visible, onDismiss = { showExport = false })
    if (showHelp) HelpSheet(onDismiss = { showHelp = false })
}

private const val UNCATEGORIZED = "uncategorized"

@Composable
private fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SourceBar(
    source: NotifSource,
    recording: Boolean,
    recordedCount: Int,
    oldest: Long?,
    onSource: (NotifSource) -> Unit,
    onRecordClick: () -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(selected = source == NotifSource.LIVE, onClick = { onSource(NotifSource.LIVE) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(stringResource(R.string.notif_source_live))
                }
                SegmentedButton(selected = source == NotifSource.RECORDED, onClick = { onSource(NotifSource.RECORDED) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(stringResource(R.string.notif_source_recorded))
                }
            }
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
        if (source == NotifSource.RECORDED && recordedCount > 0) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val since = oldest?.let { humanAgo((System.currentTimeMillis() - it).coerceAtLeast(0)) }
                Text(
                    if (since != null) stringResource(R.string.notif_recorded_stats, recordedCount, since) else stringResource(R.string.notif_recorded_count, recordedCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.notif_clear), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun NotificationRow(r: NotificationRecord, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(r.packageName, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    r.title.ifEmpty { r.display() },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (r.ongoing) MiniBadge(stringResource(R.string.notif_tag_ongoing), MaterialTheme.colorScheme.tertiary)
                r.category?.let { CategoryTag(it) }
            }
            if (r.text.isNotEmpty()) Text(r.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${r.display()} · ${humanTime(r.postedAt)}", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CategoryTag(category: String) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        prettyCategory(category),
        style = MaterialTheme.typography.labelSmall.copy(color = color),
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
    )
}

/* ---------- Detail sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationDetailSheet(r: NotificationRecord, onOpenApp: (String) -> Unit, onDismiss: () -> Unit) {
    val images = remember(r.key) { NotificationImageCache.get(r.key) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val largeIcon = images?.largeIcon
                if (largeIcon != null) {
                    Image(bitmap = largeIcon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                } else {
                    AppIcon(r.packageName, size = 40.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.display(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.packageName, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
                if (r.ongoing) MiniBadge(stringResource(R.string.notif_tag_ongoing), MaterialTheme.colorScheme.tertiary)
            }
            // Big-picture attachment, when present and still cached.
            images?.bigPicture?.let { pic ->
                Image(
                    bitmap = pic.asImageBitmap(),
                    contentDescription = stringResource(R.string.notif_detail_picture),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.FillWidth,
                )
            }
            HorizontalDivider()
            if (r.title.isNotEmpty()) SelectionContainer { DetailRow(stringResource(R.string.notif_detail_title_label), r.title) }
            if (r.subText.isNotEmpty()) SelectionContainer { DetailRow(stringResource(R.string.notif_detail_subtext), r.subText) }
            // Prefer the expanded big text as the body when it carries more than the collapsed text.
            val body = if (r.bigText.isNotEmpty()) r.bigText else r.text
            if (body.isNotEmpty()) SelectionContainer { DetailRow(stringResource(R.string.notif_detail_text), body) }
            if (r.summaryText.isNotEmpty()) SelectionContainer { DetailRow(stringResource(R.string.notif_detail_summary), r.summaryText) }
            if (r.infoText.isNotEmpty()) SelectionContainer { DetailRow(stringResource(R.string.notif_detail_info), r.infoText) }
            if (r.actions.isNotEmpty()) DetailRow(stringResource(R.string.notif_detail_actions), r.actions)
            if (r.progress.isNotEmpty()) DetailRow(stringResource(R.string.notif_detail_progress), r.progress)
            DetailRow(stringResource(R.string.notif_detail_category), r.category?.let { prettyCategory(it) } ?: stringResource(R.string.notif_uncategorized))
            if (r.channelId.isNotEmpty()) SelectionContainer { DetailRow(stringResource(R.string.notif_detail_channel), r.channelId) }
            if ((r.hasLargeIcon || r.hasBigPicture) && images == null) {
                DetailRow(stringResource(R.string.notif_detail_media), stringResource(R.string.notif_detail_media_uncached))
            }
            DetailRow(stringResource(R.string.notif_detail_when), humanTime(r.postedAt))
            SelectionContainer { DetailRow(stringResource(R.string.notif_detail_exact), absTime(r.postedAt)) }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenApp(r.packageName) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.notif_open_app, r.display()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).widthIn(min = 84.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(1f))
    }
}

/* ---------- Aggregate views ---------- */

private data class AppAgg(val pkg: String, val label: String, val total: Int, val ongoing: Int, val categories: Int)
private data class CatAgg(val category: String, val total: Int, val apps: Int)

@Composable
private fun ByAppView(rows: List<NotificationRecord>, onOpenApp: (String) -> Unit) {
    val apps = remember(rows) {
        rows.groupBy { it.packageName }.map { (pkg, list) ->
            AppAgg(pkg, list.first().display(), list.size, list.count { it.ongoing }, list.mapNotNull { it.category }.distinct().size)
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
                    if (a.ongoing > 0) MiniBadge(stringResource(R.string.notif_agg_ongoing, a.ongoing), MaterialTheme.colorScheme.tertiary)
                    if (a.categories > 0) MiniBadge(stringResource(R.string.notif_agg_categories, a.categories), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun ByCategoryView(rows: List<NotificationRecord>) {
    val cats = remember(rows) {
        rows.groupBy { it.category ?: UNCATEGORIZED }.map { (cat, list) ->
            CatAgg(cat, list.size, list.map { it.packageName }.distinct().size)
        }.sortedByDescending { it.total }
    }
    val max = remember(cats) { (cats.firstOrNull()?.total ?: 1).coerceAtLeast(1) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(cats, key = { it.category }) { c ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(prettyCategory(c.category), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.notif_agg_apps, c.apps), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(c.total.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(2.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(c.total.toFloat() / max).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun OverviewView(rows: List<NotificationRecord>, onOpenApp: (String) -> Unit) {
    val total = rows.size
    val apps = remember(rows) { rows.map { it.packageName }.distinct().size }
    val ongoing = remember(rows) { rows.count { it.ongoing } }
    val categories = remember(rows) { rows.map { it.category ?: UNCATEGORIZED }.distinct().size }
    val topApps = remember(rows) {
        rows.groupBy { it.packageName }.map { (p, l) -> AppAgg(p, l.first().display(), l.size, l.count { it.ongoing }, 0) }
            .sortedByDescending { it.total }.take(5)
    }
    val topCats = remember(rows) {
        rows.groupBy { it.category ?: UNCATEGORIZED }.map { (c, l) -> CatAgg(c, l.size, l.map { it.packageName }.distinct().size) }
            .sortedByDescending { it.total }.take(6)
    }
    val buckets = remember(rows) { hourlyBuckets(rows) }
    val appMax = (topApps.firstOrNull()?.total ?: 1).coerceAtLeast(1)
    val catMax = (topCats.firstOrNull()?.total ?: 1).coerceAtLeast(1)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(total.toString(), stringResource(R.string.notif_stat_total), Modifier.weight(1f))
                StatTile(apps.toString(), stringResource(R.string.notif_stat_apps), Modifier.weight(1f))
                StatTile(ongoing.toString(), stringResource(R.string.notif_stat_ongoing), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                StatTile(categories.toString(), stringResource(R.string.notif_stat_categories), Modifier.weight(1f))
            }
        }
        if (buckets.any { it > 0 }) {
            item { SectionHeader(stringResource(R.string.notif_over_time)) }
            item { HourlyChart(buckets, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        item { SectionHeader(stringResource(R.string.notif_top_apps)) }
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
        if (topCats.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.notif_top_categories)) }
            items(topCats, key = { "c:" + it.category }) { c ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(prettyCategory(c.category), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 4.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(2.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(c.total.toFloat() / catMax).height(4.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp)))
                        }
                    }
                    Text(c.total.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

/** 24 one-hour buckets ending now; value = number of notifications in that hour. */
private fun hourlyBuckets(rows: List<NotificationRecord>): IntArray {
    val out = IntArray(24)
    val now = System.currentTimeMillis()
    val hourMs = 3_600_000L
    for (r in rows) {
        val h = ((now - r.postedAt).coerceAtLeast(0) / hourMs).toInt()
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
            Text(stringResource(R.string.notif_chart_24h), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.notif_chart_now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun ExportSheet(rows: List<NotificationRecord>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun export(kind: String) {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(System.currentTimeMillis())
                    val file = java.io.File(context.cacheDir, "notifications-$stamp.${if (kind == "json") "jsonl" else if (kind == "csv") "csv" else "txt"}")
                    file.writeText(when (kind) { "csv" -> rowsToCsv(rows); "json" -> rowsToJsonl(rows); else -> rowsToReport(rows) })
                    file.absolutePath
                }.getOrNull()
            }
            if (path != null) com.snatik.storage.app.util.Intents.share(context, listOf(path))
            onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.notif_export_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.notif_export_body, rows.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExportRow("CSV", stringResource(R.string.notif_export_csv)) { export("csv") }
            ExportRow("JSON", stringResource(R.string.notif_export_json)) { export("json") }
            ExportRow(stringResource(R.string.notif_export_report_label), stringResource(R.string.notif_export_report)) { export("report") }
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

private fun rowsToCsv(rows: List<NotificationRecord>): String = buildString {
    appendLine("app,package,title,text,category,channel,ongoing,at_iso")
    for (r in rows) {
        append(csvCell(r.display())); append(',')
        append(csvCell(r.packageName)); append(',')
        append(csvCell(r.title)); append(',')
        append(csvCell(r.text)); append(',')
        append(csvCell(r.category ?: UNCATEGORIZED)); append(',')
        append(csvCell(r.channelId)); append(',')
        append(if (r.ongoing) "yes" else "no"); append(',')
        appendLine(absTime(r.postedAt))
    }
}

private fun rowsToJsonl(rows: List<NotificationRecord>): String = buildString {
    for (r in rows) {
        append(
            org.json.JSONObject()
                .put("app", r.display()).put("package", r.packageName)
                .put("title", r.title).put("text", r.text)
                .put("category", r.category ?: org.json.JSONObject.NULL)
                .put("channel", r.channelId).put("ongoing", r.ongoing)
                .put("at_ms", r.postedAt).toString(),
        )
        append('\n')
    }
}

private fun rowsToReport(rows: List<NotificationRecord>): String = buildString {
    appendLine("Notification report — ${rows.size} notifications")
    appendLine("Apps: ${rows.map { it.packageName }.distinct().size}   Ongoing: ${rows.count { it.ongoing }}   Categories: ${rows.map { it.category ?: UNCATEGORIZED }.distinct().size}")
    appendLine()
    appendLine("Top apps:")
    rows.groupBy { it.packageName }.map { (_, l) -> l.first().display() to l.size }.sortedByDescending { it.second }.take(15).forEach {
        appendLine("  ${it.second.toString().padStart(5)}  ${it.first}")
    }
    appendLine()
    appendLine("Top categories:")
    rows.groupBy { it.category ?: UNCATEGORIZED }.map { it.key to it.value.size }.sortedByDescending { it.second }.take(15).forEach {
        appendLine("  ${it.second.toString().padStart(5)}  ${prettyCategory(it.first)}")
    }
}

/* ---------- Filter sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    query: String,
    ongoingOnly: Boolean,
    categoryFilter: String?,
    categories: List<String>,
    onQuery: (String) -> Unit,
    onOngoingChange: (Boolean) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.notif_filter_title), style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.notif_search_hint)) },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.notif_ongoing_only), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.notif_ongoing_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = ongoingOnly, onCheckedChange = onOngoingChange)
            }

            if (categories.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.notif_filter_category), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StateChip(stringResource(R.string.notif_filter_any), categoryFilter == null) { onCategoryChange(null) }
                    categories.forEach { c ->
                        StateChip(prettyCategory(c), categoryFilter == c) { onCategoryChange(c) }
                    }
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StateChip(label: String, selected: Boolean, accent: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.16f), selectedLabelColor = accent),
    )
}

/* ---------- Record start sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordSheet(store: NotificationRecorderStore, sink: ExternalSink, onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    val savedCap by store.capacity.collectAsStateWithLifecycle()
    var cap by remember { mutableStateOf(savedCap) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.notif_record_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.notif_record_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.recorder_capacity), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                NotificationRecorderStore.CAPACITIES.forEachIndexed { i, c ->
                    SegmentedButton(selected = cap == c, onClick = { cap = c }, shape = SegmentedButtonDefaults.itemShape(i, NotificationRecorderStore.CAPACITIES.size)) {
                        Text("%,d".format(c))
                    }
                }
            }
            com.snatik.storage.app.ui.components.ExternalSinkOption(sink)

            Button(onClick = { onStart(cap) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.recorder_start_button), modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

/* ---------- Help sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.notif_help_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.notif_help_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.notif_help_live_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.notif_help_live), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.notif_help_categories_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.notif_help_categories), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.notif_help_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
        }
    }
}

/* ---------- helpers ---------- */

private fun NotificationRecord.display(): String = label.ifEmpty { packageName }

/** Turn a raw Notification.category constant into a human label. */
private fun prettyCategory(raw: String): String = when (raw) {
    "msg" -> "Message"
    "email" -> "Email"
    "call" -> "Call"
    "missed_call" -> "Missed call"
    "voicemail" -> "Voicemail"
    "social" -> "Social"
    "event" -> "Event"
    "reminder" -> "Reminder"
    "alarm" -> "Alarm"
    "progress" -> "Progress"
    "transport" -> "Media"
    "promo" -> "Promotion"
    "recommendation" -> "Recommendation"
    "status" -> "Status"
    "service" -> "Service"
    "sys" -> "System"
    "err" -> "Error"
    "navigation" -> "Navigation"
    "stopwatch" -> "Stopwatch"
    "workout" -> "Workout"
    "health" -> "Health"
    "location_sharing" -> "Location"
    "account" -> "Account"
    UNCATEGORIZED -> "Uncategorized"
    else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private val absFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
private fun absTime(ms: Long): String = absFmt.format(ms)

private fun humanTime(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when { s < 5 -> "now"; s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60}m ago"; s < 86400 -> "${s / 3600}h ago"; else -> "${s / 86400}d ago" }
}

private fun humanAgo(ms: Long): String {
    val s = ms / 1000
    return when { s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60}m ago"; s < 86400 -> "${s / 3600}h ago"; else -> "${s / 86400}d ago" }
}
