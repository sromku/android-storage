package com.snatik.storage.app.feature.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.CodeView
import com.snatik.storage.app.ui.highlight.Highlighter
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.util.Intents
import com.snatik.storage.app.util.readableSize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.material.icons.filled.Block

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(
    path: String,
    onBack: () -> Unit,
    onViewAsHex: () -> Unit,
    viewModel: TextViewerViewModel = koinViewModel(parameters = { parametersOf(path) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.name, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.truncated) stringResource(R.string.showing_first, state.loadedBytes.readableSize(), state.totalSize.readableSize())
                            else state.totalSize.readableSize(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) }
                },
                actions = {
                    if (state.canPretty) {
                        IconToggleButton(checked = state.pretty, onCheckedChange = { viewModel.togglePretty() }) {
                            Icon(Icons.Default.DataObject, contentDescription = stringResource(R.string.pretty_print))
                        }
                    }
                    IconToggleButton(checked = state.wrap, onCheckedChange = { viewModel.toggleWrap() }) {
                        Icon(Icons.AutoMirrored.Filled.WrapText, contentDescription = stringResource(R.string.wrap_lines))
                    }
                    var more by remember { mutableStateOf(false) }
                    IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.view_as_hex)) }, onClick = { more = false; onViewAsHex() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.open_with)) }, onClick = { more = false; Intents.openWith(context, path) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = { more = false; Intents.share(context, listOf(path)) })
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.lines.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.cannot_read_file), state.error)
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    if (state.looksBinary) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.looks_binary), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = onViewAsHex) { Text(stringResource(R.string.view_as_hex)) }
                            }
                        }
                    }
                    CodeView(
                        lines = state.lines,
                        wrap = state.wrap,
                        language = Highlighter.languageForFile(state.name),
                        truncated = state.truncated,
                        onLoadMore = viewModel::loadMore,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
