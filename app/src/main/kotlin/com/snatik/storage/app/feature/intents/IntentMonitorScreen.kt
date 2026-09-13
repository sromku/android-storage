package com.snatik.storage.app.feature.intents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.intents.FullDataResult
import com.snatik.storage.core.intents.MonitoredIntent
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentMonitorScreen(onBack: () -> Unit, onRerun: (String) -> Unit, viewModel: IntentMonitorViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val self = context.packageName
    var selected by remember { mutableStateOf<MonitoredIntent?>(null) }
    var showStart by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val visible = remember(state.intents, state.hideSelf, state.query) {
        state.intents.filter { i ->
            (!state.hideSelf || (i.callerPackage != self && i.packageName != self)) &&
                (state.query.isBlank() || listOfNotNull(i.action, i.data, i.type, i.packageName, i.callerPackage).any { it.contains(state.query, true) })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.intent_monitor_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { viewModel.refresh(); scope.launch { listState.animateScrollToItem(0) } }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.intent_monitor_refresh)) }
                    if (state.shellAvailable) {
                        IconButton(onClick = { if (state.running) viewModel.stop() else showStart = true }) {
                            if (state.running) Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.intent_monitor_stop))
                            else Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.intent_monitor_resume))
                        }
                    }
                    IconButton(onClick = viewModel::clear, enabled = state.count > 0) { Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.log_clear)) }
                },
            )
        },
    ) { padding ->
        if (!state.shellAvailable) {
            EmptyState(Icons.Default.Sensors, stringResource(R.string.intent_monitor_needs_shell), stringResource(R.string.intent_monitor_needs_shell_hint), modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = state.hideSelf, onClick = { viewModel.setHideSelf(!state.hideSelf) }, label = { Text(stringResource(R.string.intent_monitor_hide_self)) })
                val status = if (state.running) R.string.intent_monitor_listening else R.string.intent_monitor_paused
                Text(
                    stringResource(status, "%,d".format(visible.size), "%,d".format(state.capacity)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            if (visible.isEmpty()) {
                EmptyState(Icons.Default.Sensors, stringResource(R.string.intent_monitor_empty), stringResource(R.string.intent_monitor_empty_hint))
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(visible, key = { it.id }) { intent -> MonitorRow(intent, onClick = { selected = intent }) }
                }
            }
        }
    }

    if (showStart) {
        ConfirmStartSheet(
            currentCapacity = state.capacity,
            capacities = viewModel.capacities,
            onStart = { cap -> viewModel.start(cap); showStart = false },
            onDismiss = { showStart = false },
        )
    }

    selected?.let { intent ->
        DetailSheet(
            intent = intent,
            onResolve = { viewModel.resolveFullData(it) },
            onRerun = { json -> onRerun(json); selected = null },
            onDismiss = { selected = null },
        )
    }
}

private sealed interface ResolveState {
    data object Idle : ResolveState
    data object Loading : ResolveState
    data class Done(val data: String) : ResolveState
    data object Ambiguous : ResolveState
    data object NotFound : ResolveState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheet(
    intent: MonitoredIntent,
    onResolve: suspend (MonitoredIntent) -> FullDataResult,
    onRerun: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var resolve by remember(intent.id) { mutableStateOf<ResolveState>(ResolveState.Idle) }
    val effectiveData = (resolve as? ResolveState.Done)?.data ?: intent.data
    val spec = remember(intent.id, effectiveData) { intent.toSpec().copy(data = effectiveData) }
    val truncated = intent.data?.endsWith("...") == true

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            intent.callerPackage?.let { AppLine(stringResource(R.string.intent_monitor_role_from), it) }
            intent.packageName?.let { AppLine(stringResource(R.string.intent_monitor_role_to), it) }
            HorizontalDivider()
            IntentDetails(spec)
            if (truncated) {
                when (resolve) {
                    ResolveState.Idle -> TextButton(onClick = {
                        resolve = ResolveState.Loading
                        scope.launch { resolve = onResolve(intent).toState() }
                    }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.intent_monitor_resolve)) }
                    ResolveState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.intent_monitor_resolving), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is ResolveState.Done -> Unit // shown in details above
                    ResolveState.Ambiguous -> Text(stringResource(R.string.intent_monitor_resolve_ambiguous), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    ResolveState.NotFound -> Text(stringResource(R.string.intent_monitor_resolve_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            intent.callerUid?.let { Text(stringResource(R.string.intent_monitor_caller_uid, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (intent.hasExtras) Text(stringResource(R.string.intent_monitor_has_extras), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Button(onClick = { onRerun(spec.toJson()) }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.intent_monitor_rerun))
                }
            }
        }
    }
}

private fun FullDataResult.toState(): ResolveState = when (this) {
    is FullDataResult.Resolved -> ResolveState.Done(data)
    FullDataResult.Ambiguous -> ResolveState.Ambiguous
    FullDataResult.NotFound -> ResolveState.NotFound
}

/** A role (From / To) with the resolved app icon and label. */
@Composable
private fun AppLine(role: String, packageName: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(packageName, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(role, MaterialTheme.colorScheme.primary)
                Text(appLabel(packageName), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmStartSheet(currentCapacity: Int, capacities: List<Int>, onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var capacity by remember { mutableIntStateOf(currentCapacity.takeIf { it in capacities } ?: capacities.first()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.intent_monitor_start_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.intent_monitor_start_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.intent_monitor_capacity_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                capacities.forEachIndexed { i, c ->
                    SegmentedButton(selected = capacity == c, onClick = { capacity = c }, shape = SegmentedButtonDefaults.itemShape(i, capacities.size)) {
                        Text("%,d".format(c))
                    }
                }
            }
            Text(stringResource(R.string.intent_monitor_capacity_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onStart(capacity) }) { Text(stringResource(R.string.intent_monitor_start_action)) }
            }
        }
    }
}

@Composable
private fun MonitorRow(intent: MonitoredIntent, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        intent.packageName?.let { AppIcon(it, size = 36.dp) }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(intent.action?.substringAfterLast('.') ?: stringResource(R.string.intent_no_action), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (intent.hasExtras) Tag(stringResource(R.string.intent_monitor_extras_tag), MaterialTheme.colorScheme.tertiary)
                Text(intent.time.relativeTime(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            intent.packageName?.let { Text("→ $it", style = MonoStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) }
            val detail = listOfNotNull(intent.data, intent.type, intent.callerPackage?.let { stringResource(R.string.intent_monitor_from, it) }).joinToString("  ·  ")
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
