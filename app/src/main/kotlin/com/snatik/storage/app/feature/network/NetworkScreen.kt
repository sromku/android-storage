package com.snatik.storage.app.feature.network

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
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
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.apps.AppConnections
import com.snatik.storage.core.apps.Connection
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(onBack: () -> Unit, initialQuery: String = "", viewModel: NetworkViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
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
                    if (searchOpen) IconButton(onClick = { searchOpen = false; viewModel.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) }
                    else IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!state.shellAvailable) {
                EmptyState(Icons.Default.Terminal, stringResource(R.string.net_needs_shell), stringResource(R.string.net_needs_shell_body))
            } else {
                PullToRefreshBox(isRefreshing = state.loading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                    val visible = state.visible
                    if (!state.loading && visible.isEmpty()) {
                        EmptyState(Icons.Default.Lan, stringResource(R.string.net_none), stringResource(R.string.net_none_body))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(visible, key = { it.uid }) { app -> AppRow(app, state.hostNames) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppConnections, hosts: Map<String, String>) {
    var expanded by rememberSaveable(app.uid) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(app.packageName, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(app.connections.size).append(" live")
                        if (app.totalBytes > 0) append("  ·  ").append(app.totalBytes.readableSize())
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (app.connections.any { it.state == "ESTABLISHED" }) Tag("active", MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 48.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                app.connections.forEach { conn -> ConnectionRow(conn, hosts[conn.remoteAddress]) }
            }
        }
    }
}

@Composable
private fun ConnectionRow(conn: Connection, host: String?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            val display = host ?: if (conn.ipv6) "[${conn.remoteAddress}]" else conn.remoteAddress
            Text("$display:${conn.remotePort}", style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            if (host != null) Text(conn.remoteAddress, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(conn.state.lowercase(), style = MaterialTheme.typography.bodySmall, color = if (conn.state == "ESTABLISHED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
