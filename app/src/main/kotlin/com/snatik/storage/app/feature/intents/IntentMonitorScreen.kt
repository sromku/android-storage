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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.intents.MonitoredIntent
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentMonitorScreen(onBack: () -> Unit, viewModel: IntentMonitorViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val self = context.packageName
    var selected by remember { mutableStateOf<MonitoredIntent?>(null) }
    var showStart by remember { mutableStateOf(false) }

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
                    stringResource(status, "%,d".format(state.count), "%,d".format(state.capacity)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            if (visible.isEmpty()) {
                EmptyState(Icons.Default.Sensors, stringResource(R.string.intent_monitor_empty), stringResource(R.string.intent_monitor_empty_hint))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
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
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntentDetails(intent.toSpec())
                val caller = intent.callerPackage ?: intent.callerUid?.let { "uid $it" }
                caller?.let { Text(stringResource(R.string.intent_monitor_from, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (intent.hasExtras) Text(stringResource(R.string.intent_monitor_has_extras), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
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
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
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
