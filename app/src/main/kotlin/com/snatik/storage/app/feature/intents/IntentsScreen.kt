package com.snatik.storage.app.feature.intents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.TopLevel
import com.snatik.storage.app.ui.components.TopLevelBar
import com.snatik.storage.app.ui.theme.MonoStyle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentsScreen(
    onSwitchTab: (TopLevel) -> Unit,
    onOpenBuilder: () -> Unit,
    onOpenPreset: (Long) -> Unit,
    onOpenLog: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDeepLink: () -> Unit,
    viewModel: IntentsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.intents_title)) }, scrollBehavior = scrollBehavior) },
        bottomBar = { TopLevelBar(current = TopLevel.INTENTS, onSelect = onSwitchTab) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "send") {
                FeatureCard(Icons.AutoMirrored.Filled.Send, stringResource(R.string.intents_send_title), stringResource(R.string.intents_send_body), onClick = onOpenBuilder) {
                    FilledTonalButton(onClick = onOpenBuilder) { Text(stringResource(R.string.intents_new)) }
                }
            }
            if (state.presets.isNotEmpty()) {
                item(key = "presets-header") { Text(stringResource(R.string.intents_presets), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp)) }
                items(state.presets, key = { "preset:" + it.id }) { preset ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenPreset(preset.id) }.padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                            Text(preset.spec.action ?: stringResource(R.string.intent_no_action), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { viewModel.deletePreset(preset.id) }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
                    }
                }
            }
            item(key = "sink") {
                FeatureCard(Icons.Default.Radar, stringResource(R.string.intents_sink_title), stringResource(R.string.intents_sink_body), onClick = onOpenLog, trailing = {
                    Switch(checked = state.sinkEnabled, onCheckedChange = viewModel::setSinkEnabled)
                }) {
                    Text(stringResource(R.string.intents_sink_count, state.captured), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenLog) { Text(stringResource(R.string.intents_open_log)) }
                }
            }
            item(key = "monitor") {
                FeatureCard(Icons.Default.Sensors, stringResource(R.string.intents_monitor_title), stringResource(R.string.intents_monitor_body), onClick = onOpenMonitor) {
                    Text(stringResource(R.string.intents_monitor_status, state.monitorActive, state.monitorEvents), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenMonitor) { Text(stringResource(R.string.open)) }
                }
            }
            item(key = "history") {
                FeatureCard(Icons.Default.History, stringResource(R.string.intents_history_title), stringResource(R.string.intents_history_body), onClick = onOpenHistory) {
                    if (!state.shellAvailable) Text(stringResource(R.string.history_needs_shell), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                    else Text("", modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenHistory, enabled = state.shellAvailable) { Text(stringResource(R.string.open)) }
                }
            }
            item(key = "deeplink") {
                FeatureCard(Icons.Default.Link, stringResource(R.string.intents_deeplink_title), stringResource(R.string.intents_deeplink_body), onClick = onOpenDeepLink) {
                    Text("", modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenDeepLink) { Text(stringResource(R.string.open)) }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    footer: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, content = footer)
        }
    }
}
