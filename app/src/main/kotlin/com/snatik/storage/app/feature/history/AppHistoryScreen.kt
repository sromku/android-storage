package com.snatik.storage.app.feature.history

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.apps.AppEvent
import com.snatik.storage.core.apps.AppEventLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class AppHistoryUiState(val events: List<AppEvent> = emptyList(), val loading: Boolean = true)

class AppHistoryViewModel(private val log: AppEventLog) : ViewModel() {
    private val _state = MutableStateFlow(AppHistoryUiState())
    val state: StateFlow<AppHistoryUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            if (_state.value.events.isEmpty()) _state.update { it.copy(loading = true) }
            runCatching { log.reconcile() } // detect anything installed/updated/removed since last open
            val events = runCatching { log.history() }.getOrDefault(emptyList())
            _state.update { AppHistoryUiState(events, loading = false) }
        }
    }

    fun clear() {
        viewModelScope.launch {
            log.clear()
            refresh()
        }
    }
}

private enum class HistoryFilter { ALL, INSTALLED, UPDATED, UNINSTALLED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHistoryScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit, viewModel: AppHistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var menu by remember { mutableStateOf(false) }
    // Pick up events the manifest receiver logged while we were away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_history_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.history_clear)) }, onClick = { menu = false; viewModel.clear() })
                    }
                },
            )
        },
    ) { padding ->
        val shown = state.events.filter {
            when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.INSTALLED -> it.type == "installed"
                HistoryFilter.UPDATED -> it.type == "updated"
                HistoryFilter.UNINSTALLED -> it.type == "uninstalled"
            }
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(filter == HistoryFilter.ALL, { filter = HistoryFilter.ALL }, label = { Text(stringResource(R.string.hist_all)) })
                FilterChip(filter == HistoryFilter.INSTALLED, { filter = HistoryFilter.INSTALLED }, label = { Text(stringResource(R.string.ev_installed)) })
                FilterChip(filter == HistoryFilter.UPDATED, { filter = HistoryFilter.UPDATED }, label = { Text(stringResource(R.string.ev_updated)) })
                FilterChip(filter == HistoryFilter.UNINSTALLED, { filter = HistoryFilter.UNINSTALLED }, label = { Text(stringResource(R.string.ev_uninstalled)) })
            }
            Text(
                stringResource(R.string.history_live, state.events.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            if (!state.loading && shown.isEmpty()) {
                EmptyState(Icons.Default.History, stringResource(R.string.history_empty), null)
            } else {
                // Newest-first, so grouping by day keeps days in descending order too.
                val groups = shown.groupBy { dayStart(it.ts) }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groups.forEach { (dayTs, list) ->
                        item(key = "d:$dayTs") {
                            DayHeader(DateUtils.getRelativeTimeSpanString(dayTs, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString())
                        }
                        items(list, key = { it.id }) { event ->
                            EventRow(event, onClick = { if (event.type != "uninstalled") onOpenApp(event.packageName) })
                        }
                    }
                }
            }
        }
    }
}

private fun dayStart(ts: Long): Long = ts - (ts % DateUtils.DAY_IN_MILLIS)

@Composable
private fun DayHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun EventRow(event: AppEvent, onClick: () -> Unit) {
    val context = LocalContext.current
    val (icon, tint) = eventVisual(event.type)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(tint.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text(event.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            SelectionContainer { Text(event.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) }
            val version = event.versionName?.let { " · v$it" } ?: ""
            val seeded = if (event.seeded) "  ·  " + stringResource(R.string.hist_seeded) else ""
            Text(eventLabel(event.type) + version + "  ·  " + event.ts.relativeTime(context) + seeded, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun eventVisual(type: String): Pair<ImageVector, Color> = when (type) {
    "installed" -> Icons.Default.Download to MaterialTheme.colorScheme.primary
    "updated" -> Icons.Default.Update to MaterialTheme.colorScheme.tertiary
    else -> Icons.Default.Delete to MaterialTheme.colorScheme.error
}

@Composable
private fun eventLabel(type: String): String = stringResource(
    when (type) {
        "installed" -> R.string.ev_installed
        "updated" -> R.string.ev_updated
        else -> R.string.ev_uninstalled
    },
)
