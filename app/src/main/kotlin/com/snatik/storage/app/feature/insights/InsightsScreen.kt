package com.snatik.storage.app.feature.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.AppRepository
import com.snatik.storage.core.apps.DuplicateSet
import com.snatik.storage.core.apps.GhostFootprint
import com.snatik.storage.core.apps.InsightsEvent
import com.snatik.storage.core.apps.InsightsReport
import com.snatik.storage.core.apps.StorageInsights
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

class InsightsViewModel(private val insights: StorageInsights, private val apps: AppRepository) : ViewModel() {
    private val _report = MutableStateFlow<InsightsReport?>(null)
    val report: StateFlow<InsightsReport?> = _report.asStateFlow()
    private val _ghosts = MutableStateFlow<List<GhostFootprint>>(emptyList())
    val ghosts: StateFlow<List<GhostFootprint>> = _ghosts.asStateFlow()
    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress.asStateFlow()
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
}

private data class Root(val label: String, val path: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onBack: () -> Unit, onOpenPath: (String) -> Unit, viewModel: InsightsViewModel = koinViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val ghosts by viewModel.ghosts.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val roots = listOf(
        Root(stringResource(R.string.search_root_shared), "/storage/emulated/0"),
        Root(stringResource(R.string.search_root_system), "/"),
        Root(stringResource(R.string.search_root_data), "/data/data"),
    )
    var root by remember { mutableStateOf(roots.first()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                roots.forEach { r -> FilterChip(selected = root == r, onClick = { root = r }, label = { Text(r.label) }) }
            }
            Button(onClick = { viewModel.scan(root.path) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), enabled = progress == null) {
                Text(if (progress == null) stringResource(R.string.insights_scan) else progress!!)
            }
            if (progress != null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            val r = report
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    r == null && progress == null -> EmptyState(Icons.Default.Insights, stringResource(R.string.insights_prompt), stringResource(R.string.insights_prompt_body))
                    r == null -> {}
                    else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { SummaryCard(r) }
                        if (r.duplicateSets.isNotEmpty()) item { DuplicatesCard(r.duplicateSets, r.wastedByDuplicates, onOpenPath) }
                        if (ghosts.isNotEmpty()) item { GhostsCard(ghosts) }
                        if (r.zeroByteFiles.isNotEmpty()) item { ListCard(stringResource(R.string.insights_zero, r.zeroByteFiles.size), Icons.Default.DeleteOutline, r.zeroByteFiles.take(50), onOpenPath) }
                        if (r.emptyDirs.isNotEmpty()) item { ListCard(stringResource(R.string.insights_empty_dirs, r.emptyDirs.size), Icons.Default.Folder, r.emptyDirs.take(50), onOpenPath) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(r: InsightsReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.insights_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.insights_scanned, r.totalFiles, human(r.totalBytes)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.wastedByDuplicates > 0) Text(stringResource(R.string.insights_reclaimable, human(r.wastedByDuplicates)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DuplicatesCard(sets: List<DuplicateSet>, wasted: Long, onOpenPath: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.insights_dupes, sets.size, human(wasted)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            sets.take(20).forEach { set -> DuplicateRow(set, onOpenPath) }
        }
    }
}

@Composable
private fun DuplicateRow(set: DuplicateSet, onOpenPath: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(set.paths.first().substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.insights_dupe_copies, set.paths.size, human(set.size)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun GhostsCard(ghosts: List<GhostFootprint>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.insights_ghosts, ghosts.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.insights_ghosts_body), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ghosts.take(30).forEach { g ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(g.packageName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(human(g.bytes), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ListCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, paths: List<String>, onOpenPath: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            paths.forEach { p ->
                Text(p, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clickable { onOpenPath(p) }.padding(vertical = 2.dp))
            }
        }
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
