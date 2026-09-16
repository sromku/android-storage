package com.snatik.storage.app.feature.api

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.feature.transfer.ServerService
import com.snatik.storage.app.feature.transfer.TransferHub
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.net.DEFAULT_PORT
import com.snatik.storage.core.net.ServerState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One operation's metadata for the tools list. */
data class ToolInfo(val name: String, val description: String, val privileged: Boolean, val destructive: Boolean, val params: List<Pair<String, String>>)

class ApiViewModel(private val config: ApiConfig, private val audit: AuditLog, private val hub: TransferHub, private val operations: ApiOperations) : ViewModel() {
    val settings = config.settings
    val serverState: StateFlow<ServerState> = hub.server.state
    val auditEntries = audit.entries
    val tools: List<ToolInfo> = operations.all.map { op ->
        val props = runCatching { op.schema["properties"]?.jsonObject }.getOrNull()
        val params = props?.entries?.map { (k, v) -> k to (runCatching { v.jsonObject["type"]?.jsonPrimitive?.content }.getOrNull() ?: "string") } ?: emptyList()
        ToolInfo(op.name, op.description, op.privileged, op.destructive, params)
    }.sortedBy { it.name }
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
    var showTools by remember { mutableStateOf(false) }
    var showAllAudit by remember { mutableStateOf(false) }
    var selectedCall by remember { mutableStateOf<AuditEntry?>(null) }

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
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (settings.enabled) stringResource(R.string.api_on) else stringResource(R.string.api_off), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    if (server.running) stringResource(R.string.api_server_running, server.addresses.firstOrNull() ?: "localhost") else stringResource(R.string.api_server_stopped),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Enabling the API also brings the server up so it's reachable right away.
                            Switch(checked = settings.enabled, onCheckedChange = { on -> viewModel.setEnabled(on); if (on) ServerService.start(context) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (server.running) {
                                Pill(stringResource(R.string.api_stop_server), MaterialTheme.colorScheme.error) { ServerService.stop(context) }
                            } else {
                                Pill(stringResource(R.string.api_start_server), MaterialTheme.colorScheme.primary) { ServerService.start(context) }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showTools = true }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.api_tools_count, viewModel.toolCount), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.api_view_tools), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item(key = "gates") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        GateRow(stringResource(R.string.api_gate_privileged), stringResource(R.string.api_gate_privileged_body), settings.allowPrivileged, viewModel::setPrivileged)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        GateRow(stringResource(R.string.api_gate_destructive), stringResource(R.string.api_gate_destructive_body), settings.allowDestructive, viewModel::setDestructive)
                    }
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
                        CopyableCode("adb forward tcp:$DEFAULT_PORT tcp:$DEFAULT_PORT", context)
                        CopyableCode(mcpConfig(settings.token), context)
                    }
                }
            }
            item(key = "audit-header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.api_audit), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    if (audit.isNotEmpty()) Text(audit.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = viewModel::clearAudit, enabled = audit.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.api_audit_clear)) }
                }
                if (audit.isEmpty()) Text(stringResource(R.string.api_audit_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val shown = if (showAllAudit) audit else audit.take(AUDIT_PREVIEW)
            items(shown, key = { it.time.toString() + it.tool }) { entry ->
                AuditRow(entry, context) { selectedCall = entry }
            }
            if (audit.size > AUDIT_PREVIEW) item(key = "audit-more") {
                TextButton(onClick = { showAllAudit = !showAllAudit }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAllAudit) stringResource(R.string.api_show_less) else stringResource(R.string.api_show_all, audit.size))
                }
            }
        }
    }

    if (showTools) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showTools = false }, sheetState = sheetState) {
            ToolsSheet(viewModel.tools)
        }
    }
    selectedCall?.let { entry ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { selectedCall = null }, sheetState = sheetState) {
            CallDetailSheet(entry, context)
        }
    }
}

/* ---------- audit row ---------- */

@Composable
private fun AuditRow(entry: AuditEntry, context: Context, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val color = if (entry.allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.tool, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                TransportBadge(entry.transport)
            }
            val sub = entry.error ?: entry.summary.takeIf { it.isNotBlank() }?.let { stringResource(R.string.api_args_keys, it) }
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = if (entry.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(entry.time.relativeTime(context), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

@Composable
private fun TransportBadge(transport: String) {
    val mcp = transport.equals("mcp", ignoreCase = true)
    val c = if (mcp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(c.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(transport.uppercase(), style = MaterialTheme.typography.labelSmall, color = c, fontWeight = FontWeight.Bold)
    }
}

/* ---------- call detail sheet ---------- */

@Composable
private fun CallDetailSheet(entry: AuditEntry, context: Context) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(entry.tool, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            TransportBadge(entry.transport)
        }
        DetailRow(stringResource(R.string.api_detail_status), if (entry.allowed) stringResource(R.string.api_detail_allowed) else stringResource(R.string.api_detail_denied), if (entry.allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        DetailRow(stringResource(R.string.api_detail_time), FULL_TIME.format(Date(entry.time)))
        DetailRow(stringResource(R.string.api_detail_endpoint), if (entry.transport.equals("mcp", true)) "POST /mcp · tools/call ${entry.tool}" else "POST /api/v1/${entry.tool}")
        entry.error?.let { DetailRow(stringResource(R.string.api_detail_error), it, MaterialTheme.colorScheme.error) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.api_detail_args), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = { copy(context, entry.args.ifBlank { "{}" }) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.api_copy), modifier = Modifier.size(16.dp)) }
            }
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(12.dp)) {
                SelectionContainer { Text(entry.args.ifBlank { "{}" }, style = MonoStyle.copy(fontSize = 12.sp)) }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(1f))
    }
}

/* ---------- tools sheet ---------- */

@Composable
private fun ToolsSheet(tools: List<ToolInfo>) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(tools, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) tools else tools.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.api_tools_title, tools.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.api_tools_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            shape = RoundedCornerShape(14.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.name }) { tool -> ToolCard(tool) }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolInfo) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tool.name, style = MonoStyle.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f))
                if (tool.privileged) GateBadge(stringResource(R.string.api_badge_shell), MaterialTheme.colorScheme.tertiary)
                if (tool.destructive) GateBadge(stringResource(R.string.api_badge_changes), MaterialTheme.colorScheme.error)
            }
            Text(tool.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tool.params.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                tool.params.forEach { (name, type) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name, style = MonoStyle.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.primary))
                        Text(type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun GateBadge(text: String, color: Color) {
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

/* ---------- shared ---------- */

@Composable
private fun Pill(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GateRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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

private const val AUDIT_PREVIEW = 12
private val FULL_TIME = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

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
