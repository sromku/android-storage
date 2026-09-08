package com.snatik.storage.app.feature.api

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.feature.transfer.TransferHub
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.net.DEFAULT_PORT
import com.snatik.storage.core.net.ServerState
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.compose.koinViewModel

class ApiViewModel(private val config: ApiConfig, private val audit: AuditLog, private val hub: TransferHub, private val operations: ApiOperations) : ViewModel() {
    val settings = config.settings
    val serverState: StateFlow<ServerState> = hub.server.state
    val auditEntries = audit.entries
    val toolCount = operations.all.size
    fun setEnabled(on: Boolean) = config.setEnabled(on)
    fun setPrivileged(on: Boolean) = config.setPrivileged(on)
    fun setDestructive(on: Boolean) = config.setDestructive(on)
    fun newToken() = config.regenerateToken()
    fun clearAudit() = audit.clear()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiScreen(onBack: () -> Unit, viewModel: ApiViewModel = koinViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val server by viewModel.serverState.collectAsStateWithLifecycle()
    val audit by viewModel.auditEntries.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.api_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "toggle") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (settings.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (settings.enabled) stringResource(R.string.api_on) else stringResource(R.string.api_off), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Switch(checked = settings.enabled, onCheckedChange = viewModel::setEnabled)
                        }
                        Text(
                            if (server.running) stringResource(R.string.api_server_running, server.addresses.firstOrNull() ?: "localhost") else stringResource(R.string.api_server_stopped) + " · " + stringResource(R.string.api_start_server),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stringResource(R.string.api_tools_count, viewModel.toolCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "gates") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GateRow(stringResource(R.string.api_gate_privileged), stringResource(R.string.api_gate_privileged_body), settings.allowPrivileged, viewModel::setPrivileged)
                    GateRow(stringResource(R.string.api_gate_destructive), stringResource(R.string.api_gate_destructive_body), settings.allowDestructive, viewModel::setDestructive)
                }
            }
            item(key = "token") {
                Column {
                    Text(stringResource(R.string.api_token), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectionContainer(modifier = Modifier.weight(1f)) { Text(settings.token, style = MonoStyle.copy(fontSize = 13.sp)) }
                        IconButton(onClick = { copy(context, settings.token) }) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.api_copy)) }
                        TextButton(onClick = viewModel::newToken) { Text(stringResource(R.string.api_new_token)) }
                    }
                }
            }
            item(key = "setup") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.api_setup), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.api_setup_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val forward = "adb forward tcp:$DEFAULT_PORT tcp:$DEFAULT_PORT"
                        CopyableCode(forward, context)
                        val mcp = mcpConfig(settings.token)
                        CopyableCode(mcp, context)
                    }
                }
            }
            item(key = "audit-header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.api_audit), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::clearAudit, enabled = audit.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.api_audit_clear)) }
                }
                if (audit.isEmpty()) Text(stringResource(R.string.api_audit_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(audit, key = { it.time.toString() + it.tool }) { entry ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (entry.allowed) "✓" else "✕", color = if (entry.allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.tool + "  ·  " + entry.transport, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                        Text(entry.error ?: entry.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(entry.time.relativeTime(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun GateRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CopyableCode(text: String, context: Context) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SelectionContainer(modifier = Modifier.weight(1f)) { Text(text, style = MonoStyle.copy(fontSize = 12.sp)) }
        IconButton(onClick = { copy(context, text) }) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.api_copy)) }
    }
}

private fun mcpConfig(token: String): String = """{
  "mcpServers": {
    "android-storage": {
      "url": "http://localhost:$DEFAULT_PORT/mcp",
      "headers": { "Authorization": "Bearer $token" }
    }
  }
}"""

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("api", text))
    Toast.makeText(context, R.string.api_copied, Toast.LENGTH_SHORT).show()
}
