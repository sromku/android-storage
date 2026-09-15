package com.snatik.storage.app.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.koin.compose.koinInject

private data class Tool(val title: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit, val recording: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onSwitchTab: (TopLevel) -> Unit,
    onOpenIntents: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenReceive: () -> Unit,
    onOpenApi: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenMatrix: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProviderWatch: () -> Unit,
    onOpenClipboard: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenSystem: () -> Unit,
    onOpenTimeMachine: () -> Unit,
    onOpenBenchmark: () -> Unit,
    onOpenPermFootprint: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPalette: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // A card shows a "recording" badge when any monitor it contains is running.
    val appOpsRec by koinInject<com.snatik.storage.core.apps.AppOpsRecorderStore>().running.collectAsStateWithLifecycle()
    val intentRec by koinInject<com.snatik.storage.core.intents.IntentMonitorStore>().running.collectAsStateWithLifecycle()
    val broadcastRec by koinInject<com.snatik.storage.core.intents.BroadcastStore>().running.collectAsStateWithLifecycle()
    val notifRec by koinInject<com.snatik.storage.core.apps.NotificationRecorderStore>().running.collectAsStateWithLifecycle()
    val providerRec by koinInject<com.snatik.storage.core.apps.ProviderRecorderStore>().running.collectAsStateWithLifecycle()

    val tools = listOf(
        Tool(stringResource(R.string.intents_title), stringResource(R.string.tools_intents_sub), Icons.AutoMirrored.Filled.Send, recording = intentRec || broadcastRec, onClick = onOpenIntents),
        Tool(stringResource(R.string.capture_title), stringResource(R.string.tools_capture_sub), Icons.Default.FiberManualRecord, onClick = onOpenCapture),
        Tool(stringResource(R.string.receive_title), stringResource(R.string.tools_transfer_sub), Icons.Default.Wifi, onOpenReceive),
        Tool(stringResource(R.string.api_title), stringResource(R.string.tools_api_sub), Icons.Default.Api, onOpenApi),
        Tool(stringResource(R.string.net_title), stringResource(R.string.tools_net_sub), Icons.Default.Lan, onOpenNetwork),
        Tool(stringResource(R.string.dashboard_title), stringResource(R.string.tools_dash_sub), Icons.Default.Dashboard, onOpenDashboard),
        Tool(stringResource(R.string.matrix_title), stringResource(R.string.tools_matrix_sub), Icons.Default.GridOn, onOpenMatrix),
        Tool(stringResource(R.string.timeline_title), stringResource(R.string.tools_timeline_sub), Icons.Default.History, onOpenTimeline, recording = appOpsRec),
        Tool(stringResource(R.string.notif_title), stringResource(R.string.tools_notif_sub), Icons.Default.NotificationsActive, onOpenNotifications, recording = notifRec),
        Tool(stringResource(R.string.watch_title), stringResource(R.string.tools_watch_sub), Icons.Default.Sensors, onOpenProviderWatch, recording = providerRec),
        Tool(stringResource(R.string.clip_title), stringResource(R.string.tools_clip_sub), Icons.Default.ContentPaste, onOpenClipboard),
        Tool(stringResource(R.string.insights_title), stringResource(R.string.tools_insights_sub), Icons.Default.Insights, onOpenInsights),
        Tool(stringResource(R.string.system_title), stringResource(R.string.tools_system_sub), Icons.Default.Memory, onOpenSystem),
        Tool(stringResource(R.string.tm_title), stringResource(R.string.tools_tm_sub), Icons.Default.Timeline, onOpenTimeMachine),
        Tool(stringResource(R.string.bench_title), stringResource(R.string.tools_bench_sub), Icons.Default.Speed, onOpenBenchmark),
        Tool(stringResource(R.string.permfoot_title), stringResource(R.string.tools_permfoot_sub), Icons.Default.Balance, onOpenPermFootprint),
        Tool(stringResource(R.string.app_history_title), stringResource(R.string.tools_history_sub), Icons.Default.History, onOpenHistory),
    )
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.tab_tools)) }, scrollBehavior = scrollBehavior, actions = { androidx.compose.material3.IconButton(onClick = onOpenPalette) { Icon(Icons.Default.Bolt, contentDescription = stringResource(R.string.palette_open)) } }) },
        bottomBar = { TopLevelBar(current = TopLevel.TOOLS, onSelect = onSwitchTab) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tools) { tool ->
                Card(onClick = tool.onClick, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.weight(1f))
                            if (tool.recording) RecordingBadge()
                        }
                        Text(tool.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingBadge() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(50)))
        Text(
            stringResource(R.string.tools_recording),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
