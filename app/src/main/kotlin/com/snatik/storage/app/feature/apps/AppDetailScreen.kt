package com.snatik.storage.app.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.CodeView
import com.snatik.storage.app.ui.components.ConfirmDialog
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.fullDateTime
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.apps.AppDetails
import com.snatik.storage.core.apps.Component
import com.snatik.storage.core.apps.ContentProvider
import com.snatik.storage.core.apps.RequestedPermission
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    onBrowse: (label: String, path: String) -> Unit,
    onDiskUsage: (label: String, path: String) -> Unit,
    viewModel: AppDetailViewModel = koinViewModel(parameters = { parametersOf(packageName) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, resources) {
        viewModel.messages.collect { message ->
            val text = when {
                message == "done" -> resources.getString(R.string.action_done)
                message.startsWith("exported:") -> resources.getString(R.string.apk_exported, message.removePrefix("exported:"))
                else -> message
            }
            snackbar.showSnackbar(text)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.details?.summary?.label ?: packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val details = state.details
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                details == null -> EmptyState(Icons.Default.Block, stringResource(R.string.app_not_found), packageName)
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Header(details)
                    PrimaryScrollableTabRow(selectedTabIndex = state.tab.ordinal, edgePadding = 8.dp) {
                        DetailTab.entries.forEach { tab ->
                            val label = when (tab) {
                                DetailTab.OVERVIEW -> R.string.tab_overview
                                DetailTab.MANIFEST -> R.string.tab_manifest
                                DetailTab.COMPONENTS -> R.string.tab_components
                                DetailTab.PERMISSIONS -> R.string.tab_permissions
                            }
                            Tab(selected = state.tab == tab, onClick = { viewModel.selectTab(tab) }, text = { Text(stringResource(label)) })
                        }
                    }
                    if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    when (state.tab) {
                        DetailTab.OVERVIEW -> OverviewTab(
                            details = details,
                            shellAvailable = state.shellAvailable,
                            onOpen = { viewModel.launchIntent()?.let(context::startActivity) },
                            onSettings = { context.startActivity(viewModel.settingsIntent()) },
                            onExport = viewModel::exportApk,
                            onForceStop = viewModel::forceStop,
                            onClearCache = viewModel::clearCache,
                            onClearData = viewModel::requestClearData,
                            onUninstall = viewModel::requestUninstall,
                            onBrowse = onBrowse,
                            onDiskUsage = onDiskUsage,
                        )
                        DetailTab.MANIFEST -> ManifestTab(state, onQuery = viewModel::setManifestQuery)
                        DetailTab.COMPONENTS -> ComponentsTab(details)
                        DetailTab.PERMISSIONS -> PermissionsTab(details, state.shellAvailable, onToggle = viewModel::setPermission)
                    }
                }
            }
        }
    }

    when (state.dialog) {
        null -> Unit
        DetailDialog.ConfirmClearData -> ConfirmDialog(
            title = stringResource(R.string.confirm_clear_data, state.details?.summary?.label ?: packageName),
            body = stringResource(R.string.delete_items_body),
            confirmLabel = stringResource(R.string.action_clear_data),
            destructive = true,
            onConfirm = viewModel::clearData,
            onDismiss = viewModel::dismissDialog,
        )
        DetailDialog.ConfirmUninstall -> ConfirmDialog(
            title = stringResource(R.string.confirm_uninstall, state.details?.summary?.label ?: packageName),
            body = stringResource(R.string.delete_items_body),
            confirmLabel = stringResource(R.string.action_uninstall),
            destructive = true,
            onConfirm = viewModel::uninstall,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

@Composable
private fun Header(details: AppDetails) {
    val s = details.summary
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(s.packageName, size = 56.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SelectionContainer { Text(s.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${s.versionName ?: ""} (${s.versionCode})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.isSystem) Tag(stringResource(R.string.chip_system))
                if (s.isDebuggable) Tag(stringResource(R.string.chip_debuggable), MaterialTheme.colorScheme.tertiary)
                if (!s.isEnabled) Tag(stringResource(R.string.chip_disabled), MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun OverviewTab(
    details: AppDetails,
    shellAvailable: Boolean,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit,
    onForceStop: () -> Unit,
    onClearCache: () -> Unit,
    onClearData: () -> Unit,
    onUninstall: () -> Unit,
    onBrowse: (String, String) -> Unit,
    onDiskUsage: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val s = details.summary
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val storage = s.storage
                if (storage != null) {
                    StorageBar(storage.appBytes, storage.dataBytes, storage.cacheBytes)
                    Text(stringResource(R.string.storage_total, storage.totalBytes.readableSize()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.usage_access_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (details.launchable) AssistChip(onClick = onOpen, label = { Text(stringResource(R.string.action_open)) })
                AssistChip(onClick = onSettings, label = { Text(stringResource(R.string.action_settings)) })
                AssistChip(onClick = onExport, label = { Text(stringResource(R.string.action_export_apk)) })
            }
        }
        item {
            FlowRow(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onForceStop, enabled = shellAvailable, label = { Text(stringResource(R.string.action_force_stop)) })
                AssistChip(onClick = onClearCache, enabled = shellAvailable, label = { Text(stringResource(R.string.action_clear_cache)) })
                AssistChip(onClick = onClearData, enabled = shellAvailable, label = { Text(stringResource(R.string.action_clear_data)) })
                AssistChip(onClick = onUninstall, enabled = shellAvailable && !s.isSystem, label = { Text(stringResource(R.string.action_uninstall)) })
            }
            if (!shellAvailable) {
                Text(stringResource(R.string.shell_actions_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        item { InfoRow(stringResource(R.string.info_uid), s.uid.toString()) }
        item { InfoRow(stringResource(R.string.info_sdk), stringResource(R.string.info_sdk_value, s.targetSdk, details.minSdk)) }
        item { InfoRow(stringResource(R.string.info_installed), s.firstInstallTime.fullDateTime(context)) }
        item { InfoRow(stringResource(R.string.info_updated), s.lastUpdateTime.fullDateTime(context)) }
        details.installer?.let { item { InfoRow(stringResource(R.string.info_installer), it) } }
        item { PathRow(stringResource(R.string.info_apk), s.apkPath, onBrowse = { onBrowse(s.label, s.apkPath.substringBeforeLast('/')) }) }
        if (details.splitApks.isNotEmpty()) item { InfoRow(stringResource(R.string.info_splits), details.splitApks.joinToString("\n") { it.substringAfterLast('/') }) }
        item { PathRow(stringResource(R.string.info_data_dir), details.dataDir, onBrowse = { onBrowse(s.label, details.dataDir) }, onAnalyze = { onDiskUsage(s.label, details.dataDir) }) }
        details.externalDataDir?.let { item { PathRow(stringResource(R.string.info_external_dir), it, onBrowse = { onBrowse(s.label, it) }, onAnalyze = { onDiskUsage(s.label, it) }) } }
        details.nativeLibraryDir?.let { item { PathRow(stringResource(R.string.info_native_libs), it, onBrowse = { onBrowse(s.label, it) }) } }
        items(details.signatures) { sig -> InfoRow(stringResource(R.string.info_signing), sig.sha256 + if (sig.subject.isNotEmpty()) "\n${sig.subject}" else "") }
    }
}

@Composable
private fun StorageBar(app: Long, data: Long, cache: Long) {
    val total = (app + data + cache).coerceAtLeast(1)
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.outline)
    val labels = listOf(stringResource(R.string.storage_app), stringResource(R.string.storage_data), stringResource(R.string.storage_cache))
    val values = listOf(app, data, cache)
    Row(modifier = Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEachIndexed { i, v ->
            val weight = v.toFloat() / total
            if (weight > 0f) Box(modifier = Modifier.weight(weight).fillMaxSize().background(colors[i], MaterialTheme.shapes.extraSmall))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        values.forEachIndexed { i, v ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.width(10.dp).height(10.dp).background(colors[i], MaterialTheme.shapes.extraSmall))
                Text("${labels[i]} ${v.readableSize()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        SelectionContainer(modifier = Modifier.weight(1f)) { Text(value, style = MonoStyle) }
    }
}

@Composable
private fun PathRow(label: String, path: String, onBrowse: () -> Unit, onAnalyze: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
            SelectionContainer(modifier = Modifier.weight(1f)) { Text(path, style = MonoStyle) }
        }
        Row(modifier = Modifier.padding(start = 110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.browse), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBrowse).padding(vertical = 4.dp))
            if (onAnalyze != null) {
                Text(stringResource(R.string.analyze), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onAnalyze).padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun ManifestTab(state: AppDetailUiState, onQuery: (String) -> Unit) {
    val manifest = state.manifest
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.manifestQuery,
            onValueChange = onQuery,
            placeholder = { Text(stringResource(R.string.manifest_search)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.manifestQuery.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            state.manifestError != null -> EmptyState(Icons.Default.Block, stringResource(R.string.cannot_read_file), state.manifestError)
            manifest == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.manifest_loading), style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                val allLines = remember(manifest) { manifest.lines() }
                val query = state.manifestQuery.trim()
                val (lines, numbers) = remember(allLines, query) {
                    if (query.isEmpty()) allLines to null
                    else allLines.withIndex().filter { it.value.contains(query, ignoreCase = true) }.let { hits -> hits.map { it.value } to hits.map { it.index + 1 } }
                }
                CodeView(lines = lines, wrap = false, lineNumbers = numbers, highlight = query.ifEmpty { null }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ComponentsTab(details: AppDetails) {
    val pkg = details.summary.packageName
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        componentSection(R.string.section_activities, details.activities, pkg)
        componentSection(R.string.section_services, details.services, pkg)
        componentSection(R.string.section_receivers, details.receivers, pkg)
        item { SectionTitle(stringResource(R.string.section_providers), details.providers.size) }
        if (details.providers.isEmpty()) item { NothingDeclared() }
        items(details.providers, key = { "p:" + it.name }) { ProviderRow(it, pkg) }
        item { SectionTitle(stringResource(R.string.section_defined_permissions), details.definedPermissions.size) }
        if (details.definedPermissions.isEmpty()) item { NothingDeclared() }
        items(details.definedPermissions, key = { "dp:$it" }) { Text(it, style = MonoStyle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
    }
}

private fun LazyListScope.componentSection(title: Int, items: List<Component>, pkg: String) {
    item { SectionTitle(stringResource(title), items.size) }
    if (items.isEmpty()) item { NothingDeclared() }
    items(items, key = { "$title:" + it.name }) { ComponentRow(it, pkg) }
}

@Composable
private fun SectionTitle(text: String, count: Int) {
    Text(
        "$text · $count",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun NothingDeclared() {
    Text(stringResource(R.string.nothing_declared), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

private fun shortName(name: String, pkg: String): String = if (name.startsWith("$pkg.")) name.removePrefix(pkg) else name

@Composable
private fun ComponentRow(component: Component, pkg: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectionContainer(modifier = Modifier.weight(1f, fill = false)) { Text(shortName(component.name, pkg), style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface)) }
            if (component.exported) Tag(stringResource(R.string.chip_exported), MaterialTheme.colorScheme.error)
            if (!component.enabled) Tag(stringResource(R.string.chip_disabled))
        }
        component.permission?.let { Text(stringResource(R.string.permission_requires, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ProviderRow(provider: ContentProvider, pkg: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectionContainer(modifier = Modifier.weight(1f, fill = false)) { Text(provider.authority, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface)) }
            if (provider.exported) Tag(stringResource(R.string.chip_exported), MaterialTheme.colorScheme.error)
        }
        Text(shortName(provider.name, pkg), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        provider.readPermission?.let { Text(stringResource(R.string.provider_read, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        provider.writePermission?.let { Text(stringResource(R.string.provider_write, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun PermissionsTab(details: AppDetails, shellAvailable: Boolean, onToggle: (String, Boolean) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        if (shellAvailable) {
            item { Text(stringResource(R.string.grant_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        }
        if (details.permissions.isEmpty()) item { NothingDeclared() }
        items(details.permissions, key = { it.name }) { permission -> PermissionRow(permission, shellAvailable, onToggle) }
    }
}

@Composable
private fun PermissionRow(permission: RequestedPermission, shellAvailable: Boolean, onToggle: (String, Boolean) -> Unit) {
    val clickable = shellAvailable && permission.isRuntime
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable { onToggle(permission.name, !permission.granted) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(permission.name.substringAfterLast('.'), style = MaterialTheme.typography.bodyMedium)
            Text(permission.name, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        if (permission.isRuntime) Tag(stringResource(R.string.chip_runtime))
        if (permission.granted) Tag(stringResource(R.string.chip_granted), MaterialTheme.colorScheme.primary) else Tag(stringResource(R.string.chip_denied), MaterialTheme.colorScheme.outline)
    }
}
