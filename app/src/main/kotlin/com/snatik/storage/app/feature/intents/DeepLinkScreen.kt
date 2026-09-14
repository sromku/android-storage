package com.snatik.storage.app.feature.intents

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.intents.DeepLinkCatalog
import com.snatik.storage.core.intents.DeepLinkData
import com.snatik.storage.core.intents.IntentSender
import com.snatik.storage.core.intents.IntentSpec
import com.snatik.storage.core.intents.ResolvedTarget
import com.snatik.storage.core.intents.SchemeLink
import com.snatik.storage.core.intents.SendAs
import com.snatik.storage.core.intents.WebLink
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class DeepLinkUiState(val url: String = "https://", val targets: List<ResolvedTarget>? = null)

data class DiscoveryUiState(val loading: Boolean = false, val error: String? = null, val data: DeepLinkData? = null)

class DeepLinkViewModel(
    private val sender: IntentSender,
    private val catalog: DeepLinkCatalog,
    private val privilege: PrivilegeManager,
) : ViewModel() {
    private val _state = MutableStateFlow(DeepLinkUiState())
    val state: StateFlow<DeepLinkUiState> = _state.asStateFlow()

    private val _discovery = MutableStateFlow(DiscoveryUiState())
    val discovery: StateFlow<DiscoveryUiState> = _discovery.asStateFlow()

    private fun spec() = IntentSpec(action = Intent.ACTION_VIEW, data = _state.value.url.trim(), categories = listOf(Intent.CATEGORY_BROWSABLE), sendAs = SendAs.ACTIVITY)

    fun setUrl(url: String) = _state.update { it.copy(url = url, targets = null) }
    fun resolve() = _state.update { it.copy(targets = runCatching { sender.resolve(spec()) }.getOrDefault(emptyList())) }
    fun launch(target: ResolvedTarget) = runCatching { sender.send(spec().copy(packageName = target.packageName, className = target.className)) }
    fun chooser(title: String): Intent = sender.chooser(spec(), title)

    fun discover(force: Boolean = false) {
        if (!force && (_discovery.value.loading || _discovery.value.data != null)) return
        viewModelScope.launch {
            _discovery.update { it.copy(loading = true, error = null) }
            val shell = privilege.executor.value
            if (shell == null) {
                _discovery.update { it.copy(loading = false, error = "no-shell") }
                return@launch
            }
            try {
                val data = catalog.fetch(shell)
                _discovery.update { it.copy(loading = false, data = data) }
            } catch (e: Exception) {
                _discovery.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }
}

private enum class DeepLinkMode { COMPOSE, DISCOVER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepLinkScreen(onBack: () -> Unit, viewModel: DeepLinkViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val discovery by viewModel.discovery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.deeplink_open_chooser)
    var mode by remember { mutableStateOf(DeepLinkMode.COMPOSE) }

    LaunchedEffect(mode) { if (mode == DeepLinkMode.DISCOVER) viewModel.discover() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deeplink_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    if (mode == DeepLinkMode.DISCOVER) {
                        IconButton(onClick = { viewModel.discover(force = true) }, enabled = !discovery.loading) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.intent_monitor_refresh)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SegmentedButton(selected = mode == DeepLinkMode.COMPOSE, onClick = { mode = DeepLinkMode.COMPOSE }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(stringResource(R.string.deeplink_mode_compose))
                }
                SegmentedButton(selected = mode == DeepLinkMode.DISCOVER, onClick = { mode = DeepLinkMode.DISCOVER }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(stringResource(R.string.deeplink_mode_discover))
                }
            }
            when (mode) {
                DeepLinkMode.COMPOSE -> ComposeTab(state, context, chooserTitle, viewModel)
                DeepLinkMode.DISCOVER -> Discover(discovery) { url -> viewModel.setUrl(url); mode = DeepLinkMode.COMPOSE }
            }
        }
    }
}

