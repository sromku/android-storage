package com.snatik.storage.app.feature.insights

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.AppRepository
import com.snatik.storage.core.apps.CategoryStat
import com.snatik.storage.core.apps.DuplicateSet
import com.snatik.storage.core.apps.FileCategory
import com.snatik.storage.core.apps.GhostFootprint
import com.snatik.storage.core.apps.InsightsEvent
import com.snatik.storage.core.apps.InsightsReport
import com.snatik.storage.core.apps.StorageInsights
import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.fs.LargeFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

class InsightsViewModel(
    private val insights: StorageInsights,
    private val apps: AppRepository,
    private val fs: FileSystem,
) : ViewModel() {
    private val _report = MutableStateFlow<InsightsReport?>(null)
    val report: StateFlow<InsightsReport?> = _report.asStateFlow()
    private val _ghosts = MutableStateFlow<List<GhostFootprint>>(emptyList())
    val ghosts: StateFlow<List<GhostFootprint>> = _ghosts.asStateFlow()
    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _freed = MutableStateFlow<Long?>(null)
    val freed: StateFlow<Long?> = _freed.asStateFlow()
    private var job: Job? = null

    fun scan(root: String) {
        job?.cancel()
        _report.value = null
        _ghosts.value = emptyList()
        _progress.value = "Scanning"
        job = viewModelScope.launch {
            val installed = apps.list().map { it.packageName }.toSet()
            _ghosts.value = insights.ghosts(installed)
            insights.scan(root).collect { ev ->
                when (ev) {
                    is InsightsEvent.Progress -> _progress.value = "${ev.phase}… ${ev.filesSeen}"
                    is InsightsEvent.Done -> { _report.value = ev.report; _progress.value = null }
                }
            }
        }
    }

    /** Drain the delete flow to completion, reporting how much was freed. */
    private fun delete(paths: List<String>, freedBytes: Long, onDone: () -> Unit) {
        if (paths.isEmpty() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            runCatching { fs.delete(paths).collect { } }
            onDone()
            _freed.value = freedBytes
            _busy.value = false
        }
    }

    fun deleteDuplicateSet(set: DuplicateSet) {
        val extras = set.paths.drop(1)  // keep the first copy
        delete(extras, set.wasted) {
            val r = _report.value ?: return@delete
            val remaining = r.duplicateSets.filterNot { it.hash == set.hash && it.size == set.size }
            _report.value = r.copy(duplicateSets = remaining, wastedByDuplicates = remaining.sumOf { it.wasted })
        }
    }

    fun deleteAllDuplicates() {
        val r = _report.value ?: return
        val extras = r.duplicateSets.flatMap { it.paths.drop(1) }
        delete(extras, r.wastedByDuplicates) {
            _report.value = _report.value?.copy(duplicateSets = emptyList(), wastedByDuplicates = 0)
        }
    }

    fun deleteZeroByte() {
        val r = _report.value ?: return
        delete(r.zeroByteFiles, 0) { _report.value = _report.value?.copy(zeroByteFiles = emptyList()) }
    }

    fun deleteEmptyDirs() {
        val r = _report.value ?: return
        delete(r.emptyDirs, 0) { _report.value = _report.value?.copy(emptyDirs = emptyList()) }
    }

    fun deleteGhost(g: GhostFootprint) {
        delete(listOf(g.path), g.bytes) { _ghosts.value = _ghosts.value.filterNot { it.path == g.path } }
    }

    fun deleteLargest(file: LargeFile) {
        delete(listOf(file.path), file.size) {
            _report.value = _report.value?.let { it.copy(largest = it.largest.filterNot { l -> l.path == file.path }) }
        }
    }

    fun clearFreed() { _freed.value = null }
}

