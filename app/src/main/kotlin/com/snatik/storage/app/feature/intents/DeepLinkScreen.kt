package com.snatik.storage.app.feature.intents

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
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
import com.snatik.storage.core.intents.HostPaths
import com.snatik.storage.core.intents.IntentSender
import com.snatik.storage.core.intents.IntentSpec
import com.snatik.storage.core.intents.ResolvedTarget
import com.snatik.storage.core.intents.SchemeLink
import com.snatik.storage.core.intents.SendAs
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class DeepLinkUiState(val url: String = "https://", val targets: List<ResolvedTarget>? = null)

data class DiscoveryUiState(val loading: Boolean = false, val error: String? = null, val data: DeepLinkData? = null)

/** Lazily-loaded per-app web hosts with their example paths, keyed by package in the VM. */
data class AppLinksUi(val loading: Boolean = false, val error: String? = null, val hosts: List<HostPaths>? = null)

/** Lazily-mined example URIs for a custom scheme, keyed by scheme in the VM. */
data class SchemeExamplesUi(val loading: Boolean = false, val error: String? = null, val examples: List<String>? = null)

class DeepLinkViewModel(
    private val sender: IntentSender,
    private val catalog: DeepLinkCatalog,
    private val privilege: PrivilegeManager,
) : ViewModel() {
    private val _state = MutableStateFlow(DeepLinkUiState())
    val state: StateFlow<DeepLinkUiState> = _state.asStateFlow()

    private val _discovery = MutableStateFlow(DiscoveryUiState())
    val discovery: StateFlow<DiscoveryUiState> = _discovery.asStateFlow()

    private val _appLinks = MutableStateFlow<Map<String, AppLinksUi>>(emptyMap())
    val appLinks: StateFlow<Map<String, AppLinksUi>> = _appLinks.asStateFlow()

    private val _schemeExamples = MutableStateFlow<Map<String, SchemeExamplesUi>>(emptyMap())
    val schemeExamples: StateFlow<Map<String, SchemeExamplesUi>> = _schemeExamples.asStateFlow()

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

    fun loadAppLinks(packageName: String) {
        val current = _appLinks.value[packageName]
        if (current?.loading == true || current?.hosts != null) return
        viewModelScope.launch {
            _appLinks.update { it + (packageName to AppLinksUi(loading = true)) }
            val shell = privilege.executor.value
            if (shell == null) {
                _appLinks.update { it + (packageName to AppLinksUi(error = "no-shell")) }
                return@launch
            }
            try {
                val hosts = catalog.fetchAppLinks(shell, packageName)
                _appLinks.update { it + (packageName to AppLinksUi(hosts = hosts)) }
            } catch (e: Exception) {
                _appLinks.update { it + (packageName to AppLinksUi(error = e.message ?: e.toString())) }
            }
        }
    }

    fun loadSchemeExamples(scheme: String, packageName: String) {
        val current = _schemeExamples.value[scheme]
        if (current?.loading == true || current?.examples != null) return
        viewModelScope.launch {
            _schemeExamples.update { it + (scheme to SchemeExamplesUi(loading = true)) }
            val shell = privilege.executor.value
            if (shell == null) {
                _schemeExamples.update { it + (scheme to SchemeExamplesUi(error = "no-shell")) }
                return@launch
            }
            try {
                val examples = catalog.schemeExamples(shell, packageName, scheme)
                _schemeExamples.update { it + (scheme to SchemeExamplesUi(examples = examples)) }
            } catch (e: Exception) {
                _schemeExamples.update { it + (scheme to SchemeExamplesUi(error = e.message ?: e.toString())) }
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
    val appLinks by viewModel.appLinks.collectAsStateWithLifecycle()
    val schemeExamples by viewModel.schemeExamples.collectAsStateWithLifecycle()
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
                DeepLinkMode.DISCOVER -> Discover(
                    state = discovery,
                    appLinks = appLinks,
                    schemeExamples = schemeExamples,
                    onLoadAppLinks = viewModel::loadAppLinks,
                    onLoadSchemeExamples = viewModel::loadSchemeExamples,
                    onPick = { url -> viewModel.setUrl(url); mode = DeepLinkMode.COMPOSE },
                )
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
private fun Discover(
    state: DiscoveryUiState,
    appLinks: Map<String, AppLinksUi>,
    schemeExamples: Map<String, SchemeExamplesUi>,
    onLoadAppLinks: (String) -> Unit,
    onLoadSchemeExamples: (String, String) -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val sectionOpen = remember { mutableStateMapOf<String, Boolean>() }
    var selectedScheme by remember { mutableStateOf<SchemeLink?>(null) }
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
                        .map { (pkg, links) -> pkg to links.map { it.domain }.distinct().sorted() }
                        .sortedByDescending { it.second.size }
                }
                val schemes = remember(data, q) {
                    data.schemes.filter { q.isBlank() || it.scheme.contains(q, true) || it.packages.any { p -> p.contains(q, true) } }
                }
                val domainTotal = remember(webGroups) { webGroups.sumOf { it.second.size } }
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    item(key = "hint") {
                        Text(stringResource(R.string.deeplink_discover_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    item(key = "search") {
                        OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text(stringResource(R.string.deeplink_search)) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    }
                    if (webGroups.isEmpty() && schemes.isEmpty()) {
                        item { EmptyState(Icons.Default.Search, stringResource(R.string.deeplink_discover_empty), null, modifier = Modifier.padding(top = 24.dp)) }
                    }
                    if (webGroups.isNotEmpty()) {
                        val webOpen = sectionOpen["web"] != false
                        item(key = "web-header") {
                            SectionHeader(stringResource(R.string.deeplink_web_links), stringResource(R.string.deeplink_web_count, domainTotal, webGroups.size), webOpen, { sectionOpen["web"] = !webOpen }, Modifier.padding(top = 4.dp, bottom = 6.dp))
                        }
                        if (webOpen) webGroups.forEach { (pkg, domains) ->
                            val isOpen = expanded[pkg] == true
                            val links = appLinks[pkg]
                            item(key = "web:$pkg") {
                                WebAppHeader(pkg, domains.size, isOpen, topOnly = isOpen, modifier = Modifier.padding(top = 8.dp)) {
                                    val next = !isOpen
                                    expanded[pkg] = next
                                    if (next) onLoadAppLinks(pkg)
                                }
                            }
                            if (isOpen) {
                                if (links?.loading == true) {
                                    item(key = "load:$pkg") { HostLoading(pkg) }
                                } else {
                                    val pathsByHost = links?.hosts?.associate { it.host to it.paths } ?: emptyMap()
                                    val lastDomain = domains.lastOrNull()
                                    items(domains, key = { "host:$pkg:$it" }) { domain ->
                                        HostRow(domain, pathsByHost[domain].orEmpty(), q, bottom = domain == lastDomain, onPick = onPick)
                                    }
                                }
                            }
                        }
                    }
                    if (schemes.isNotEmpty()) {
                        val schemesOpen = sectionOpen["schemes"] != false
                        item(key = "scheme-header") {
                            SectionHeader(stringResource(R.string.deeplink_schemes), stringResource(R.string.deeplink_scheme_count, schemes.size), schemesOpen, { sectionOpen["schemes"] = !schemesOpen }, Modifier.padding(top = 16.dp, bottom = 6.dp))
                        }
                        if (schemesOpen) items(schemes, key = { "scheme:" + it.scheme }) { s -> SchemeRow(s, Modifier.padding(top = 8.dp)) { selectedScheme = s } }
                    }
                }
            }
        }
    }

    selectedScheme?.let { scheme ->
        SchemeExamplesSheet(
            scheme = scheme,
            examples = schemeExamples[scheme.scheme],
            onLoad = { onLoadSchemeExamples(scheme.scheme, scheme.packages.first()) },
            onPick = { url -> selectedScheme = null; onPick(url) },
            onDismiss = { selectedScheme = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchemeExamplesSheet(
    scheme: SchemeLink,
    examples: SchemeExamplesUi?,
    onLoad: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(scheme.scheme) { onLoad() }
    val pkg = scheme.packages.first()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(pkg, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(scheme.scheme + "://", style = MaterialTheme.typography.titleLarge)
                    Text(appLabel(pkg) + (if (scheme.packages.size > 1) "  ·  " + stringResource(R.string.deeplink_scheme_apps, scheme.packages.size) else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ExampleRow(scheme.scheme + "://", stringResource(R.string.deeplink_examples_root)) { onPick(scheme.scheme + "://") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            when {
                examples == null || examples.loading -> {
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.deeplink_examples_source, appLabel(pkg)).substringBefore(" —"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                examples.error == "no-shell" -> Text(stringResource(R.string.deeplink_discover_needs_shell), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                examples.error != null -> Text(stringResource(R.string.deeplink_examples_error, examples.error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                examples.examples.isNullOrEmpty() -> Text(stringResource(R.string.deeplink_examples_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Text(stringResource(R.string.deeplink_examples_source, appLabel(pkg)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val prefix = scheme.scheme + "://"
                    val groups = remember(examples.examples) {
                        examples.examples.groupBy { it.removePrefix(prefix).takeWhile { c -> c != '/' && c != '?' && c != '#' } }
                            .toList().sortedBy { it.first }
                    }
                    groups.forEach { (host, uris) ->
                        if (uris.size >= 2) {
                            Text(host, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                            uris.forEach { uri ->
                                val remainder = uri.removePrefix("$prefix$host")
                                ExampleRow(remainder.ifEmpty { uri }, null, Modifier.padding(start = 8.dp)) { onPick(uri) }
                            }
                        } else {
                            ExampleRow(uris.first(), null) { onPick(uris.first()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleRow(uri: String, subtitle: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(uri, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = stringResource(if (expanded) R.string.deeplink_collapse else R.string.deeplink_expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The collapsible app row for a web-links group. Top-rounded when expanded so the host rows below attach. */
@Composable
private fun WebAppHeader(packageName: String, count: Int, expanded: Boolean, topOnly: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val shape = if (topOnly) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp) else MaterialTheme.shapes.medium
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = shape, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(packageName, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(appLabel(packageName), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.deeplink_domains, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.deeplink_collapse else R.string.deeplink_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The "loading paths" row shown under an expanded app while its dumpsys runs. */
@Composable
private fun HostLoading(packageName: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
        }
    }
}

/** One verified host as a link row (its own lazy item), with each example path as an indented sub-row. */
@Composable
private fun HostRow(host: String, paths: List<String>, query: String, bottom: Boolean, onPick: (String) -> Unit) {
    val concrete = remember(paths, query) {
        paths.filter { it.isNotEmpty() }.filter { query.isBlank() || it.contains(query, true) || host.contains(query, true) }
    }
    val shape = if (bottom) RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp) else RectangleShape
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onPick("https://$host/") }.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("https://$host", style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
            concrete.forEach { path ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick("https://$host$path") }.padding(start = 40.dp, end = 14.dp, top = 2.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    Text(path, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
            }
        }
    }
}

@Composable
private fun SchemeRow(scheme: SchemeLink, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(scheme.packages.first(), size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(scheme.scheme + "://", style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                Text(appLabel(scheme.packages.first()) + (if (scheme.packages.size > 1) "  ·  " + stringResource(R.string.deeplink_scheme_apps, scheme.packages.size) else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
