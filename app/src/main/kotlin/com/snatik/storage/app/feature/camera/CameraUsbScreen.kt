package com.snatik.storage.app.feature.camera

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.snatik.storage.app.R
import com.snatik.storage.app.util.readableSize
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraUsbScreen(onBack: () -> Unit, viewModel: CameraUsbViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onTreePicked(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.usb_title))
                        if (state.status == UsbStatus.READY) {
                            Text(
                                stringResource(R.string.usb_count, state.items.size, state.selected.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    if (state.status == UsbStatus.READY && state.items.isNotEmpty()) {
                        TextButton(onClick = { if (state.selected.size == state.items.size) viewModel.clearSelection() else viewModel.selectAll() }) {
                            Text(stringResource(if (state.selected.size == state.items.size) R.string.camerabrowse_none else R.string.camerabrowse_all))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.selected.isNotEmpty() && state.status == UsbStatus.READY) {
                Button(onClick = { viewModel.importSelected() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.usb_import, state.selected.size), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.status) {
                UsbStatus.IDLE -> Idle(onPick = { picker.launch(null) })
                UsbStatus.SCANNING -> Loading(stringResource(R.string.usb_scanning, state.scanned))
                UsbStatus.READY, UsbStatus.IMPORTING -> {
                    if (state.items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.usb_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        Grid(state, onToggle = viewModel::toggle)
                    }
                }
            }
            if (state.status == UsbStatus.IMPORTING) {
                Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                    Text(
                        stringResource(R.string.usb_importing, state.importedDone, state.importedTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally),
                    )
                    LinearProgressIndicator(
                        progress = { if (state.importedTotal == 0) 0f else state.importedDone.toFloat() / state.importedTotal },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Idle(onPick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.usb_start_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
        Text(
            stringResource(R.string.usb_start_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )
        Button(onClick = onPick) { Text(stringResource(R.string.usb_choose)) }
    }
}

@Composable
private fun Loading(text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Grid(state: CameraUsbState, onToggle: (android.net.Uri) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(state.items, key = { it.uri.toString() }) { item ->
            val selected = item.uri in state.selected
            Box(Modifier.aspectRatio(1f).clickable { onToggle(item.uri) }) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (selected) Modifier.padding(8.dp) else Modifier)
                        .clip(RoundedCornerShape(if (selected) 8.dp else 4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(model = item.uri, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (item.isRaw) {
                        Text(
                            "RAW", style = MaterialTheme.typography.labelSmall, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                        )
                    }
                }
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                        .then(if (selected) Modifier.background(Color.White, RoundedCornerShape(50)) else Modifier),
                )
            }
        }
    }
}
