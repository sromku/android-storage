package com.snatik.storage.app.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.AppOpAccess
import com.snatik.storage.core.apps.AppOpsTimeline
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class AppOpsTimelineViewModel(private val timeline: AppOpsTimeline, private val privilege: PrivilegeManager) : ViewModel() {
    private val _entries = MutableStateFlow<List<AppOpAccess>?>(null)
    val entries: StateFlow<List<AppOpAccess>?> = _entries.asStateFlow()
    val shell get() = privilege.executor.value != null
    init { load() }
    fun load() { viewModelScope.launch { _entries.update { timeline.recent() } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOpsTimelineScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit, viewModel: AppOpsTimelineViewModel = koinViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var sensitiveOnly by remember { mutableStateOf(true) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timeline_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { IconToggleButton(checked = sensitiveOnly, onCheckedChange = { sensitiveOnly = it }) { Icon(Icons.Default.FilterAlt, contentDescription = stringResource(R.string.timeline_sensitive_only)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val list = entries
            when {
                !viewModel.shell && list == null -> EmptyState(Icons.Default.Terminal, stringResource(R.string.timeline_needs_shell), null)
                list == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> {
                    val visible = if (sensitiveOnly) list.filter { it.sensitive } else list
                    if (visible.isEmpty()) EmptyState(Icons.Default.FilterAlt, stringResource(R.string.timeline_empty), null)
                    else LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(visible.size) { i -> Entry(visible[i], onOpenApp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Entry(e: AppOpAccess, onOpenApp: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(e.packageName, size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(e.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (e.sensitive) Tag(e.op.lowercase(), MaterialTheme.colorScheme.error) else Text(e.op.lowercase(), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(humanAgo(e.agoMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun humanAgo(ms: Long): String {
    val s = ms / 1000
    return when { s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60}m ago"; s < 86400 -> "${s / 3600}h ago"; else -> "${s / 86400}d ago" }
}
