package com.snatik.storage.app.feature.apps

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.AppWatch
import com.snatik.storage.core.apps.OpAccess
import com.snatik.storage.core.apps.RunningProcess
import com.snatik.storage.core.apps.RunningService
import com.snatik.storage.core.apps.Severity
import com.snatik.storage.core.apps.Signal
import androidx.compose.ui.res.stringResource

@Composable
fun BehaviorTab(watch: AppWatch?, loading: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            loading && watch == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            watch == null -> Unit
            !watch.shellAvailable -> EmptyState(Icons.Default.Terminal, stringResource(R.string.watch_needs_shell), stringResource(R.string.watch_needs_shell_body))
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item { ScoreHeader(watch) }
                if (watch.signals.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.watch_signals)) }
                    items(watch.signals) { SignalRow(it) }
                }
                item { SectionTitle(stringResource(R.string.watch_access) + " · " + watch.accessedOps.size) }
                if (watch.accessedOps.isEmpty()) item { Empty(stringResource(R.string.watch_no_access)) }
                items(watch.accessedOps) { OpRow(it) }
                item { SectionTitle(stringResource(R.string.watch_services) + " · " + watch.services.size) }
                if (watch.services.isEmpty()) item { Empty(stringResource(R.string.watch_no_services)) }
                items(watch.services) { ServiceRow(it) }
                item { SectionTitle(stringResource(R.string.watch_processes) + " · " + watch.processes.size) }
                items(watch.processes) { ProcessRow(it) }
                if (watch.recentFiles.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.watch_recent_files) + " · " + watch.recentFiles.size) }
                    items(watch.recentFiles) { f -> Text(f.path, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ScoreHeader(watch: AppWatch) {
    val color = severityColor(watch.level)
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(56.dp).background(color.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Text(watch.score.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
        Column {
            Text(
                stringResource(when (watch.level) { Severity.ALERT -> R.string.watch_level_alert; Severity.WARN -> R.string.watch_level_warn; Severity.INFO -> R.string.watch_level_ok }),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color,
            )
            Text(stringResource(R.string.watch_score_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SignalRow(signal: Signal) {
    val color = severityColor(signal.severity)
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (signal.severity == Severity.INFO) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(signal.title, style = MaterialTheme.typography.bodyLarge)
            Text(signal.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OpRow(op: OpAccess) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(op.op, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (op.running) Tag("running", MaterialTheme.colorScheme.error)
        Text(op.mode.name.lowercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ServiceRow(service: RunningService) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Bolt, contentDescription = null, tint = if (service.foreground) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(service.name, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(service.process, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (service.foreground) Tag("foreground", MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ProcessRow(process: RunningProcess) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(process.name, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        Text("pid ${process.pid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("%.0f MB".format(process.rssKb / 1024f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))

@Composable
private fun Empty(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

@Composable
private fun severityColor(s: Severity): Color = when (s) {
    Severity.ALERT -> MaterialTheme.colorScheme.error
    Severity.WARN -> MaterialTheme.colorScheme.tertiary
    Severity.INFO -> MaterialTheme.colorScheme.primary
}
