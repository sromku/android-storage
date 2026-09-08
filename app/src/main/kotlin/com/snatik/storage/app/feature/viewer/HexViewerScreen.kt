package com.snatik.storage.app.feature.viewer

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexViewerScreen(path: String, onBack: () -> Unit, viewModel: HexViewerViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                else -> HexRows(state, onLoadMore = viewModel::loadMore)
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
