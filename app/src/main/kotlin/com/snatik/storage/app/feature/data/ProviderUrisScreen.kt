package com.snatik.storage.app.feature.data

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private fun isPattern(path: String) = path.any { it == '#' || it == '*' }

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
                actions = {
                    if (!state.running && (state.paths.isNotEmpty() || state.lastDiscoveredAt != null)) {
                        IconButton(onClick = viewModel::rediscover) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.uri_rediscover)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.permission?.let { PermissionCard(it, state.privileged) }

            if (state.running) RunningHeader(state, viewModel::cancel)

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.error != null -> EmptyState(Icons.Default.Link, stringResource(R.string.query_failed), state.error)
                    !state.running && state.paths.isEmpty() -> EmptyUris(state, viewModel::scanAll)
                    else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.lastDiscoveredAt?.takeIf { !state.running }?.let { at ->
                            item(key = "meta") { DiscoveredMeta(at, state.paths.size, state.scannedAll) }
                        }
                        items(state.paths, key = { it }) { path ->
                            UriRow(
                                uri = viewModel.uriFor(path),
                                pattern = isPattern(path),
                                saved = viewModel.uriFor(path) in state.savedUris,
                                onQuery = { onOpenProvider(path, viewModel.uriFor(path)) },
                                onResolve = { viewModel.resolvePattern(path) },
                                onSave = { viewModel.toggleSave(path) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.resolve?.let { resolve ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::closeResolve, sheetState = sheetState) {
            ResolveSheet(
                resolve = resolve,
                toUri = viewModel::uriFor,
                onQuery = { concrete ->
                    viewModel.closeResolve()
                    onOpenProvider(concrete, viewModel.uriFor(concrete))
                },
            )
        }
    }
}

@Composable
private fun PermissionCard(perm: PermStatus, privileged: Boolean) {
    val held = perm.held
    val icon = if (held) Icons.Default.LockOpen else Icons.Default.Lock
    val tint = if (held) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.uri_perm_line, perm.name.substringAfterLast('.'), perm.protection),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                // Signature/privileged permissions can never be held by a normal app (not even via
                // pm grant) — so make clear there's nothing to grant, unlike a runtime permission.
                val signatureOnly = perm.protection == "signature" || perm.protection == "special"
                val note = when {
                    held -> stringResource(R.string.uri_perm_held)
                    signatureOnly && privileged -> stringResource(R.string.uri_perm_sig_shell)
                    signatureOnly -> stringResource(R.string.uri_perm_sig_need_shell)
                    privileged -> stringResource(R.string.uri_perm_shell)
                    else -> stringResource(R.string.uri_perm_need_shell)
                }
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RunningHeader(state: UrisUiState, onCancel: () -> Unit) {
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
            LinearProgressIndicator(progress = { state.scanned.toFloat() / state.total }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {}, gapSize = 0.dp)
            Text(stringResource(R.string.uri_scanned, state.scanned, state.total, state.found), style = MaterialTheme.typography.bodySmall)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.uri_cancel)) }
    }
}

@Composable
private fun DiscoveredMeta(at: Long, count: Int, scannedAll: Boolean) {
    val rel = DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val scope = stringResource(if (scannedAll) R.string.uri_scope_all else R.string.uri_scope_app)
    Text(
        stringResource(R.string.uri_meta, count, rel, scope),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyUris(state: UrisUiState, onScanAll: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.uri_none), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (state.offerScanAll) R.string.uri_none_scoped_body else R.string.uri_none_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (state.offerScanAll) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(onClick = onScanAll) { Text(stringResource(R.string.uri_scan_all)) }
        }
    }
}

@Composable
private fun UriRow(
    uri: String,
    pattern: Boolean,
    saved: Boolean,
    onQuery: () -> Unit,
    onResolve: () -> Unit,
    onSave: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { if (pattern) onResolve() else onQuery() }.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(uri, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                if (pattern) {
                    Text(stringResource(R.string.uri_pattern_tap), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (saved) Icon(Icons.Default.Bookmark, contentDescription = stringResource(R.string.uri_saved), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.uri_actions)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (pattern) {
                        MenuRow(Icons.Default.Tune, stringResource(R.string.uri_action_find)) { menu = false; onResolve() }
                        MenuRow(Icons.Default.Search, stringResource(R.string.uri_action_query_asis)) { menu = false; onQuery() }
                    } else {
                        MenuRow(Icons.Default.Search, stringResource(R.string.uri_action_query)) { menu = false; onQuery() }
                    }
                    MenuRow(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, stringResource(if (saved) R.string.uri_action_unsave else R.string.uri_action_save)) { menu = false; onSave() }
                    HorizontalDivider()
                    MenuRow(Icons.Default.ContentCopy, stringResource(R.string.uri_action_copy)) { menu = false; clipboard.setText(AnnotatedString(uri)) }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun ResolveSheet(resolve: ResolveState, toUri: (String) -> String, onQuery: (String) -> Unit) {
    val slots = remember(resolve.path) { PatternUris.slots(resolve.path) }
    var inputs by remember(resolve.path) { mutableStateOf(List(slots.size) { "" }) }
    val manualPath = PatternUris.substituteAll(resolve.path, inputs)
    val manualReady = inputs.all { it.isNotBlank() }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("title") {
            Text(
                stringResource(if (resolve.wildcard == '#') R.string.uri_resolve_title_id else R.string.uri_resolve_title_key),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Transparency: the exact collection URI we queried, and how it went.
        item("checked") {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.uri_resolve_checked), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(resolve.parentUri, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                    when {
                        resolve.loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.uri_resolve_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        resolve.error != null -> Text(resolve.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        resolve.values.isNotEmpty() -> Text(stringResource(R.string.uri_resolve_hint, resolve.values.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        else -> Text(stringResource(R.string.uri_resolve_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(resolve.values, key = { "v/" + it.value }) { v ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onQuery(PatternUris.substitute(resolve.path, v.value)) }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(v.value, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    v.label?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        // Manual entry: fill each placeholder yourself and query.
        item("manual") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                HorizontalDivider()
                Text(stringResource(R.string.uri_resolve_manual), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                slots.forEachIndexed { i, (ch, preceding) ->
                    val base = stringResource(if (ch == '#') R.string.uri_resolve_slot_id else R.string.uri_resolve_slot_value)
                    // Label by the preceding path segment when it's a real one; otherwise number the slot.
                    val where = preceding.takeIf { it.isNotEmpty() && it != "#" && it != "*" }
                    val label = if (where != null) "$where · $base" else "$base ${i + 1}"
                    OutlinedTextField(
                        value = inputs[i],
                        onValueChange = { v -> inputs = inputs.toMutableList().also { it[i] = v } },
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(toUri(manualPath), style = MonoStyle.copy(color = if (manualReady) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                Button(onClick = { onQuery(manualPath) }, enabled = manualReady, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.uri_action_query), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
