package com.snatik.storage.app.feature.capture

import android.app.DownloadManager
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.fullDateTime
import com.snatik.storage.core.capture.RecordSource
import com.snatik.storage.core.capture.RecordingEngine
import com.snatik.storage.core.capture.RecordingEntity
import com.snatik.storage.core.capture.RecordingEventEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingUiState(
    val recording: RecordingEntity? = null,
    val events: List<RecordingEventEntity> = emptyList(),
    val loading: Boolean = true,
    val source: RecordSource? = null,
    val query: String = "",
    val exporting: Boolean = false,
    val exportedZip: String? = null,
)

class RecordingViewModel(private val id: Long, private val engine: RecordingEngine) : ViewModel() {
    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Refresh while the session is still running so the timeline grows live.
            while (true) {
                val rec = engine.recording(id)
                _state.update { it.copy(recording = rec, events = engine.events(id), loading = false) }
                if (rec?.endedAt != null) break
                delay(1500)
            }
        }
    }

    fun setSource(source: RecordSource?) = _state.update { it.copy(source = source) }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun export() {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                val zip = engine.export(id, File("/storage/emulated/0/Download"))
                _state.update { it.copy(exporting = false, exportedZip = zip.absolutePath) }
            } catch (e: Exception) {
                _state.update { it.copy(exporting = false) }
                _messages.send(e.message ?: e.toString())
            }
        }
    }

    fun dismissExport() = _state.update { it.copy(exportedZip = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(id: Long, onBack: () -> Unit, viewModel: RecordingViewModel = koinViewModel(parameters = { parametersOf(id) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, resources) {
        viewModel.messages.collect { m -> snackbar.showSnackbar(m) }
    }
    val rec = state.recording
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(rec?.name ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        rec?.let {
                            Text(
                                it.startedAt.fullDateTime(context) + "  ·  " + (if (it.endedAt == null) stringResource(R.string.recording_in_progress) else stringResource(R.string.recording_events, state.events.size)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    rec?.videoPath?.let { path ->
                        IconButton(onClick = {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", File(path))
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }) { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.recording_video)) }
                    }
                    if (state.exporting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    }
                    var more by remember { mutableStateOf(false) }
                    IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.recording_export)) }, onClick = { more = false; viewModel.export() })
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val sources = remember(rec) { rec?.sources?.split(',')?.filter { it.isNotBlank() }?.map { RecordSource.valueOf(it) }.orEmpty() }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = state.source == null, onClick = { viewModel.setSource(null) }, label = { Text(stringResource(R.string.recording_filter_all)) }) }
                items(sources.filter { it != RecordSource.SCREEN }) { s ->
                    FilterChip(selected = state.source == s, onClick = { viewModel.setSource(s) }, label = { Text(s.name.lowercase()) })
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.recording_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val visible = remember(state.events, state.source, state.query) {
                state.events.filter { (state.source == null || it.source == state.source!!.name) && (state.query.isBlank() || it.text.contains(state.query, true) || it.tag?.contains(state.query, true) == true) }
            }
            val hScroll = rememberScrollState()
            if (visible.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(hScroll).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HeaderCell(stringResource(R.string.col_time), TIME_W)
                    HeaderCell(stringResource(R.string.col_source), SRC_W)
                    HeaderCell(stringResource(R.string.col_tag), TAG_W)
                    HeaderCell(stringResource(R.string.col_message), MSG_W)
                }
                HorizontalDivider()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    visible.isEmpty() -> EmptyState(Icons.Default.FiberManualRecord, stringResource(R.string.recording_events, 0), null)
                    else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(visible, key = { it.id }) { e ->
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(hScroll).padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(timeFormat.format(Date(e.time)), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.width(TIME_W))
                                Box(modifier = Modifier.width(SRC_W)) { Tag(e.source.lowercase(), sourceColor(e.source)) }
                                Text(e.tag.orEmpty(), style = MonoStyle.copy(color = MaterialTheme.colorScheme.primary), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(TAG_W))
                                Text(e.text, style = MonoStyle, maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(MSG_W))
                            }
                        }
                    }
                }
            }
        }
    }

    state.exportedZip?.let { zip ->
        ModalBottomSheet(onDismissRequest = viewModel::dismissExport) {
            Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.recording_export_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.recording_exported, zip), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(zip.substringAfterLast('/'), style = MonoStyle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { runCatching { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.recording_export_open_folder), modifier = Modifier.padding(start = 6.dp))
                    }
                    Button(
                        onClick = {
                            runCatching {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", File(zip))
                                val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.recording_export_share)))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.recording_export_share), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

private val TIME_W = 108.dp
private val SRC_W = 96.dp
private val TAG_W = 148.dp
private val MSG_W = 340.dp

@Composable
private fun HeaderCell(label: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun sourceColor(source: String) = when (source) {
    RecordSource.LOGCAT.name -> MaterialTheme.colorScheme.secondary
    RecordSource.BROADCASTS.name -> MaterialTheme.colorScheme.tertiary
    RecordSource.FILES.name -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.outline
}
