package com.snatik.storage.app.feature.capture

import android.Manifest
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.capture.RecordSource
import com.snatik.storage.core.capture.RecordingEntity
import com.snatik.storage.core.capture.SnapshotEntity
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    route: Route.Capture,
    onBack: () -> Unit,
    onOpenSnapshot: (Long) -> Unit,
    onOpenRecording: (Long) -> Unit,
    viewModel: CaptureViewModel = koinViewModel(parameters = { parametersOf(route) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showAppPicker by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
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
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.capture_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } }, scrollBehavior = scrollBehavior) },
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
                SnapshotRow(snap, onClick = { onOpenSnapshot(snap.id) }, onLongPress = { viewModel.startRenameSnapshot(snap) }, onDelete = { viewModel.deleteSnapshot(snap.id) })
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
        ModalBottomSheet(onDismissRequest = viewModel::closeSnapshotForm, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                modifier = Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.snapshot_new), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.snapshot_sheet_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = form.path,
                    onValueChange = { viewModel.updateSnapshotForm(form.copy(path = it)) },
                    label = { Text(stringResource(R.string.snapshot_path)) },
                    singleLine = true,
                    textStyle = MonoStyle,
                    trailingIcon = { IconButton(onClick = { showFolderPicker = true }) { Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.snapshot_browse)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = form.label, onValueChange = { viewModel.updateSnapshotForm(form.copy(label = it)) }, label = { Text(stringResource(R.string.snapshot_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OptionRow(stringResource(R.string.snapshot_hash_label), stringResource(R.string.snapshot_hash_desc), form.hash) { viewModel.updateSnapshotForm(form.copy(hash = it)) }
                OptionRow(stringResource(R.string.snapshot_copies_label), stringResource(R.string.snapshot_copies_desc), form.keepCopies) { viewModel.updateSnapshotForm(form.copy(keepCopies = it)) }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = viewModel::closeSnapshotForm) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = viewModel::createSnapshot, enabled = form.path.isNotBlank()) { Text(stringResource(R.string.create)) }
                }
            }
        }
    }

    state.recordingForm?.let { form ->
        ModalBottomSheet(onDismissRequest = viewModel::closeRecordingForm, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                modifier = Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.recording_start), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.recording_sheet_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = form.name, onValueChange = { viewModel.updateRecordingForm(form.copy(name = it)) }, label = { Text(stringResource(R.string.recording_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.recording_sources), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))

                OptionRow(stringResource(R.string.source_logcat), stringResource(R.string.source_logcat_desc), RecordSource.LOGCAT in form.sources) { viewModel.updateRecordingForm(form.copy(sources = form.sources.toggle(RecordSource.LOGCAT, it))) }
                if (RecordSource.LOGCAT in form.sources) {
                    Column(modifier = Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!state.shellAvailable) Text(stringResource(R.string.logcat_needs_shell), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Column(modifier = Modifier.fillMaxWidth().clickable { showAppPicker = true }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.logcat_scope_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            if (form.logcatPackages.isEmpty()) {
                                Text(stringResource(R.string.logcat_scope_all), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    form.logcatPackages.forEach { pkg ->
                                        AppChip(pkg, apps.firstOrNull { it.packageName == pkg }?.label ?: pkg.substringAfterLast('.'))
                                    }
                                }
                            }
                        }
                        OutlinedTextField(value = form.logcatFilter, onValueChange = { viewModel.updateRecordingForm(form.copy(logcatFilter = it)) }, label = { Text(stringResource(R.string.logcat_filter)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
                    }
                }

                OptionRow(stringResource(R.string.source_broadcasts), stringResource(R.string.source_broadcasts_desc), RecordSource.BROADCASTS in form.sources) { viewModel.updateRecordingForm(form.copy(sources = form.sources.toggle(RecordSource.BROADCASTS, it))) }

                OptionRow(stringResource(R.string.source_files), stringResource(R.string.source_files_desc), RecordSource.FILES in form.sources) { viewModel.updateRecordingForm(form.copy(sources = form.sources.toggle(RecordSource.FILES, it))) }
                if (RecordSource.FILES in form.sources) {
                    OutlinedTextField(value = form.watchPaths, onValueChange = { viewModel.updateRecordingForm(form.copy(watchPaths = it)) }, label = { Text(stringResource(R.string.watch_paths)) }, minLines = 2, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth().padding(start = 4.dp))
                }

                OptionRow(stringResource(R.string.source_screen), stringResource(R.string.source_screen_desc), RecordSource.SCREEN in form.sources) { viewModel.updateRecordingForm(form.copy(sources = form.sources.toggle(RecordSource.SCREEN, it))) }

                Text(stringResource(R.string.notifications_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = viewModel::closeRecordingForm) { Text(stringResource(R.string.cancel)) }
                    Button(
                        enabled = form.sources.isNotEmpty(),
                        onClick = {
                            if (RecordSource.SCREEN in form.sources) {
                                projectionLauncher.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
                            } else {
                                viewModel.startRecording()
                            }
                        },
                    ) { Text(stringResource(R.string.recording_begin)) }
                }
            }
        }
    }

    if (showAppPicker) {
        val form = state.recordingForm
        if (form == null) showAppPicker = false
        else AppPickerSheet(
            apps = apps,
            selected = form.logcatPackages.toSet(),
            onToggle = { pkg ->
                val next = if (pkg in form.logcatPackages) form.logcatPackages - pkg else form.logcatPackages + pkg
                viewModel.updateRecordingForm(form.copy(logcatPackages = next))
            },
            onClear = { viewModel.updateRecordingForm(form.copy(logcatPackages = emptyList())) },
            onDismiss = { showAppPicker = false },
        )
    }

    if (showFolderPicker) {
        val form = state.snapshotForm
        if (form == null) showFolderPicker = false
        else FolderPickerSheet(
            start = form.path,
            onPick = { path ->
                viewModel.updateSnapshotForm(form.copy(path = path, label = form.label.ifBlank { path.substringAfterLast('/') }))
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false },
        )
    }

    state.renameSnapshot?.let { rename ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRenameSnapshot,
            title = { Text(stringResource(R.string.snapshot_rename_title)) },
            text = {
                OutlinedTextField(
                    value = rename.text,
                    onValueChange = viewModel::updateRenameText,
                    label = { Text(stringResource(R.string.snapshot_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRenameSnapshot, enabled = rename.text.isNotBlank()) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRenameSnapshot) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun Set<RecordSource>.toggle(source: RecordSource, on: Boolean) = if (on) this + source else this - source

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(apps: List<AppPick>, selected: Set<String>, onToggle: (String) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) { if (query.isBlank()) apps else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                Text(stringResource(R.string.app_picker_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (selected.isNotEmpty()) TextButton(onClick = onClear) { Text(stringResource(R.string.app_picker_all)) }
            }
            OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text(stringResource(R.string.app_picker_search)) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (apps.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onToggle(app.packageName) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcon(app.packageName, size = 36.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                            }
                            Switch(checked = app.packageName in selected, onCheckedChange = { onToggle(app.packageName) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerSheet(start: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val root = remember { Environment.getExternalStorageDirectory() }
    var dir by remember { mutableStateOf(File(start.ifBlank { root.absolutePath }).let { if (it.isDirectory) it else root }) }
    val subdirs = remember(dir) { dir.listFiles()?.filter { it.isDirectory && !it.isHidden }?.sortedBy { it.name.lowercase() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.folder_picker_title), style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { dir.parentFile?.let { dir = it } }, enabled = dir.parentFile != null) { Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.folder_picker_up)) }
                Text(dir.absolutePath, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
            }
            HorizontalDivider()
            when {
                subdirs == null -> Text(stringResource(R.string.folder_picker_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 16.dp))
                subdirs.isEmpty() -> Text(stringResource(R.string.folder_picker_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(subdirs, key = { it.absolutePath }) { d ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { dir = d }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onPick(dir.absolutePath) }) { Text(stringResource(R.string.folder_picker_use)) }
            }
        }
    }
}

/** A tiny app pill (icon + label) for the selected logcat-scope apps. */
@Composable
private fun AppChip(packageName: String, label: String) {
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50)).padding(start = 4.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AppIcon(packageName, size = 16.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** A labelled toggle with a description, for the capture form sheets. */
@Composable
private fun OptionRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnapshotRow(snap: SnapshotEntity, onClick: () -> Unit, onLongPress: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongPress).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
