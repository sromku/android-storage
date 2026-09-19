package com.snatik.storage.app.feature.camera

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.util.readableSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(onBack: () -> Unit, onBrowse: () -> Unit, onUsb: () -> Unit) {
    val context = LocalContext.current
    val state by CameraSync.state.collectAsStateWithLifecycle()

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { CameraSyncService.start(context) }

    fun startReceiving() {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        else CameraSyncService.start(context)
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
                    items(state.files) { f ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(f.size.readableSize(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Idle(onStart = { startReceiving() }, onBrowse = onBrowse, onUsb = onUsb, error = state.error)
            }
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
private fun Idle(onStart: () -> Unit, onBrowse: () -> Unit, onUsb: () -> Unit, error: String?) {
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
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
            }
            Button(onClick = onStart, modifier = Modifier.padding(top = 22.dp)) {
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
