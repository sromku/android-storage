package com.snatik.storage.app.feature.monitor

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.ClipRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

class ClipboardViewModel(private val inspector: ClipboardInspector) : ViewModel() {
    val history: StateFlow<List<ClipRecord>> = inspector.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun start() = inspector.start()
    fun stop() { inspector.stop() }
    fun capture() = inspector.capture()
    fun set(text: String) { inspector.set(text); inspector.capture() }
    fun clearClipboard() = inspector.clearClipboard()
    fun clearHistory() { viewModelScope.launch { inspector.clearHistory() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(onBack: () -> Unit, onOpenQuery: (title: String, uri: String) -> Unit = { _, _ -> }, viewModel: ClipboardViewModel = koinViewModel()) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showSet by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    val visible = remember(history, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) history else history.filter { it.text.lowercase().contains(q) || it.label.lowercase().contains(q) || it.uris.any { u -> u.lowercase().contains(q) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clip_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { viewModel.capture() }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh)) }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.clip_set)) }, onClick = { showMenu = false; showSet = true }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.clip_clear_clipboard)) }, onClick = { showMenu = false; viewModel.clearClipboard() }, leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.clip_clear_history)) }, onClick = { showMenu = false; viewModel.clearHistory() }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.clip_export)) }, enabled = history.isNotEmpty(), onClick = { showMenu = false; showExport = true }, leadingIcon = { Icon(Icons.Default.IosShare, contentDescription = null) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.clip_about)) }, onClick = { showMenu = false; showHelp = true }, leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (history.isEmpty()) {
                EmptyState(Icons.Default.ContentPaste, stringResource(R.string.clip_empty), stringResource(R.string.clip_empty_body))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.clip_search)) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) })
                    }
                    item { Text(stringResource(R.string.clip_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(visible, key = { it.key }) { e -> ClipCard(e) { selectedKey = e.key } }
                }
            }
        }
    }

    val sel = selectedKey?.let { key -> history.firstOrNull { it.key == key } }
    if (sel != null) ClipDetailSheet(sel, onCopy = { viewModel.set(sel.text) }, onShare = { shareText(context, sel.text) }, onOpenQuery = onOpenQuery, onOpenUri = { openUri(context, it) }) { selectedKey = null }
    if (showSet) SetTextSheet(onSet = { viewModel.set(it); showSet = false }) { showSet = false }
    if (showExport) ExportSheet(visible) { showExport = false }
    if (showHelp) HelpSheet { showHelp = false }
}

