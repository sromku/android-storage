package com.snatik.storage.app.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.fullDateTime
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.capture.ChangeKind
import com.snatik.storage.core.capture.DiffLine
import com.snatik.storage.core.capture.DiffOp
import com.snatik.storage.core.capture.FileChange
import com.snatik.storage.core.capture.LineDiff
import com.snatik.storage.core.capture.SnapshotDiff
import com.snatik.storage.core.capture.SnapshotEntity
import com.snatik.storage.core.capture.SnapshotFileEntity
import com.snatik.storage.core.capture.SnapshotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

// Snapshot detail

data class SnapshotUiState(
    val snapshot: SnapshotEntity? = null,
    val files: List<SnapshotFileEntity> = emptyList(),
    val query: String = "",
    val others: List<SnapshotEntity> = emptyList(),
    val picking: Boolean = false,
    val loading: Boolean = true,
)

class SnapshotViewModel(private val id: Long, private val repo: SnapshotRepository) : ViewModel() {
    private val _state = MutableStateFlow(SnapshotUiState())
    val state: StateFlow<SnapshotUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val snap = repo.snapshot(id)
            val files = repo.files(id)
            val others = repo.snapshots.first().filter { it.id != id && it.rootPath == snap?.rootPath }
            _state.update { it.copy(snapshot = snap, files = files, others = others, loading = false) }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setPicking(p: Boolean) = _state.update { it.copy(picking = p) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotScreen(id: Long, onBack: () -> Unit, onCompare: (Long, Long) -> Unit, viewModel: SnapshotViewModel = koinViewModel(parameters = { parametersOf(id) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snap = state.snapshot
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(snap?.label ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(snap?.rootPath ?: "", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    TextButton(onClick = { viewModel.setPicking(true) }, enabled = state.others.isNotEmpty()) {
                        Icon(Icons.Default.Compare, contentDescription = null)
                        Text(stringResource(R.string.snapshot_compare), modifier = Modifier.padding(start = 6.dp))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (snap != null) {
                Text(
                    stringResource(R.string.snapshot_summary, snap.fileCount, snap.totalBytes.readableSize()) + "  ·  " + snap.createdAt.fullDateTime(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.snapshot_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val files = remember(state.files, state.query) { if (state.query.isBlank()) state.files else state.files.filter { it.path.contains(state.query, true) } }
            if (state.loading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(files, key = { it.path }) { f ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(f.path, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                            Text(f.lastModified.fullDateTime(context) + (f.hash?.let { "  ·  " + it.take(12) } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(f.size.readableSize(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (state.picking && snap != null) {
        AlertDialog(
            onDismissRequest = { viewModel.setPicking(false) },
            title = { Text(stringResource(R.string.snapshot_compare_title)) },
            text = {
                Column {
                    state.others.forEach { other ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.setPicking(false); onCompare(if (other.createdAt < snap.createdAt) other.id else snap.id, if (other.createdAt < snap.createdAt) snap.id else other.id) }.padding(vertical = 10.dp)) {
                            Column {
                                Text(other.label, style = MaterialTheme.typography.bodyLarge)
                                Text(other.createdAt.fullDateTime(context) + "  ·  " + stringResource(R.string.snapshot_summary, other.fileCount, other.totalBytes.readableSize()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.setPicking(false) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// Diff of two snapshots

data class SnapshotDiffUiState(val diff: SnapshotDiff? = null, val error: String? = null, val filter: ChangeKind? = null)

class SnapshotDiffViewModel(route: Route.SnapshotDiff, private val repo: SnapshotRepository) : ViewModel() {
    private val _state = MutableStateFlow(SnapshotDiffUiState())
    val state: StateFlow<SnapshotDiffUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _state.update { it.copy(diff = repo.diff(route.aId, route.bId)) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e.toString()) }
            }
        }
    }

    fun setFilter(kind: ChangeKind?) = _state.update { it.copy(filter = kind) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotDiffScreen(route: Route.SnapshotDiff, onBack: () -> Unit, onOpenFile: (String) -> Unit, viewModel: SnapshotDiffViewModel = koinViewModel(parameters = { parametersOf(route) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val diff = state.diff
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.diff_title), style = MaterialTheme.typography.titleMedium)
                        diff?.let { Text("${it.a.label} → ${it.b.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.query_failed), state.error)
                diff == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                diff.changes.isEmpty() -> EmptyState(Icons.Default.Check, stringResource(R.string.diff_none), stringResource(R.string.diff_unchanged, diff.unchanged))
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = state.filter == null, onClick = { viewModel.setFilter(null) }, label = { Text(stringResource(R.string.recording_filter_all) + " " + diff.changes.size) }) }
                        items(ChangeKind.entries.toList()) { kind ->
                            val count = diff.count(kind)
                            if (count > 0) FilterChip(selected = state.filter == kind, onClick = { viewModel.setFilter(kind) }, label = { Text(kind.label() + " " + count) })
                        }
                    }
                    Text(stringResource(R.string.diff_unchanged, diff.unchanged), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    val visible = remember(diff, state.filter) { diff.changes.filter { state.filter == null || it.kind == state.filter } }
                    val canTextDiff = diff.a.copied && diff.b.copied
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                        items(visible, key = { it.kind.name + it.path }) { change -> ChangeRow(change, clickable = canTextDiff && change.kind == ChangeKind.MODIFIED, onClick = { onOpenFile(change.path) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeKind.label(): String = when (this) {
    ChangeKind.ADDED -> stringResource(R.string.diff_added)
    ChangeKind.REMOVED -> stringResource(R.string.diff_removed)
    ChangeKind.MODIFIED -> stringResource(R.string.diff_modified)
    ChangeKind.MOVED -> stringResource(R.string.diff_moved)
}

@Composable
private fun ChangeKind.color() = when (this) {
    ChangeKind.ADDED -> MaterialTheme.colorScheme.primary
    ChangeKind.REMOVED -> MaterialTheme.colorScheme.error
    ChangeKind.MODIFIED -> MaterialTheme.colorScheme.tertiary
    ChangeKind.MOVED -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun ChangeRow(change: FileChange, clickable: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Tag(change.kind.label(), change.kind.color())
        Column(modifier = Modifier.weight(1f)) {
            Text(change.path, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            if (change.kind == ChangeKind.MOVED) Text("← " + change.before?.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        val delta = change.sizeDelta
        Text(
            when (change.kind) {
                ChangeKind.ADDED -> "+" + (change.after?.size ?: 0).readableSize()
                ChangeKind.REMOVED -> "−" + (change.before?.size ?: 0).readableSize()
                else -> (if (delta >= 0) "+" else "−") + kotlin.math.abs(delta).readableSize()
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Text diff of one file

data class FileDiffUiState(val lines: List<DiffLine>? = null, val error: String? = null)

class FileDiffViewModel(route: Route.FileDiff, private val repo: SnapshotRepository) : ViewModel() {
    val path = route.path
    private val _state = MutableStateFlow(FileDiffUiState())
    val state: StateFlow<FileDiffUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val before = repo.copyOf(route.aId, route.path)
            val after = repo.copyOf(route.bId, route.path)
            if (before == null || after == null) {
                _state.update { it.copy(error = "no-copies") }
                return@launch
            }
            val result = withContext(Dispatchers.Default) {
                val a = before.readBytes()
                val b = after.readBytes()
                if (looksBinary(a) || looksBinary(b)) null else LineDiff.diff(String(a), String(b))
            }
            _state.update { if (result == null) it.copy(error = "binary") else it.copy(lines = result) }
        }
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val sample = bytes.take(4096)
        return sample.isNotEmpty() && sample.count { it.toInt() == 0 } > 0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDiffScreen(route: Route.FileDiff, onBack: () -> Unit, viewModel: FileDiffViewModel = koinViewModel(parameters = { parametersOf(route) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var onlyChanges by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { FilterChip(selected = onlyChanges, onClick = { onlyChanges = !onlyChanges }, label = { Text(stringResource(R.string.diff_title)) }, modifier = Modifier.padding(end = 8.dp)) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val lines = state.lines
            when {
                state.error == "no-copies" -> EmptyState(Icons.Default.Block, stringResource(R.string.diff_no_copies), null)
                state.error == "binary" -> EmptyState(Icons.Default.Block, stringResource(R.string.diff_binary), null)
                lines == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> {
                    val added = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    val removed = MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                    val visible = remember(lines, onlyChanges) { if (onlyChanges) lines.filter { it.op != DiffOp.EQUAL } else lines }
                    SelectionContainer {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                            itemsIndexed(visible) { _, line ->
                                Row(modifier = Modifier.fillMaxWidth().background(when (line.op) { DiffOp.INSERT -> added; DiffOp.DELETE -> removed; DiffOp.EQUAL -> androidx.compose.ui.graphics.Color.Transparent })) {
                                    Text(line.oldLine?.toString() ?: "", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp).padding(start = 8.dp))
                                    Text(line.newLine?.toString() ?: "", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                                    Text(when (line.op) { DiffOp.INSERT -> "+"; DiffOp.DELETE -> "−"; DiffOp.EQUAL -> " " }, style = MonoStyle, modifier = Modifier.width(16.dp))
                                    Text(line.text, style = MonoStyle, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