private data class Root(val label: String, val path: String)
/** A pending destructive action: the confirm dialog resolves [messageId] with [args]. */
private data class Confirm(val messageId: Int, val args: List<Any>, val onConfirm: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onBack: () -> Unit, onOpenPath: (String) -> Unit, viewModel: InsightsViewModel = koinViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val ghosts by viewModel.ghosts.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val freed by viewModel.freed.collectAsStateWithLifecycle()
    val roots = listOf(
        Root(stringResource(R.string.search_root_shared), "/storage/emulated/0"),
        Root(stringResource(R.string.search_root_system), "/"),
        Root(stringResource(R.string.search_root_data), "/data/data"),
    )
    var root by remember { mutableStateOf(roots.first()) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val freedMsg = freed?.let { stringResource(R.string.insights_freed, human(it)) }
    LaunchedEffect(freed) {
        if (freedMsg != null) { snackbar.showSnackbar(freedMsg); viewModel.clearFreed() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                roots.forEach { r -> FilterChip(selected = root == r, onClick = { root = r }, label = { Text(r.label) }, enabled = progress == null) }
            }
            Button(onClick = { viewModel.scan(root.path) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), enabled = progress == null && !busy) {
                Text(if (progress == null) stringResource(R.string.insights_scan) else progress!!)
            }
            if (progress != null || busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            val r = report
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    r == null && progress == null -> EmptyState(Icons.Default.Insights, stringResource(R.string.insights_prompt), stringResource(R.string.insights_prompt_body))
                    r == null -> {}
                    else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { ReclaimCard(r, ghosts) }
                        if (r.categories.isNotEmpty()) item { CategoryCard(r.categories, r.totalBytes) }
                        if (r.duplicateSets.isNotEmpty()) item {
                            DuplicatesCard(
                                r.duplicateSets, r.wastedByDuplicates, busy, onOpenPath,
                                onDeleteAll = { confirm = Confirm(R.string.insights_confirm_dupes, listOf(r.duplicateSets.sumOf { it.paths.size - 1 }, human(r.wastedByDuplicates))) { viewModel.deleteAllDuplicates() } },
                                onDeleteSet = { s -> confirm = Confirm(R.string.insights_confirm_dupes, listOf(s.paths.size - 1, human(s.wasted))) { viewModel.deleteDuplicateSet(s) } },
                            )
                        }
                        if (ghosts.isNotEmpty()) item {
                            GhostsCard(ghosts, busy) { g -> confirm = Confirm(R.string.insights_confirm_ghost, listOf(g.packageName, human(g.bytes))) { viewModel.deleteGhost(g) } }
                        }
                        if (r.largest.isNotEmpty()) item {
                            LargestCard(r.largest, busy, onOpenPath) { f -> confirm = Confirm(R.string.insights_confirm_file, listOf(f.path.substringAfterLast('/'))) { viewModel.deleteLargest(f) } }
                        }
                        if (r.zeroByteFiles.isNotEmpty()) item {
                            ListCard(
                                stringResource(R.string.insights_zero, r.zeroByteFiles.size), Icons.Default.DeleteOutline, r.zeroByteFiles.take(50), busy, onOpenPath,
                                onDeleteAll = { confirm = Confirm(R.string.insights_confirm_zero, listOf(r.zeroByteFiles.size)) { viewModel.deleteZeroByte() } },
                            )
                        }
                        if (r.emptyDirs.isNotEmpty()) item {
                            ListCard(
                                stringResource(R.string.insights_empty_dirs, r.emptyDirs.size), Icons.Default.Folder, r.emptyDirs.take(50), busy, onOpenPath,
                                onDeleteAll = { confirm = Confirm(R.string.insights_confirm_empty, listOf(r.emptyDirs.size)) { viewModel.deleteEmptyDirs() } },
                            )
                        }
                    }
                }
            }
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.insights_confirm_title)) },
            text = { Text(stringResource(c.messageId, *c.args.toTypedArray())) },
            confirmButton = { TextButton(onClick = { c.onConfirm(); confirm = null }) { Text(stringResource(R.string.insights_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/* ---------- reclaim hero ---------- */

@Composable
private fun ReclaimCard(r: InsightsReport, ghosts: List<GhostFootprint>) {
    val ghostBytes = ghosts.sumOf { it.bytes }
    val reclaimable = r.wastedByDuplicates + ghostBytes
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.insights_reclaim_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (reclaimable > 0) {
                Text(human(reclaimable), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (r.wastedByDuplicates > 0) Text(stringResource(R.string.insights_reclaim_from_dupes, human(r.wastedByDuplicates), r.duplicateSets.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (ghostBytes > 0) Text(stringResource(R.string.insights_reclaim_from_ghosts, human(ghostBytes), ghosts.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                Text(stringResource(R.string.insights_reclaim_none), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.insights_scanned, r.totalFiles, human(r.totalBytes)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}

/* ---------- file-type breakdown ---------- */

@Composable
private fun CategoryCard(cats: List<CategoryStat>, totalBytes: Long) {
    val total = cats.sumOf { it.bytes }.coerceAtLeast(1)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.insights_breakdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(6.dp))) {
                cats.forEach { c ->
                    if (c.bytes > 0) Box(modifier = Modifier.weight((c.bytes.toDouble() / total).toFloat().coerceAtLeast(0.003f)).fillMaxHeight().background(catColor(c.category)))
                }
            }
            cats.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(catColor(c.category)))
                    Spacer(Modifier.width(8.dp))
                    Text(catLabel(c.category), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.insights_cat_count, c.count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(human(c.bytes), style = MonoStyle, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/* ---------- duplicates ---------- */

@Composable
private fun DuplicatesCard(sets: List<DuplicateSet>, wasted: Long, busy: Boolean, onOpenPath: (String) -> Unit, onDeleteAll: () -> Unit, onDeleteSet: (DuplicateSet) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.insights_dupes, sets.size, human(wasted)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onDeleteAll, enabled = !busy) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.insights_clean_dupes, human(wasted)), color = MaterialTheme.colorScheme.error)
            }
            sets.take(20).forEach { set -> DuplicateRow(set, busy, onOpenPath, onDeleteSet) }
        }
    }
}

@Composable
private fun DuplicateRow(set: DuplicateSet, busy: Boolean, onOpenPath: (String) -> Unit, onDeleteSet: (DuplicateSet) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(set.paths.first().substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.insights_dupe_copies, set.paths.size, human(set.size)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onDeleteSet(set) }, enabled = !busy) {
                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.insights_delete), tint = MaterialTheme.colorScheme.error)
            }
            Icon(Icons.Default.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(expanded) {
            Column(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                set.paths.forEach { p ->
                    Text(p, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clickable { onOpenPath(p) }.padding(vertical = 2.dp))
                }
            }
        }
    }
}

/* ---------- ghosts ---------- */

@Composable
private fun GhostsCard(ghosts: List<GhostFootprint>, busy: Boolean, onDelete: (GhostFootprint) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.insights_ghosts, ghosts.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.insights_ghosts_body), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            ghosts.take(30).forEach { g ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(g.packageName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(human(g.bytes), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { onDelete(g) }, enabled = !busy) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.insights_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/* ---------- largest ---------- */

@Composable
private fun LargestCard(largest: List<LargeFile>, busy: Boolean, onOpenPath: (String) -> Unit, onDelete: (LargeFile) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.insights_largest), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
            largest.take(15).forEach { f ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f).clickable { onOpenPath(f.path) }.padding(vertical = 4.dp)) {
                        Text(f.path.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(human(f.size), style = MonoStyle, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { onDelete(f) }, enabled = !busy) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.insights_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/* ---------- generic list card with a "delete all" action ---------- */

@Composable
private fun ListCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, paths: List<String>, busy: Boolean, onOpenPath: (String) -> Unit, onDeleteAll: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDeleteAll, enabled = !busy) {
                    Text(stringResource(R.string.insights_delete_all_short), color = MaterialTheme.colorScheme.error)
                }
            }
            paths.forEach { p ->
                Text(p, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clickable { onOpenPath(p) }.padding(vertical = 2.dp))
            }
        }
    }
}

