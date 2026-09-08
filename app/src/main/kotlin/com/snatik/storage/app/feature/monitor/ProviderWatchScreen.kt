package com.snatik.storage.app.feature.monitor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.compose.koinViewModel

class ProviderWatchViewModel(private val watcher: ProviderWatcher) : ViewModel() {
    val watching: StateFlow<Set<String>> = watcher.watching
    val events: StateFlow<List<ProviderEvent>> = watcher.events
    fun toggle(t: WatchTarget) = watcher.toggle(t)
    fun clear() = watcher.clearLog()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderWatchScreen(onBack: () -> Unit, viewModel: ProviderWatchViewModel = koinViewModel()) {
    val watching by viewModel.watching.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watch_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { IconButton(onClick = { viewModel.clear() }) { Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                COMMON_PROVIDERS.forEach { t ->
                    FilterChip(selected = t.uri in watching, onClick = { viewModel.toggle(t) }, label = { Text(t.label) })
                }
            }
            HorizontalDivider()
            if (events.isEmpty()) {
                EmptyState(Icons.Default.Sensors, stringResource(R.string.watch_empty), stringResource(R.string.watch_empty_body))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(events.size) { i ->
                        val e = events[i]
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(e.target, style = MaterialTheme.typography.bodyLarge)
                            Text(e.uri, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(humanClock(e.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (i < events.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun humanClock(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when { s < 5 -> "now"; s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60}m ago"; else -> "${s / 3600}h ago" }
}
