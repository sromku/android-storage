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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Insights
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.TopLevel
import com.snatik.storage.app.ui.components.TopLevelBar

private data class Tool(val title: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit)

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
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProviderWatch: () -> Unit,
    onOpenClipboard: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val tools = listOf(
        Tool(stringResource(R.string.intents_title), stringResource(R.string.tools_intents_sub), Icons.AutoMirrored.Filled.Send, onOpenIntents),
        Tool(stringResource(R.string.capture_title), stringResource(R.string.tools_capture_sub), Icons.Default.FiberManualRecord, onOpenCapture),
        Tool(stringResource(R.string.receive_title), stringResource(R.string.tools_transfer_sub), Icons.Default.Wifi, onOpenReceive),
        Tool(stringResource(R.string.api_title), stringResource(R.string.tools_api_sub), Icons.Default.Api, onOpenApi),
        Tool(stringResource(R.string.net_title), stringResource(R.string.tools_net_sub), Icons.Default.Lan, onOpenNetwork),
        Tool(stringResource(R.string.dashboard_title), stringResource(R.string.tools_dash_sub), Icons.Default.Dashboard, onOpenDashboard),
        Tool(stringResource(R.string.matrix_title), stringResource(R.string.tools_matrix_sub), Icons.Default.GridOn, onOpenMatrix),
        Tool(stringResource(R.string.timeline_title), stringResource(R.string.tools_timeline_sub), Icons.Default.History, onOpenTimeline),
        Tool(stringResource(R.string.search_title), stringResource(R.string.tools_search_sub), Icons.Default.Search, onOpenSearch),
        Tool(stringResource(R.string.notif_title), stringResource(R.string.tools_notif_sub), Icons.Default.NotificationsActive, onOpenNotifications),
        Tool(stringResource(R.string.watch_title), stringResource(R.string.tools_watch_sub), Icons.Default.Sensors, onOpenProviderWatch),
        Tool(stringResource(R.string.clip_title), stringResource(R.string.tools_clip_sub), Icons.Default.ContentPaste, onOpenClipboard),
        Tool(stringResource(R.string.insights_title), stringResource(R.string.tools_insights_sub), Icons.Default.Insights, onOpenInsights),
    )
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.tab_tools)) }, scrollBehavior = scrollBehavior) },
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
                        Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
                        Text(tool.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
