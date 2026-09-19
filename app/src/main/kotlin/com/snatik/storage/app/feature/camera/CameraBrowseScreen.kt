package com.snatik.storage.app.feature.camera

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RawOn
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.snatik.storage.app.R
import com.snatik.storage.app.util.readableSize
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraBrowseScreen(onBack: () -> Unit, viewModel: CameraBrowseViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.camerabrowse_title))
                        if (state.status == CamStatus.CONNECTED || state.status == CamStatus.DOWNLOADING) {
                            Text(
                                stringResource(R.string.camerabrowse_count, state.items.size, state.selected.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (state.status == CamStatus.IDLE) onBack() else viewModel.disconnect() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                    }
                },
                actions = {
                    if (state.status == CamStatus.CONNECTED && state.items.isNotEmpty()) {
                        TextButton(onClick = { if (state.selected.size == state.items.size) viewModel.clearSelection() else viewModel.selectAll() }) {
                            Text(stringResource(if (state.selected.size == state.items.size) R.string.camerabrowse_none else R.string.camerabrowse_all))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.selected.isNotEmpty() && state.status == CamStatus.CONNECTED) {
                Button(
                    onClick = { viewModel.downloadSelected() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.camerabrowse_download, state.selected.size), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.status) {
                CamStatus.IDLE, CamStatus.ERROR -> ConnectForm(state, onHost = viewModel::setHost, onConnect = viewModel::connect)
                CamStatus.CONNECTING -> Loading(stringResource(R.string.camerabrowse_connecting))
                CamStatus.LISTING -> Loading(stringResource(R.string.camerabrowse_listing, state.listed, state.total))
                CamStatus.CONNECTED, CamStatus.DOWNLOADING -> Grid(state, onToggle = viewModel::toggle)
            }
            if (state.status == CamStatus.DOWNLOADING) {
                Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                    Text(
                        stringResource(R.string.camerabrowse_downloading, state.downloadDone, state.downloadTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally),
                    )
                    LinearProgressIndicator(
                        progress = { if (state.downloadTotal == 0) 0f else state.downloadDone.toFloat() / state.downloadTotal },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Loading(text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectForm(state: CameraBrowseState, onHost: (String) -> Unit, onConnect: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.camerabrowse_connect_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
        Text(
            stringResource(R.string.camerabrowse_connect_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        OutlinedTextField(
            value = state.host,
            onValueChange = onHost,
            singleLine = true,
            label = { Text(stringResource(R.string.camerabrowse_ip)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConnect() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.error != null) {
            Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        }
        Button(onClick = onConnect, modifier = Modifier.padding(top = 18.dp)) {
            Text(stringResource(R.string.camerabrowse_connect))
        }
    }
}

@Composable
private fun Grid(state: CameraBrowseState, onToggle: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(state.items, key = { it.handle }) { item ->
            val selected = item.handle in state.selected
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clickable { onToggle(item.handle) },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (selected) Modifier.padding(8.dp) else Modifier)
                        .clip(RoundedCornerShape(if (selected) 8.dp else 4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.thumb != null) {
                        AsyncImage(model = item.thumb, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(if (item.isRaw) Icons.Default.RawOn else Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                    }
                    if (item.isRaw) {
                        Text(
                            "RAW",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                .background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
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

