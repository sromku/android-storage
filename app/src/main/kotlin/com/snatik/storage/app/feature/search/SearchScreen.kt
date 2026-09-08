package com.snatik.storage.app.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.FileSearch
import com.snatik.storage.core.apps.SearchHit
import com.snatik.storage.core.apps.SearchMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class SearchViewModel(private val search: FileSearch) : ViewModel() {
    private val _hits = MutableStateFlow<List<SearchHit>>(emptyList())
    val hits: StateFlow<List<SearchHit>> = _hits.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    var searched = false; private set
    private var job: Job? = null

    fun run(root: String, query: String, mode: SearchMode) {
        job?.cancel()
        searched = true
        _hits.value = emptyList()
        _running.value = true
        job = viewModelScope.launch {
            try {
                val batch = ArrayList<SearchHit>()
                search.search(root, query, mode).collect { hit ->
                    batch.add(hit)
                    if (batch.size % 20 == 0) _hits.value = ArrayList(batch)
                }
                _hits.value = batch
            } finally {
                _running.value = false
            }
        }
    }
}

private data class Root(val label: String, val path: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenPath: (String) -> Unit, viewModel: SearchViewModel = koinViewModel()) {
    val hits by viewModel.hits.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SearchMode.NAME) }
    val roots = listOf(
        Root(stringResource(R.string.search_root_shared), "/storage/emulated/0"),
        Root(stringResource(R.string.search_root_system), "/"),
        Root(stringResource(R.string.search_root_data), "/data/data"),
    )
    var root by remember { mutableStateOf(roots.first()) }

    fun go() { if (query.isNotBlank()) viewModel.run(root.path, query.trim(), mode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                trailingIcon = { IconButton(onClick = ::go) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_title)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(horizontal = 16.dp)) {
                SegmentedButton(selected = mode == SearchMode.NAME, onClick = { mode = SearchMode.NAME }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.search_by_name)) }
                SegmentedButton(selected = mode == SearchMode.CONTENT, onClick = { mode = SearchMode.CONTENT }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.search_by_content)) }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                roots.forEach { r ->
                    FilterChip(selected = root == r, onClick = { root = r }, label = { Text(r.label) })
                }
            }
            if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !viewModel.searched -> EmptyState(Icons.Default.Search, stringResource(R.string.search_prompt), stringResource(R.string.search_prompt_body))
                    hits.isEmpty() && !running -> EmptyState(Icons.Default.Search, stringResource(R.string.search_none), null)
                    else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item { if (hits.isNotEmpty()) Text(stringResource(R.string.search_count, hits.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 8.dp)) }
                        items(hits.size) { i -> Hit(hits[i], onOpenPath) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hit(hit: SearchHit, onOpenPath: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onOpenPath(hit.path) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(hit.path.substringAfterLast('/'), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(hit.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val line = hit.line
        if (line != null) Text(line, style = MonoStyle, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}
