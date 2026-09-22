package com.snatik.storage.app.feature.camera

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.snatik.storage.app.R
import com.snatik.storage.app.feature.media.RawImageModel
import com.snatik.storage.app.feature.media.isRawMedia
import com.snatik.storage.app.util.readableSize

// Declared as a literal so it compiles on any SDK; the permission itself only exists on API 36+.
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(onBack: () -> Unit, onBrowse: () -> Unit, onUsb: () -> Unit) {
    val context = LocalContext.current
    val state by CameraSync.state.collectAsStateWithLifecycle()

    // Optional destination folder for received frames; null means the default Storage Studio/Camera.
    var destDir by remember { mutableStateOf<java.io.File?>(null) }
    var pickingDest by remember { mutableStateOf(false) }

    // Android 16 (Local Network Protections) puts LAN traffic behind a runtime permission. Without it
    // the FTP receiver's replies to the camera are silently dropped and no transfer ever connects, so
    // we request it before starting. Older releases don't have the permission and don't need it.
    val localNetDeniedMsg = stringResource(R.string.camera_local_network_denied)
    val localNetPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) CameraSyncService.start(context, destDir?.absolutePath)
        else CameraSync.setError(localNetDeniedMsg)
    }

    fun ensureLocalNetworkThenStart() {
        if (Build.VERSION.SDK_INT >= 36 &&
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetPermission.launch(LOCAL_NETWORK_PERMISSION)
        } else {
            CameraSyncService.start(context, destDir?.absolutePath)
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ensureLocalNetworkThenStart() }

    fun startReceiving() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ensureLocalNetworkThenStart()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.camera_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (state.running) {
                ConnectionCard(state)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.camera_received, state.files.size, state.totalBytes.readableSize()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(
                        onClick = { CameraSyncService.stop(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.camera_stop), modifier = Modifier.padding(start = 6.dp))
                    }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.files, key = { it.name + it.at }) { f ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ReceivedThumbnail(f)
                            Text(
                                f.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(f.size.readableSize(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Idle(
                    onStart = { startReceiving() },
                    onBrowse = onBrowse,
                    onUsb = onUsb,
                    error = state.error,
                    destLabel = destDir?.name ?: CameraSyncService.DEFAULT_DEST_LABEL,
                    onPickDest = { pickingDest = true },
                )
            }
        }
    }

    if (pickingDest) {
        val saveHereFmt = stringResource(R.string.camera_save_here)
        com.snatik.storage.app.ui.components.FolderPickerSheet(
            title = stringResource(R.string.camera_dest_title),
            confirmLabel = { name -> saveHereFmt.format(name) },
            onDismiss = { pickingDest = false },
            onPick = { folder -> destDir = folder; pickingDest = false },
            start = destDir ?: android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
        )
    }
}

/** A live 48dp thumbnail of a received frame, with a check badge; RAW previews via its embedded JPEG. */
@Composable
private fun ReceivedThumbnail(f: ReceivedFile) {
    val model = remember(f.uri, f.path) {
        when {
            f.path != null && isRawMedia(f.name, "") -> RawImageModel(f.path)
            else -> f.uri
        }
    }
    val raw = remember(f.name) { isRawMedia(f.name, "") }
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        // Base layer: an icon (RAW tag, or a camera glyph) shown while decoding or when a file has no
        // quick preview. A decoded JPEG paints over it; RAW frames keep the tag.
        if (raw) {
            Text("RAW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        AsyncImage(
            model = model,
            contentDescription = f.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(16.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ConnectionCard(state: CameraSyncState) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(9.dp).background(Color(0xFF34C759), RoundedCornerShape(50)))
                Text(stringResource(R.string.camera_listening), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                stringResource(R.string.camera_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            Field(stringResource(R.string.camera_field_host), state.host)
            Field(stringResource(R.string.camera_field_port), state.port.toString())
            Field(stringResource(R.string.camera_field_mode), "FTP · Passive")
            Field(stringResource(R.string.camera_field_user), state.user)
            Field(stringResource(R.string.camera_field_pass), state.pass)
            if (state.dest.isNotBlank()) Field(stringResource(R.string.camera_field_dest), state.dest)
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(width = 96.dp, height = 22.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Idle(
    onStart: () -> Unit,
    onBrowse: () -> Unit,
    onUsb: () -> Unit,
    error: String?,
    destLabel: String,
    onPickDest: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.camera_start_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            Text(
                stringResource(R.string.camera_start_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
            )
            // Optional destination folder, chosen before starting.
            androidx.compose.material3.Surface(
                onClick = onPickDest,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(top = 18.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(R.string.camera_dest_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(destLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
            }
            Button(onClick = onStart, modifier = Modifier.padding(top = 16.dp)) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.camera_start), modifier = Modifier.padding(start = 8.dp))
            }
            androidx.compose.material3.TextButton(onClick = onBrowse, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.camerabrowse_link))
            }
            androidx.compose.material3.TextButton(onClick = onUsb) {
                Text(stringResource(R.string.usb_start_title))
            }
        }
    }
}
