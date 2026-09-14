package com.snatik.storage.app.feature.monitor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.compose.koinViewModel

class NotificationMonitorViewModel : ViewModel() {
    val entries: StateFlow<List<NotificationRecord>> = NotificationLog.entries
    val connected: StateFlow<Boolean> = NotificationLog.connected
    fun clear() = NotificationLog.clear()
    fun hasAccess(context: Context) = NotificationLog.hasAccess(context)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationMonitorScreen(onBack: () -> Unit, viewModel: NotificationMonitorViewModel = koinViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasAccess = viewModel.hasAccess(context)
    LaunchedEffect(Unit) { NotificationLog.initStreaming(context) }
    val streamOn by NotificationLog.streamEnabled.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notif_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { IconButton(onClick = { viewModel.clear() }) { Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (hasAccess) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { NotificationLog.setStreamEnabled(context, !streamOn) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.notif_stream), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.notif_stream_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = streamOn, onCheckedChange = { NotificationLog.setStreamEnabled(context, it) })
            }
            if (streamOn) {
                com.snatik.storage.app.ui.components.ExternalSinkOption(org.koin.compose.koinInject(), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp))
            }
            HorizontalDivider()
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !hasAccess -> EmptyState(
                    Icons.Default.NotificationsActive,
                    stringResource(R.string.notif_needs_access),
                    stringResource(R.string.notif_needs_access_body),
                    action = { Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text(stringResource(R.string.notif_grant)) } },
                )
                entries.isEmpty() -> EmptyState(Icons.Default.NotificationsActive, stringResource(R.string.notif_empty), stringResource(R.string.notif_empty_body))
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(entries.size) { i ->
                        NotificationRow(entries[i])
                        if (i < entries.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun NotificationRow(r: NotificationRecord) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(r.packageName, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    r.title.ifEmpty { r.packageName },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (r.removed) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (r.ongoing) Tag("ongoing", MaterialTheme.colorScheme.tertiary)
            }
            if (r.text.isNotEmpty()) Text(r.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("${r.packageName} · ${humanTime(r.postedAt)}", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun humanTime(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when { s < 5 -> "now"; s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60}m ago"; s < 86400 -> "${s / 3600}h ago"; else -> "${s / 86400}d ago" }
}
