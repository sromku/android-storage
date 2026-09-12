package com.snatik.storage.app.feature.data

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

data class UrisUiState(
    val running: Boolean = true,
    val phase: UriDiscovery.Phase = UriDiscovery.Phase.READING,
    val scanned: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val dexIndex: Int = 0,
    val dexCount: Int = 0,
    val paths: List<String> = emptyList(),
    val error: String? = null,
    /** True once a scoped (app-code-only) scan finished with no results — offer a full scan. */
    val offerScanAll: Boolean = false,
)

class ProviderUrisViewModel(route: Route.ProviderUris, private val discovery: UriDiscovery) : ViewModel() {
    val authority = route.authority
    val label = route.label
    private val pkg = route.packageName
    private val providerClass = route.className

    private val _state = MutableStateFlow(UrisUiState())
    val state: StateFlow<UrisUiState> = _state.asStateFlow()
    private var job: Job? = null

    init { start(scanAll = false) }

    fun start(scanAll: Boolean) {
        job?.cancel()
        _state.value = UrisUiState(running = true)
        job = viewModelScope.launch {
            try {
                discovery.discover(pkg, providerClass, authority, scanAll).collect { e ->
                    when (e) {
                        is UriDiscovery.Event.Status -> _state.update { it.copy(phase = e.phase, scanned = e.scanned, total = e.total, found = e.found, dexIndex = e.dexIndex, dexCount = e.dexCount) }
                        is UriDiscovery.Event.Done -> _state.update {
                            it.copy(running = false, paths = e.paths, found = e.paths.size, offerScanAll = e.scopedOnly && e.paths.isEmpty())
                        }
                    }
                }
            } catch (e: UriDiscovery.DiscoveryException) {
                _state.update { it.copy(running = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(running = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun scanAll() = start(scanAll = true)

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun uriFor(path: String) = "content://$authority/$path"

    override fun onCleared() { job?.cancel() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderUrisScreen(
    route: Route.ProviderUris,
    onBack: () -> Unit,
    onOpenProvider: (title: String, uri: String) -> Unit,
    viewModel: ProviderUrisViewModel = koinViewModel(parameters = { parametersOf(route) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(viewModel.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.uri_discover_title), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.running) {
                val phaseLabel = when (state.phase) {
                    UriDiscovery.Phase.READING -> stringResource(R.string.uri_phase_reading)
                    UriDiscovery.Phase.LOADING -> stringResource(R.string.uri_phase_loading)
                    UriDiscovery.Phase.SCANNING ->
                        if (state.dexCount > 0) stringResource(R.string.uri_phase_scanning_dex, state.dexIndex, state.dexCount)
                        else stringResource(R.string.uri_phase_scanning)
                }
                val scanning = state.phase == UriDiscovery.Phase.SCANNING && state.total > 0
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(phaseLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (scanning) {
                        LinearProgressIndicator(
                            progress = { state.scanned.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                            drawStopIndicator = {},
                            gapSize = 0.dp,
                        )
                        Text(stringResource(R.string.uri_scanned, state.scanned, state.total, state.found), style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = viewModel::cancel, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.uri_cancel)) }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.error != null -> EmptyState(Icons.Default.Link, stringResource(R.string.query_failed), state.error)
                    !state.running && state.paths.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.uri_none), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(if (state.offerScanAll) R.string.uri_none_scoped_body else R.string.uri_none_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        if (state.offerScanAll) {
                            Spacer(Modifier.height(12.dp))
                            androidx.compose.material3.Button(onClick = viewModel::scanAll) { Text(stringResource(R.string.uri_scan_all)) }
                        }
                    }
                    else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(state.paths, key = { it }) { path ->
                            UriRow(uri = viewModel.uriFor(path), pattern = path.any { it == '#' || it == '*' }, onClick = { onOpenProvider(path, viewModel.uriFor(path)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UriRow(uri: String, pattern: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(uri, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        if (pattern) Text(stringResource(R.string.uri_pattern_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}
