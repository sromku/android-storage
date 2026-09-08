package com.snatik.storage.app.feature.capture

import android.Manifest
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.app.navigation.TopLevel
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.components.TopLevelBar
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.capture.RecordSource
import com.snatik.storage.core.capture.RecordingEntity
import com.snatik.storage.core.capture.SnapshotEntity
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    route: Route.Capture,
    onSwitchTab: (TopLevel) -> Unit,
    onOpenSnapshot: (Long) -> Unit,
    onOpenRecording: (Long) -> Unit,
    viewModel: CaptureViewModel = koinViewModel(parameters = { parametersOf(route) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.startRecording(result.resultCode, result.data)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.capture_title)) }, scrollBehavior = scrollBehavior) },
        bottomBar = { TopLevelBar(current = TopLevel.CAPTURE, onSelect = onSwitchTab) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "recording") {
                RecordingCard(state, onStart = viewModel::openRecordingForm, onStop = viewModel::stopRecording, onOpen = { state.active?.let { onOpenRecording(it.id) } })
            }
            item(key = "snapshots-header") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.section_snapshots), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.openSnapshotForm() }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.snapshot_new), modifier = Modifier.padding(start = 6.dp))
                    }
                }
                if (state.snapshotRunning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    state.snapshotProgress?.let { p ->
                        Text(stringResource(R.string.snapshot_progress, p.files, p.bytes.readableSize()) + "  ·  " + p.current, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (state.snapshots.isEmpty() && !state.snapshotRunning) {
                item { EmptyState(Icons.Default.CameraAlt, stringResource(R.string.snapshot_none), stringResource(R.string.snapshot_none_hint), modifier = Modifier.padding(vertical = 8.dp)) }
            }
            items(state.snapshots, key = { "s:" + it.id }) { snap ->
                SnapshotRow(snap, onClick = { onOpenSnapshot(snap.id) }, onDelete = { viewModel.deleteSnapshot(snap.id) })
            }
            item(key = "recordings-header") {
                Text(stringResource(R.string.section_recordings), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
            }
            val past = state.recordings.filter { it.endedAt != null }
            if (past.isEmpty()) item { Text(stringResource(R.string.recording_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
            items(past, key = { "r:" + it.id }) { rec ->
                RecordingRow(rec, onClick = { onOpenRecording(rec.id) }, onDelete = { viewModel.deleteRecording(rec.id) })
            }
        }
    }

    state.snapshotForm?.let { form ->
        AlertDialog(
            onDismissRequest = viewModel::closeSnapshotForm,
            title = { Text(stringResource(R.string.snapshot_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = form.path, onValueChange = { viewModel.updateSnapshotForm(form.copy(path = it)) }, label = { Text(stringResource(R.string.snapshot_path)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = form.label, onValueChange = { viewModel.updateSnapshotForm(form.copy(label = it)) }, label = { Text(stringResource(R.string.snapshot_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    CheckRow(stringResource(R.string.snapshot_hash), form.hash) { viewModel.updateSnapshotForm(form.copy(hash = it)) }
                    CheckRow(stringResource(R.string.snapshot_copies), form.keepCopies) { viewModel.updateSnapshotForm(form.copy(keepCopies = it)) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::createSnapshot, enabled = form.path.isNotBlank()) { Text(stringResource(R.string.create)) } },
            dismissButton = { TextButton(onClick = viewModel::closeSnapshotForm) { Text(stringResource(R.string.cancel)) } },
        )
    }

    state.recordingForm?.let { form ->
        AlertDialog(
            onDismissRequest = viewModel::closeRecordingForm,
            title = { Text(stringResource(R.string.recording_start)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = form.name, onValueChange = { viewModel.updateRecordingForm(form.copy(name = it)) }, label = { Text(stringResource(R.string.recording_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.recording_sources), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    CheckRow(stringResource(R.string.source_logcat), RecordSource.LOGCAT in form.sources) { viewModel.updateRecordingForm(form.copy(sources = form.sources.toggle(RecordSource.LOGCAT, it))) }
                    if (RecordSource.LOGCAT in form.sources) {
                        if (!state.shellAvailable) Text(stringResource(R.string.logcat_needs_shell), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedTextField(value = form.logcatPackage, onValueChange = { viewModel.updateRecordingForm(form.copy(logcatPackage = it)) }, label = { Text(stringResource(R.string.logcat_package)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = form.logcatFilter, onValueChange = { viewModel.updateRecordingForm(form.copy(logcatFilter = it)) }, label = { Text(stringResource(R.string.logcat_filter)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
                    }
                    CheckRow(stringResource(R.string.source_broadcasts), RecordSource.BROADCASTS in form.sources) { viewModel.updateRecordingForm(form.copy(sources = form.sources.toggle(RecordSource.BROADCASTS, it))) }
                    if (RecordSource.BROADCASTS in form.sources) Text(stringResource(R.string.broadcasts_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CheckRow(stringResource(R.string.source_files), RecordSource.FILES in form.sources) { viewModel.updateRecordingForm(form.copy(sources = form.sources.toggle(RecordSource.FILES, it))) }
                    if (RecordSource.FILES in form.sources) {
                        OutlinedTextField(value = form.watchPaths, onValueChange = { viewModel.updateRecordingForm(form.copy(watchPaths = it)) }, label = { Text(stringResource(R.string.watch_paths)) }, minLines = 2, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
                    }
                    CheckRow(stringResource(R.string.source_screen), RecordSource.SCREEN in form.sources) { viewModel.updateRecordingForm(form.copy(sources = form.sources.toggle(RecordSource.SCREEN, it))) }
                    if (RecordSource.SCREEN in form.sources) Text(stringResource(R.string.screen_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.notifications_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = form.sources.isNotEmpty(),
                    onClick = {
                        if (RecordSource.SCREEN in form.sources) {
                            projectionLauncher.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
                        } else {
                            viewModel.startRecording()
                        }
                    },
                ) { Text(stringResource(R.string.recording_begin)) }
            },
            dismissButton = { TextButton(onClick = viewModel::closeRecordingForm) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun Set<RecordSource>.toggle(source: RecordSource, on: Boolean) = if (on) this + source else this - source

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RecordingCard(state: CaptureUiState, onStart: () -> Unit, onStop: () -> Unit, onOpen: () -> Unit) {
    val active = state.active
    Card(
        colors = CardDefaults.cardColors(containerColor = if (active != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = if (active != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f)) {
                    Text(active?.name ?: stringResource(R.string.recording_idle), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (active != null) Text(stringResource(R.string.recording_events, state.liveCount) + "  ·  " + active.sources.lowercase().replace(",", ", "), style = MaterialTheme.typography.bodySmall)
                }
                if (active != null) {
                    TextButton(onClick = onOpen) { Text(stringResource(R.string.open)) }
                    Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.recording_stop)) }
                } else {
                    FilledTonalButton(onClick = onStart) { Text(stringResource(R.string.recording_start)) }
                }
            }
        }
    }
}

@Composable
private fun SnapshotRow(snap: SnapshotEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(snap.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (snap.hashed) Tag("hash")
                if (snap.copied) Tag("copies")
            }
            Text(snap.rootPath, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(stringResource(R.string.snapshot_summary, snap.fileCount, snap.totalBytes.readableSize()) + "  ·  " + snap.createdAt.relativeTime(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
    }
}

@Composable
private fun RecordingRow(rec: RecordingEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val duration = ((rec.endedAt ?: System.currentTimeMillis()) - rec.startedAt) / 1000
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (rec.videoPath != null) Icons.Default.Videocam else Icons.Default.FiberManualRecord, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(rec.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.recording_duration, "%d:%02d".format(duration / 60, duration % 60), rec.eventCount) + "  ·  " + rec.startedAt.relativeTime(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
    }
}
