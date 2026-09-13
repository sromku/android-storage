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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
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
    onNetwork: (String) -> Unit,
    onStorage: (String) -> Unit,
    onArt: (label: String, packageName: String, debuggable: Boolean) -> Unit,
    onOpenPrefs: (String) -> Unit = {},
    viewModel: AppDetailViewModel = koinViewModel(parameters = { parametersOf(packageName) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    val onOpen = { viewModel.launchIntent()?.let(context::startActivity); Unit }
    val onSettings = { context.startActivity(viewModel.settingsIntent()) }
    val onPlayStore = {
        val market = android.content.Intent(android.content.Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        runCatching { context.startActivity(market) }.onFailure {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
        }
        Unit
    }

    LaunchedEffect(viewModel, resources) {
        viewModel.messages.collect { message ->
            if (message == "uninstalled") { onBack(); return@collect }
            val text = when {
                message == "done" -> resources.getString(R.string.action_done)
                message.startsWith("exported:") -> resources.getString(R.string.apk_exported, message.removePrefix("exported:"))
                message.startsWith("compile:") -> resources.getString(R.string.compile_done, message.removePrefix("compile:"))
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
                actions = {
                    state.details?.let { d ->
                        AppActionsMenu(
                            details = d,
                            shellAvailable = state.shellAvailable,
                            onPlayStore = onPlayStore,
                            onExport = viewModel::exportApk,
                            onForceStop = viewModel::forceStop,
                            onClearCache = viewModel::clearCache,
                            onClearData = viewModel::requestClearData,
                            onUninstall = viewModel::requestUninstall,
                            onArt = { onArt(d.summary.label, d.summary.packageName, d.summary.isDebuggable) },
                        )
                    }
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
                                DetailTab.BEHAVIOR -> R.string.tab_behavior
                                DetailTab.PREFERENCES -> R.string.tab_preferences
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
                            onNetwork = { onNetwork(details.summary.packageName) },
                            onStorage = { onStorage(details.summary.packageName) },
                            onOpen = onOpen,
                            onSettings = onSettings,
                            onBrowse = onBrowse,
                            onDiskUsage = onDiskUsage,
                        )
                        DetailTab.BEHAVIOR -> BehaviorTab(state.watch, state.watchLoading)
                        DetailTab.PREFERENCES -> PreferencesTab(state, viewModel.prefsDir, onOpenPrefs)
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${s.versionName ?: ""} (${s.versionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                if (s.isSystem) Tag(stringResource(R.string.chip_system))
                if (s.isDebuggable) Tag(stringResource(R.string.chip_debuggable), MaterialTheme.colorScheme.tertiary)
                if (!s.isEnabled) Tag(stringResource(R.string.chip_disabled), MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Overflow menu in the app bar: the management, ART and export actions, out of the body's way. */
@Composable
private fun AppActionsMenu(
    details: AppDetails,
    shellAvailable: Boolean,
    onPlayStore: () -> Unit,
    onExport: () -> Unit,
    onForceStop: () -> Unit,
    onClearCache: () -> Unit,
    onClearData: () -> Unit,
    onUninstall: () -> Unit,
    onArt: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val error = MaterialTheme.colorScheme.error
    IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        fun run(action: () -> Unit) { open = false; action() }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_play_store)) },
            leadingIcon = { Icon(Icons.Default.Shop, contentDescription = null) },
            onClick = { run(onPlayStore) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_export_apk)) },
            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
            onClick = { run(onExport) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_force_stop)) },
            leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) },
            enabled = shellAvailable,
            onClick = { run(onForceStop) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_clear_cache)) },
            leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
            enabled = shellAvailable,
            onClick = { run(onClearCache) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_clear_data), color = if (shellAvailable) error else MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = if (shellAvailable) error else disabledTint()) },
            enabled = shellAvailable,
            onClick = { run(onClearData) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_uninstall), color = if (shellAvailable && !details.summary.isSystem) error else MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = if (shellAvailable && !details.summary.isSystem) error else disabledTint()) },
            enabled = shellAvailable && !details.summary.isSystem,
            onClick = { run(onUninstall) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.compile_title)) },
            leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null) },
            onClick = { run(onArt) },
        )
        if (!shellAvailable) {
            Text(
                stringResource(R.string.shell_actions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(240.dp),
            )
        }
    }
}

@Composable
private fun disabledTint() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

