package com.snatik.storage.app.feature.intents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.CodeView
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.intents.BroadcastHistory
import com.snatik.storage.core.intents.HistoricalBroadcast
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class HistoryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val raw: String = "",
    val parsed: List<HistoricalBroadcast> = emptyList(),
    val showRaw: Boolean = false,
    val query: String = "",
)

class BroadcastHistoryViewModel(private val history: BroadcastHistory, private val privilege: PrivilegeManager) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val shell = privilege.executor.value
            if (shell == null) {
                _state.update { it.copy(loading = false, error = "no-shell") }
                return@launch
            }
            try {
                val raw = history.fetchRaw(shell)
                _state.update { it.copy(loading = false, raw = raw, parsed = BroadcastHistory.parse(raw)) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun setShowRaw(raw: Boolean) = _state.update { it.copy(showRaw = raw) }
    fun setQuery(query: String) = _state.update { it.copy(query = query) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastHistoryScreen(onBack: () -> Unit, viewModel: BroadcastHistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { IconButton(onClick = viewModel::refresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, contentDescription = null) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error == "no-shell" -> EmptyState(Icons.Default.Block, stringResource(R.string.history_needs_shell), null)
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.query_failed), state.error)
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(selected = !state.showRaw, onClick = { viewModel.setShowRaw(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.history_parsed)) }
                            SegmentedButton(selected = state.showRaw, onClick = { viewModel.setShowRaw(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.history_raw)) }
                        }
                        Text(stringResource(R.string.history_count, state.parsed.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
                    }
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(R.string.history_search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    val query = state.query.trim()
                    if (state.showRaw) {
                        val allLines = remember(state.raw) { state.raw.lines() }
                        val (lines, numbers) = remember(allLines, query) {
                            if (query.isEmpty()) allLines to null
                            else allLines.withIndex().filter { it.value.contains(query, true) }.let { hits -> hits.map { it.value } to hits.map { it.index + 1 } }
                        }
                        CodeView(lines = lines, wrap = false, lineNumbers = numbers, highlight = query.ifEmpty { null }, modifier = Modifier.fillMaxSize())
                    } else {
                        val items = remember(state.parsed, query) { if (query.isEmpty()) state.parsed else state.parsed.filter { it.intent.contains(query, true) } }
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            itemsIndexed(items) { _, b ->
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(b.action ?: b.intent, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (b.queue != "?") Tag(b.queue)
                                    }
                                    Text(listOfNotNull(b.enqueued, b.intent).joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