@Composable
private fun ComposeTab(state: DeepLinkUiState, context: android.content.Context, chooserTitle: String, viewModel: DeepLinkViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(value = state.url, onValueChange = viewModel::setUrl, label = { Text(stringResource(R.string.deeplink_url)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::resolve) { Text(stringResource(R.string.deeplink_resolve)) }
                OutlinedButton(onClick = { context.startActivity(viewModel.chooser(chooserTitle)) }) { Text(stringResource(R.string.deeplink_open_chooser)) }
            }
        }
        state.targets?.let { targets ->
            if (targets.isEmpty()) {
                item { Text(stringResource(R.string.deeplink_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(targets, key = { it.packageName + "/" + it.className }) { target ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.launch(target) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(target.packageName, size = 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(target.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (target.isDefault) Tag(stringResource(R.string.builder_default), MaterialTheme.colorScheme.primary)
                        }
                        Text(target.className, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                    TextButton(onClick = { viewModel.launch(target) }) { Text(stringResource(R.string.deeplink_launch)) }
                }
            }
        }
    }
}

@Composable
private fun Discover(state: DiscoveryUiState, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.error == "no-shell" -> EmptyState(Icons.Default.Block, stringResource(R.string.deeplink_discover_needs_shell), null)
            state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.query_failed), state.error)
            state.data != null -> {
                val data = state.data
                val q = query.trim()
                val webGroups = remember(data, q) {
                    data.webLinks
                        .filter { q.isBlank() || it.domain.contains(q, true) || it.packageName.contains(q, true) }
                        .groupBy { it.packageName }
                        .toList()
                        .sortedByDescending { it.second.size }
                }
                val schemes = remember(data, q) {
                    data.schemes.filter { q.isBlank() || it.scheme.contains(q, true) || it.packages.any { p -> p.contains(q, true) } }
                }
                val domainTotal = remember(webGroups) { webGroups.sumOf { it.second.size } }
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "hint") {
                        Text(stringResource(R.string.deeplink_discover_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    item(key = "search") {
                        OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text(stringResource(R.string.deeplink_search)) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (webGroups.isEmpty() && schemes.isEmpty()) {
                        item { EmptyState(Icons.Default.Search, stringResource(R.string.deeplink_discover_empty), null, modifier = Modifier.padding(top = 24.dp)) }
                    }
                    if (webGroups.isNotEmpty()) {
                        item(key = "web-header") { SectionHeader(stringResource(R.string.deeplink_web_links), stringResource(R.string.deeplink_web_count, domainTotal, webGroups.size)) }
                        items(webGroups, key = { "web:" + it.first }) { (pkg, links) -> WebLinkGroup(pkg, links, onPick) }
                    }
                    if (schemes.isNotEmpty()) {
                        item(key = "scheme-header") { SectionHeader(stringResource(R.string.deeplink_schemes), stringResource(R.string.deeplink_scheme_count, schemes.size)) }
                        items(schemes, key = { "scheme:" + it.scheme }) { s -> SchemeRow(s, onPick) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WebLinkGroup(packageName: String, links: List<WebLink>, onPick: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppHeader(packageName)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                links.forEach { link ->
                    AssistChip(onClick = { onPick("https://" + link.domain + "/") }, label = { Text(link.domain, style = MonoStyle) })
                }
            }
        }
    }
}

@Composable
private fun SchemeRow(scheme: SchemeLink, onPick: (String) -> Unit) {
    Surface(onClick = { onPick(scheme.scheme + "://") }, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(scheme.packages.first(), size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(scheme.scheme + "://", style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                Text(appLabel(scheme.packages.first()) + (if (scheme.packages.size > 1) "  ·  " + stringResource(R.string.deeplink_scheme_apps, scheme.packages.size) else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AppHeader(packageName: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(packageName, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(appLabel(packageName), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
}

@Composable
private fun appLabel(packageName: String): String {
    val context = LocalContext.current
    return remember(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
    }
}
