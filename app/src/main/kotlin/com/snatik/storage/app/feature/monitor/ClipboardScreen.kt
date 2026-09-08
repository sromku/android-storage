package com.snatik.storage.app.feature.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.compose.koinViewModel

class ClipboardViewModel(private val inspector: ClipboardInspector) : ViewModel() {
    val history: StateFlow<List<ClipEntry>> = inspector.history
    fun start() = inspector.start()
    fun stop() = inspector.stop()
    fun capture() = inspector.capture()
    fun clear() = inspector.clearHistory()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(onBack: () -> Unit, viewModel: ClipboardViewModel = koinViewModel()) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clip_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { viewModel.capture() }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh)) }
                    IconButton(onClick = { viewModel.clear() }) { Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear)) }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (history.isEmpty()) {
                EmptyState(Icons.Default.ContentPaste, stringResource(R.string.clip_empty), stringResource(R.string.clip_empty_body))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(stringResource(R.string.clip_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(history.size) { i ->
                        val e = history[i]
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(e.text, style = MonoStyle)
                                Text("${e.description} · ${humanAgo(e.at)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun humanAgo(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when { s < 5 -> "now"; s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60}m ago"; else -> "${s / 3600}h ago" }
}