@Composable
private fun ClipCard(e: ClipRecord, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val preview = if (e.sensitive) stringResource(R.string.clip_redacted) else e.text.ifEmpty { e.uris.firstOrNull().orEmpty() }
            Text(preview, style = MonoStyle, maxLines = 3, overflow = TextOverflow.Ellipsis, color = if (e.sensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (e.sensitive) Chip(stringResource(R.string.clip_tag_sensitive), MaterialTheme.colorScheme.error)
                if (e.uris.isNotEmpty()) Chip(stringResource(R.string.clip_tag_uri, e.uris.size), MaterialTheme.colorScheme.primary)
                if (e.html.isNotEmpty()) Chip("HTML", MaterialTheme.colorScheme.secondary)
                if (e.itemCount > 1) Chip(stringResource(R.string.clip_tag_items, e.itemCount), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(clipMeta(e), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
}

/* ---------- Detail ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipDetailSheet(e: ClipRecord, onCopy: () -> Unit, onShare: () -> Unit, onOpenQuery: (String, String) -> Unit, onOpenUri: (String) -> Unit, onDismiss: () -> Unit) {
    var revealed by remember { mutableStateOf(!e.sensitive) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(e.label.ifEmpty { stringResource(R.string.clip_title) }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (e.sensitive) Chip(stringResource(R.string.clip_tag_sensitive), MaterialTheme.colorScheme.error)
                    }
                    HorizontalDivider()
                    if (e.text.isNotEmpty()) {
                        if (revealed) SelectionContainer { Text(e.text, style = MonoStyle) }
                        else Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.clip_redacted), style = MonoStyle.copy(color = MaterialTheme.colorScheme.error), modifier = Modifier.weight(1f))
                            TextButton(onClick = { revealed = true }) { Text(stringResource(R.string.clip_reveal)) }
                        }
                    }
                    if (e.html.isNotEmpty() && revealed) DetailRow(stringResource(R.string.clip_detail_html), e.html.take(500))
                    e.uris.forEach { u ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectionContainer(modifier = Modifier.weight(1f)) { Text(u, style = MonoStyle.copy(color = MaterialTheme.colorScheme.primary), maxLines = 2, overflow = TextOverflow.MiddleEllipsis) }
                            if (u.startsWith("content://")) TextButton(onClick = { onOpenQuery(e.label.ifEmpty { "Clip" }, u) }) { Text(stringResource(R.string.clip_query)) }
                            else IconButton(onClick = { onOpenUri(u) }) { Icon(Icons.Default.OpenInNew, contentDescription = stringResource(R.string.clip_open)) }
                        }
                    }
                    if (e.mimeTypes.isNotEmpty()) DetailRow(stringResource(R.string.clip_detail_mime), e.mimeTypes.joinToString(", "))
                    DetailRow(stringResource(R.string.clip_detail_items), e.itemCount.toString())
                    DetailRow(stringResource(R.string.clip_detail_copied), absTime(e.copiedAt))
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCopy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)); Text(stringResource(R.string.clip_copy_back), modifier = Modifier.padding(start = 6.dp))
                        }
                        androidx.compose.material3.OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(18.dp)); Text(stringResource(R.string.clip_share), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    Spacer(Modifier.size(4.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).widthIn(min = 64.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/* ---------- Sheets ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetTextSheet(onSet: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.clip_set_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.clip_set_hint)) })
            Button(onClick = { onSet(text) }, enabled = text.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.clip_set_button)) }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(rows: List<ClipRecord>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun export(kind: String) {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(System.currentTimeMillis())
                    val file = java.io.File(context.cacheDir, "clipboard-$stamp.${if (kind == "json") "jsonl" else "csv"}")
                    file.writeText(if (kind == "json") rowsToJsonl(rows) else rowsToCsv(rows))
                    file.absolutePath
                }.getOrNull()
            }
            if (path != null) com.snatik.storage.app.util.Intents.share(context, listOf(path))
            onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.clip_export_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.clip_export_body, rows.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExportRow("CSV") { export("csv") }
            ExportRow("JSON") { export("json") }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun ExportRow(title: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.IosShare, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.clip_help_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.clip_help_foreground), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.clip_help_sensitive), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
        }
    }
}

/* ---------- helpers ---------- */

private fun shareText(context: android.content.Context, text: String) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openUri(context: android.content.Context, uri: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun csvCell(s: String?): String {
    val v = s.orEmpty()
    return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

private fun rowsToCsv(rows: List<ClipRecord>): String = buildString {
    appendLine("label,text,uris,mime_types,items,sensitive,copied_iso")
    for (r in rows) {
        append(csvCell(r.label)); append(',')
        append(csvCell(if (r.sensitive) "«sensitive»" else r.text)); append(',')
        append(csvCell(r.uris.joinToString(" "))); append(',')
        append(csvCell(r.mimeTypes.joinToString(" "))); append(',')
        append(r.itemCount); append(',')
        append(if (r.sensitive) "yes" else "no"); append(',')
        appendLine(absTime(r.copiedAt))
    }
}

private fun rowsToJsonl(rows: List<ClipRecord>): String = buildString {
    for (r in rows) {
        append(
            org.json.JSONObject()
                .put("label", r.label).put("text", if (r.sensitive) "«sensitive»" else r.text)
                .put("uris", org.json.JSONArray(r.uris)).put("mime_types", org.json.JSONArray(r.mimeTypes))
                .put("items", r.itemCount).put("sensitive", r.sensitive).put("copied_at_ms", r.copiedAt).toString(),
        )
        append('\n')
    }
}

private val absFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
private fun absTime(ms: Long): String = absFmt.format(ms)

private fun clipMeta(e: ClipRecord): String {
    val label = e.label.ifEmpty { e.mimeTypes.firstOrNull() ?: "clip" }
    return "$label · ${humanAgo(e.copiedAt)}"
}

private fun humanAgo(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when { s < 5 -> "now"; s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60}m ago"; s < 86400 -> "${s / 3600}h ago"; else -> "${s / 86400}d ago" }
}