/* ---------- helpers ---------- */

@Composable
private fun catColor(cat: FileCategory): Color = when (cat) {
    FileCategory.IMAGES -> MaterialTheme.colorScheme.primary
    FileCategory.VIDEO -> MaterialTheme.colorScheme.tertiary
    FileCategory.AUDIO -> MaterialTheme.colorScheme.secondary
    FileCategory.APK -> MaterialTheme.colorScheme.error
    FileCategory.ARCHIVE -> MaterialTheme.colorScheme.primaryContainer
    FileCategory.DOCUMENT -> MaterialTheme.colorScheme.tertiaryContainer
    FileCategory.CODE -> MaterialTheme.colorScheme.secondaryContainer
    FileCategory.OTHER -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun catLabel(cat: FileCategory): String = stringResource(
    when (cat) {
        FileCategory.IMAGES -> R.string.insights_cat_images
        FileCategory.VIDEO -> R.string.insights_cat_video
        FileCategory.AUDIO -> R.string.insights_cat_audio
        FileCategory.APK -> R.string.insights_cat_apk
        FileCategory.ARCHIVE -> R.string.insights_cat_archive
        FileCategory.DOCUMENT -> R.string.insights_cat_document
        FileCategory.CODE -> R.string.insights_cat_code
        FileCategory.OTHER -> R.string.insights_cat_other
    }
)

private fun human(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return "${(v * 10).roundToInt() / 10.0} ${units[i]}"
}