/** Compact primary actions: one tap each to open, tweak, or inspect the app. */
@Composable
private fun QuickActions(launchable: Boolean, onOpen: () -> Unit, onSettings: () -> Unit, onNetwork: () -> Unit, onStorage: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (launchable) QuickAction(Icons.AutoMirrored.Filled.OpenInNew, R.string.action_open, Modifier.weight(1f), onOpen)
        QuickAction(Icons.Default.Settings, R.string.action_settings, Modifier.weight(1f), onSettings)
        QuickAction(Icons.Default.Lan, R.string.net_title, Modifier.weight(1f), onNetwork)
        QuickAction(Icons.Default.PieChart, R.string.app_storage_title, Modifier.weight(1f), onStorage)
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OverviewTab(
    details: AppDetails,
    onNetwork: () -> Unit,
    onStorage: () -> Unit,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onBrowse: (String, String) -> Unit,
    onDiskUsage: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val s = details.summary
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val storage = s.storage
                if (storage != null) {
                    StorageBar(storage.appBytes, storage.dataBytes, storage.cacheBytes, storage.totalBytes)
                } else {
                    Text(stringResource(R.string.usage_access_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                QuickActions(
                    launchable = details.launchable,
                    onOpen = onOpen,
                    onSettings = onSettings,
                    onNetwork = onNetwork,
                    onStorage = onStorage,
                )
            }
        }
        item { HorizontalDivider() }
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
private fun StorageBar(app: Long, data: Long, cache: Long, total: Long) {
    val denom = (app + data + cache).coerceAtLeast(1)
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.outline)
    val labels = listOf(stringResource(R.string.storage_app), stringResource(R.string.storage_data), stringResource(R.string.storage_cache))
    val values = listOf(app, data, cache)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.storage_section), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(total.readableSize(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        // One continuous rounded bar; segments sit flush against each other, no per-piece corners.
        Row(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
            values.forEachIndexed { i, v ->
                val weight = v.toFloat() / denom
                if (weight > 0f) Box(modifier = Modifier.weight(weight).fillMaxSize().background(colors[i]))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            values.forEachIndexed { i, v ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(colors[i]))
                    Text("${labels[i]} ${v.readableSize()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Key/value shown label-on-top, value below — the layout every field on this screen follows. */
@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MonoStyle) }
    }
}

@Composable
private fun PathRow(label: String, path: String, onBrowse: () -> Unit, onAnalyze: (() -> Unit)? = null) {
    val tonal = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(path, style = MonoStyle) }
        }
        FilledTonalIconButton(onClick = onBrowse, modifier = Modifier.size(34.dp), colors = tonal) {
            Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.browse), modifier = Modifier.size(18.dp))
        }
        if (onAnalyze != null) {
            FilledTonalIconButton(onClick = onAnalyze, modifier = Modifier.size(34.dp), colors = tonal) {
                Icon(Icons.Default.DonutLarge, contentDescription = stringResource(R.string.analyze), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PreferencesTab(state: AppDetailUiState, prefsDir: String?, onOpenPrefs: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text(stringResource(R.string.prefs_tab_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            prefsDir?.let {
                Text(it, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            }
        }
        val files = state.prefsFiles
        when {
            files == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            files.isEmpty() -> item {
                EmptyState(
                    Icons.Default.Tune,
                    stringResource(R.string.prefs_none),
                    if (state.prefsError != null) stringResource(R.string.prefs_none_hint) else null,
                )
            }
            else -> items(files, key = { it.path }) { f ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenPrefs(f.path) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.size.readableSize(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                CodeView(lines = lines, wrap = false, lineNumbers = numbers, language = com.snatik.storage.app.ui.highlight.HlLanguage.XML, highlight = query.ifEmpty { null }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ComponentsTab(details: AppDetails) {
    // Collapsed section keys, kept across navigation so long lists don't bury the rest.
    val collapsed = rememberSaveable { mutableStateListOf<String>() }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        componentSection("act", R.string.section_activities, details.activities, collapsed)
        componentSection("svc", R.string.section_services, details.services, collapsed)
        componentSection("rcv", R.string.section_receivers, details.receivers, collapsed)
        section("prov", R.string.section_providers, details.providers, collapsed) { ProviderRow(it) }
        section("defp", R.string.section_defined_permissions, details.definedPermissions, collapsed) { NameAndPath(it) }
    }
}

private fun <T> LazyListScope.section(key: String, titleRes: Int, items: List<T>, collapsed: MutableList<String>, row: @Composable (T) -> Unit) {
    item(key = "h:$key") { SectionHeader(stringResource(titleRes), items.size, key in collapsed) { collapsed.toggle(key) } }
    if (key !in collapsed) {
        if (items.isEmpty()) item(key = "e:$key") { NothingDeclared() }
        items(items, key = { "$key:$it".hashCode() }) { row(it) }
    }
}

private fun LazyListScope.componentSection(key: String, titleRes: Int, comps: List<Component>, collapsed: MutableList<String>) =
    section(key, titleRes, comps, collapsed) { ComponentRow(it) }

private fun MutableList<String>.toggle(key: String) { if (key in this) remove(key) else add(key) }

@Composable
private fun SectionHeader(title: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NothingDeclared() {
    Text(stringResource(R.string.nothing_declared), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

/** A fully-qualified name shown readably: the simple name in front, its package path dimmed below. */
@Composable
private fun NameAndPath(fullName: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(fullName.substringAfterLast('.'), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val path = fullName.substringBeforeLast('.', "")
            if (path.isNotEmpty()) Text(path, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        trailing?.invoke(this)
    }
}

@Composable
private fun ComponentRow(component: Component) {
    Column {
        NameAndPath(component.name) {
            if (component.exported) Tag(stringResource(R.string.chip_exported), MaterialTheme.colorScheme.error)
            if (!component.enabled) Tag(stringResource(R.string.chip_disabled))
        }
        component.permission?.let {
            Text(stringResource(R.string.permission_requires, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp))
        }
    }
}

@Composable
private fun ProviderRow(provider: ContentProvider) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer(modifier = Modifier.weight(1f)) { Text(provider.authority, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) }
            if (provider.exported) Tag(stringResource(R.string.chip_exported), MaterialTheme.colorScheme.error)
        }
        Text(provider.name, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        provider.readPermission?.let { Text(stringResource(R.string.provider_read, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        provider.writePermission?.let { Text(stringResource(R.string.provider_write, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private enum class PermGroupBy { NONE, STATUS, PROTECTION, CATEGORY }

@Composable
private fun permGroupLabel(g: PermGroupBy): String = stringResource(
    when (g) {
        PermGroupBy.NONE -> R.string.perm_by_none
        PermGroupBy.STATUS -> R.string.perm_by_status
        PermGroupBy.PROTECTION -> R.string.perm_by_protection
        PermGroupBy.CATEGORY -> R.string.perm_by_category
    },
)

@Composable
private fun PermissionsTab(details: AppDetails, shellAvailable: Boolean, onToggle: (String, Boolean) -> Unit) {
    val perms = details.permissions
    var groupBy by rememberSaveable { mutableStateOf(PermGroupBy.NONE) }
    val expanded = rememberSaveable { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    LaunchedEffect(groupBy) { listState.scrollToItem(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        PermControls(
            groupBy = groupBy,
            onGroupBy = { groupBy = it },
            allExpanded = perms.isNotEmpty() && expanded.size >= perms.size,
            onToggleAll = {
                if (expanded.size >= perms.size) expanded.clear()
                else { expanded.clear(); expanded.addAll(perms.map { it.name }) }
            },
        )
        if (shellAvailable) Text(stringResource(R.string.grant_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (perms.isEmpty()) item { NothingDeclared() }
            groupPermissions(perms, groupBy).forEach { (header, list) ->
                if (header != null) item(key = "ph:$header") {
                    Text("$header · ${list.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))
                }
                items(list, key = { it.name }) { p ->
                    PermissionRow(p, shellAvailable, p.name in expanded, onExpand = { expanded.toggle(p.name) }, onToggle = onToggle)
                }
            }
        }
    }
}

private fun groupPermissions(perms: List<RequestedPermission>, by: PermGroupBy): List<Pair<String?, List<RequestedPermission>>> = when (by) {
    PermGroupBy.NONE -> listOf(null to perms)
    PermGroupBy.STATUS -> listOf(
        "Granted" to perms.filter { it.granted },
        "Denied" to perms.filterNot { it.granted },
    ).filter { it.second.isNotEmpty() }
    PermGroupBy.PROTECTION -> {
        val order = listOf("dangerous", "signature", "normal", "internal")
        perms.groupBy { it.protection.substringBefore(" ").ifBlank { "unknown" } }
            .toList()
            .sortedBy { order.indexOf(it.first).let { i -> if (i < 0) order.size else i } }
            .map { (k, v) -> k.replaceFirstChar { c -> c.uppercase() } to v }
    }
    PermGroupBy.CATEGORY -> perms.groupBy { it.group ?: "Ungrouped" }.toList().sortedBy { it.first.lowercase() }
}

@Composable
private fun PermControls(groupBy: PermGroupBy, onGroupBy: (PermGroupBy) -> Unit, allExpanded: Boolean, onToggleAll: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        var menu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { menu = true }) {
                Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.perm_group_prefix, permGroupLabel(groupBy)))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                PermGroupBy.entries.forEach { g ->
                    DropdownMenuItem(
                        text = { Text(permGroupLabel(g)) },
                        trailingIcon = if (g == groupBy) ({ Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }) else null,
                        onClick = { onGroupBy(g); menu = false },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onToggleAll) {
            Icon(if (allExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (allExpanded) R.string.perm_collapse_all else R.string.perm_expand_all))
        }
    }
}

@Composable
private fun PermissionRow(permission: RequestedPermission, shellAvailable: Boolean, expanded: Boolean, onExpand: () -> Unit, onToggle: (String, Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(permission.name.substringAfterLast('.'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(permission.name, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
            if (permission.isRuntime) Tag(stringResource(R.string.chip_dangerous), MaterialTheme.colorScheme.error)
            Tag(
                stringResource(if (permission.granted) R.string.chip_granted else R.string.chip_denied),
                if (permission.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                permission.label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface) }
                permission.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (permission.protection.isNotBlank()) DetailLine(stringResource(R.string.perm_protection), permission.protection)
                permission.group?.let { DetailLine(stringResource(R.string.perm_category), it) }
                if (permission.isRuntime && shellAvailable) {
                    TextButton(onClick = { onToggle(permission.name, !permission.granted) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Icon(if (permission.granted) Icons.Default.Block else Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (permission.granted) R.string.perm_revoke else R.string.perm_grant))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
