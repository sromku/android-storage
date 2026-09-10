package com.snatik.storage.app.feature.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.apps.BinaryInspector
import com.snatik.storage.core.apps.ProtoField
import com.snatik.storage.core.apps.ProtoValue
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private enum class HexTab { HEX, STRINGS, INSPECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexViewerScreen(path: String, onBack: () -> Unit, viewModel: HexViewerViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(HexTab.HEX) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.name, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.truncated) stringResource(R.string.showing_first, state.bytes.size.toLong().readableSize(), state.totalSize.readableSize())
                            else state.totalSize.readableSize(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.bytes.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.cannot_read_file), state.error)
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                        HexTab.entries.forEach { t ->
                            val label = when (t) {
                                HexTab.HEX -> R.string.hex_tab
                                HexTab.STRINGS -> R.string.strings_tab
                                HexTab.INSPECT -> R.string.inspect_tab
                            }
                            Tab(selected = tab == t, onClick = { tab = t }, text = { Text(stringResource(label)) })
                        }
                    }
                    when (tab) {
                        HexTab.HEX -> HexRows(state, onLoadMore = viewModel::loadMore)
                        HexTab.STRINGS -> StringsTab(state)
                        HexTab.INSPECT -> InspectTab(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun StringsTab(state: HexViewerState) {
    val strings = remember(state.bytes) { BinaryInspector.strings(state.bytes) }
    if (strings.isEmpty()) {
        EmptyState(Icons.Default.Block, stringResource(R.string.strings_none), null)
        return
    }
    val mono = MonoStyle.copy(fontSize = 12.sp, lineHeight = 17.sp)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item { Text(stringResource(R.string.strings_count, strings.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 4.dp)) }
        items(strings) { s ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("%08X".format(s.offset), style = mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.wide) Text("w", style = mono, color = MaterialTheme.colorScheme.tertiary)
                SelectionContainer(modifier = Modifier.weight(1f)) { Text(s.text, style = mono) }
            }
        }
    }
}

@Composable
private fun InspectTab(state: HexViewerState) {
    val detected = remember(state.bytes) { BinaryInspector.detect(state.bytes) }
    val proto = remember(state.bytes) {
        if (BinaryInspector.looksLikeProtobuf(state.bytes)) runCatching { BinaryInspector.decodeProtobuf(state.bytes) }.getOrNull() else null
    }
    val protoLines = remember(proto) { proto?.let { ArrayList<Pair<Int, String>>().also { l -> flattenProto(it, 0, l) } } }
    val mono = MonoStyle.copy(fontSize = 12.sp, lineHeight = 18.sp)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.detected_type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (detected == null) {
                        Text(stringResource(R.string.detected_unknown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(detected.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(detected.mime, style = mono, color = MaterialTheme.colorScheme.primary)
                        if (detected.hint.isNotEmpty()) Text(detected.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (protoLines != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.proto_decoded, protoLines.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        protoLines.forEach { (depth, text) ->
                            Text(text, style = mono, modifier = Modifier.padding(start = (depth * 16).dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun flattenProto(fields: List<ProtoField>, depth: Int, out: MutableList<Pair<Int, String>>) {
    fields.forEach { f ->
        when (val v = f.value) {
            is ProtoValue.VarInt -> out += depth to "#${f.field} varint = ${v.v}"
            is ProtoValue.Fixed32 -> out += depth to "#${f.field} i32 = ${v.v}"
            is ProtoValue.Fixed64 -> out += depth to "#${f.field} i64 = ${v.v}"
            is ProtoValue.Text -> out += depth to "#${f.field} string = \"${v.s.take(120)}\""
            is ProtoValue.Bytes -> out += depth to "#${f.field} bytes[${v.size}] = ${v.preview}…"
            is ProtoValue.Message -> {
                out += depth to "#${f.field} message {"
                flattenProto(v.fields, depth + 1, out)
                out += depth to "}"
            }
        }
    }
}

@Composable
private fun HexRows(state: HexViewerState, onLoadMore: () -> Unit) {
    val style = MonoStyle.copy(fontSize = 12.sp, lineHeight = 18.sp)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val horizontal = rememberScrollState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 16 bytes per row needs roughly 77 monospace columns; phones in portrait get 8.
        val bytesPerRow = if (maxWidth >= 600.dp) 16 else 8
        val rows = remember(state.bytes, bytesPerRow) { (0 until state.rowCount(bytesPerRow)).toList() }
        SelectionContainer {
        LazyColumn(modifier = Modifier.fillMaxSize().horizontalScroll(horizontal), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(rows, key = { it }) { row ->
                val start = row * bytesPerRow
                val end = minOf(start + bytesPerRow, state.bytes.size)
                val hex = buildString {
                    for (i in start until end) {
                        if (i > start) append(if ((i - start) % 8 == 0) "  " else " ")
                        append(HEX[(state.bytes[i].toInt() shr 4) and 0xF]).append(HEX[state.bytes[i].toInt() and 0xF])
                    }
                    repeat((start + bytesPerRow - end)) { append("   ") }
                }
                val ascii = buildString {
                    for (i in start until end) {
                        val c = state.bytes[i].toInt() and 0xFF
                        append(if (c in 0x20..0x7E) c.toChar() else '·')
                    }
                }
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("%08X".format(start), style = style, color = muted, fontWeight = FontWeight.Medium)
                    Text(hex, style = style, modifier = Modifier.padding(start = 16.dp))
                    Text(ascii, style = style, color = muted, modifier = Modifier.padding(start = 16.dp))
                }
            }
            if (state.truncated) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onLoadMore) { Text(stringResource(R.string.load_more)) }
                    }
                }
            }
        }
        }
    }
}

private const val HEX = "0123456789ABCDEF"
