package com.snatik.storage.app.feature.transfer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.FileKindIcon
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.fs.FsEntry
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.net.ServerState
import org.koin.androidx.compose.koinViewModel

class ReceiveViewModel(private val hub: TransferHub) : ViewModel() {
    val state = hub.server.state
    val inboxPath: String get() = hub.inboxDir.absolutePath
    fun start() = hub.startReceiving()
    fun stop() = hub.stopReceiving()
    fun newCode() = hub.server.regenerateCode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(onBack: () -> Unit, onOpenInbox: (String) -> Unit, onOpenFile: (String) -> Unit, viewModel: ReceiveViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receive_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "server") { ServerCard(state, onStart = viewModel::start, onStop = viewModel::stop, onNewCode = viewModel::newCode) }
            item(key = "inbox-header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.receive_inbox), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onOpenInbox(viewModel.inboxPath) }) { Text(stringResource(R.string.receive_open_inbox)) }
                }
            }
            if (state.received.isEmpty()) {
                item { EmptyState(Icons.Default.Inbox, stringResource(R.string.receive_none), null, modifier = Modifier.padding(vertical = 8.dp)) }
            }
            items(state.received, key = { it.path }) { file ->
                val entry = remember(file.path, file.size, file.time) {
                    FsEntry(path = file.path, name = file.path.substringAfterLast('/'), isDirectory = false, size = file.size, lastModified = file.time, isHidden = false, isSymlink = false, canRead = true, canWrite = true, childCount = null)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenFile(file.path) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FileKindIcon(entry = entry, selected = false)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.path.substringAfterLast('/'), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(file.size.readableSize() + "  ·  " + stringResource(R.string.receive_from, file.from) + "  ·  " + file.time.relativeTime(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(state: ServerState, onStart: () -> Unit, onStop: () -> Unit, onNewCode: () -> Unit) {
    val context = LocalContext.current
    val copiedLabel = stringResource(R.string.copied)
    val copy: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("", text))
        android.widget.Toast.makeText(context, copiedLabel, android.widget.Toast.LENGTH_SHORT).show()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.running) stringResource(R.string.receive_server_on) else stringResource(R.string.receive_server_off), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (state.running) OutlinedButton(onClick = onStop) { Text(stringResource(R.string.receive_stop)) }
                else Button(onClick = onStart) { Text(stringResource(R.string.receive_start)) }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (state.running) {
                val url = state.urls.firstOrNull()
                if (url != null) {
                    Text(stringResource(R.string.receive_open_url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CopyRow(url, copy)
                    val qr = remember(url) { qrBitmap(url, 512) }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(bitmap = qr.asImageBitmap(), contentDescription = url, modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp))
                    }
                    if (state.urls.size > 1) {
                        Text(stringResource(R.string.receive_other), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        state.urls.drop(1).forEach { alt -> CopyRow(alt, copy) }
                    }
                } else {
                    Text(stringResource(R.string.receive_no_network, state.port), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.receive_code), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.code.chunked(3).joinToString(" "), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onNewCode))
                    }
                    TextButton(onClick = onNewCode) { Text(stringResource(R.string.receive_new_code)) }
                }

                var showHelp by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showHelp = !showHelp }.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.receive_help_toggle), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Icon(if (showHelp) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showHelp) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.receive_help_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.receive_help_step1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        CopyRow("adb forward tcp:${state.port} tcp:${state.port}", copy)
                        Text(stringResource(R.string.receive_help_step2), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        CopyRow("http://localhost:${state.port}/?code=${state.code}", copy)
                    }
                }
            }
        }
    }
}

/** A mono line with a copy button, for URLs and commands. */
@Composable
private fun CopyRow(text: String, onCopy: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(text, style = MonoStyle.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        IconButton(onClick = { onCopy(text) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
