package com.snatik.storage.app.explorer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import com.snatik.storage.app.R
import com.snatik.storage.toReadableSize
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(viewModel: ExplorerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasFullAccess by remember { mutableStateOf(StorageAccess.hasFullAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = StorageAccess.hasFullAccess(context)
                if (now != hasFullAccess) {
                    hasFullAccess = now
                    viewModel.refresh()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasFullAccess = StorageAccess.hasFullAccess(context)
        viewModel.refresh()
    }

    BackHandler(enabled = !state.atVolumeList) { viewModel.up() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when {
                        state.atVolumeList -> stringResource(R.string.volumes)
                        state.atVolumeRoot -> state.volume?.let { stringResource(it.labelRes) } ?: ""
                        else -> state.directory?.name ?: ""
                    }
                    Text(title)
                },
                navigationIcon = {
                    if (!state.atVolumeList) {
                        IconButton(onClick = { viewModel.up() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!hasFullAccess) {
                PermissionBanner(
                    onGrant = {
                        val intent = StorageAccess.allFilesSettingsIntent(context)
                        if (intent != null) context.startActivity(intent) else legacyLauncher.launch(StorageAccess.legacyPermissions)
                    },
                )
            }
            when {
                state.atVolumeList -> VolumeList(state.volumes, onOpen = viewModel::openVolume)
                else -> DirectoryList(
                    state = state,
                    onOpenDirectory = viewModel::open,
                    onOpenFile = { openWithOtherApp(context, it) },
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.permission_all_files_title), style = MaterialTheme.typography.titleMedium)
        Text(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) stringResource(R.string.permission_all_files_body)
            else stringResource(R.string.permission_legacy_rationale),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onGrant) { Text(stringResource(R.string.permission_grant)) }
    }
    HorizontalDivider()
}

@Composable
private fun VolumeList(volumes: List<Volume>, onOpen: (Volume) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(volumes, key = { it.root.absolutePath }) { volume ->
            ListItem(
                modifier = Modifier.clickable { onOpen(volume) },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                headlineContent = { Text(stringResource(volume.labelRes)) },
                supportingContent = {
                    Column {
                        Text(volume.root.absolutePath, style = MaterialTheme.typography.bodySmall)
                        if (volume.total > 0) {
                            Text(
                                "${(volume.total - volume.free).toReadableSize()} used of ${volume.total.toReadableSize()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun DirectoryList(state: ExplorerState, onOpenDirectory: (File) -> Unit, onOpenFile: (File) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading && state.entries.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.error != null -> Message(stringResource(R.string.cannot_read_directory), state.error)
            state.entries.isEmpty() -> Message(stringResource(R.string.empty_directory), state.directory?.absolutePath ?: "")
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.entries, key = { it.file.absolutePath }) { entry ->
                    EntryRow(entry) { if (entry.isDirectory) onOpenDirectory(entry.file) else onOpenFile(entry.file) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, onClick: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
        },
        headlineContent = { Text(entry.name) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (entry.isDirectory) {
                    if (entry.childCount >= 0) Text(stringResource(R.string.items_count, entry.childCount))
                } else {
                    Text(entry.sizeLabel)
                }
                Text(dateFormat.format(Date(entry.lastModified)))
            }
        },
    )
}

@Composable
private fun Message(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

private fun openWithOtherApp(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_with)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_for_file, Toast.LENGTH_SHORT).show()
    }
}
