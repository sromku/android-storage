package com.snatik.storage.app.feature.network

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.apps.AppNetworkUsage
import org.koin.androidx.compose.koinViewModel

private enum class SortField(val labelRes: Int) {
    TOTAL(R.string.net_sort_total),
    RECEIVED(R.string.net_sort_received),
    SENT(R.string.net_sort_sent),
    NAME(R.string.net_sort_name),
}

private fun List<AppNetworkUsage>.sorted(field: SortField, desc: Boolean): List<AppNetworkUsage> {
    val base = when (field) {
        SortField.TOTAL -> sortedByDescending { it.totalBytes }
        SortField.RECEIVED -> sortedByDescending { it.rxBytes }
        SortField.SENT -> sortedByDescending { it.txBytes }
        SortField.NAME -> sortedBy { it.label.lowercase() }
    }
    return if (desc) base else base.reversed()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit, onOpenSettings: () -> Unit = {}, initialQuery: String = "", viewModel: NetworkViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var sortField by rememberSaveable { mutableStateOf(SortField.TOTAL) }
    // Numeric fields default to largest-first; Name defaults to A-Z.
    var sortDesc by rememberSaveable { mutableStateOf(true) }
    var sortMenu by remember { mutableStateOf(false) }
    remember(initialQuery) { if (initialQuery.isNotEmpty()) { viewModel.setQuery(initialQuery); searchOpen = true }; 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        OutlinedTextField(value = state.query, onValueChange = viewModel::setQuery, singleLine = true, placeholder = { Text(stringResource(R.string.net_search)) }, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(stringResource(R.string.net_title))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    if (searchOpen) {
                        IconButton(onClick = { searchOpen = false; viewModel.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) }
                    } else {
                        IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh)) }
                        IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.net_sort)) }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortField.entries.forEach { f ->
                                    val active = f == sortField
                                    DropdownMenuItem(
                                        text = { Text(stringResource(f.labelRes), fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal) },
                                        onClick = {
                                            if (active) sortDesc = !sortDesc
                                            else { sortField = f; sortDesc = f != SortField.NAME }
                                        },
                                        trailingIcon = if (active) {
                                            {
                                                Icon(
                                                    if (sortDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        } else null,
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.settings_title)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!state.shellAvailable) {
                EmptyState(Icons.Default.Terminal, stringResource(R.string.net_needs_shell), stringResource(R.string.net_needs_shell_body))
            } else {
                PullToRefreshBox(isRefreshing = state.loading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                    val visible = state.visible.sorted(sortField, sortDesc)
                    if (!state.loading && visible.isEmpty()) {
                        EmptyState(Icons.Default.Lan, stringResource(R.string.net_none), stringResource(R.string.net_none_body))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(visible, key = { it.uid }) { app -> AppRow(app, onClick = { onOpenApp(app.packageName) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppNetworkUsage, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(app.packageName, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "↓ ${app.rxBytes.readableSize()}   ↑ ${app.txBytes.readableSize()}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (app.connections.any { it.state == "ESTABLISHED" }) {
                Tag(stringResource(R.string.net_live_badge), MaterialTheme.colorScheme.primary)
            }
            Text(app.totalBytes.readableSize(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
