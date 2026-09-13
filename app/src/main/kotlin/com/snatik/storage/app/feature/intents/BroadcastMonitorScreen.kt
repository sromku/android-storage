package com.snatik.storage.app.feature.intents

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Switch
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.fullDateTime
import com.snatik.storage.core.intents.BroadcastCatalog
import com.snatik.storage.core.intents.BroadcastEvent
import com.snatik.storage.core.intents.BroadcastStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class BroadcastUiState(
    val running: Boolean = false,
    val capacity: Int = BroadcastStore.DEFAULT_CAPACITY,
    val count: Int = 0,
    val query: String = "",
    val events: List<BroadcastEvent> = emptyList(),
    val active: Set<String> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastMonitorViewModel(private val context: Context, private val store: BroadcastStore) : ViewModel() {

    private val query = MutableStateFlow("")
    private val refreshTrigger = MutableStateFlow(0)

    val catalogue = BroadcastCatalog.actions

    private val monitor = refreshTrigger.flatMapLatest {
        combine(store.recent, store.count, store.running, store.capacity, store.active) { events, count, running, capacity, active ->
            Monitor(events, count, running, capacity, active.map { it.action }.toSet())
        }
    }

    val state: StateFlow<BroadcastUiState> = combine(monitor, query) { m, q ->
        BroadcastUiState(running = m.running, capacity = m.capacity, count = m.count, query = q, events = m.events, active = m.active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BroadcastUiState())

    val capacities: List<Int> = BroadcastStore.CAPACITIES

    fun toggle(action: String, scheme: String? = null) {
        val on = store.active.value.none { it.action == action }
        store.setActive(action, on, scheme)
    }

    fun start(capacity: Int) {
        store.setCapacity(capacity)
        viewModelScope.launch { store.applyCapacity() }
        BroadcastMonitorService.start(context)
    }

    fun stop() = BroadcastMonitorService.stop(context)
    fun refresh() { refreshTrigger.value++ }
    fun clear() { viewModelScope.launch { store.clear() } }
    fun setQuery(q: String) { query.value = q }

    private data class Monitor(val events: List<BroadcastEvent>, val count: Int, val running: Boolean, val capacity: Int, val active: Set<String>)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastMonitorScreen(onBack: () -> Unit, viewModel: BroadcastMonitorViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var custom by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<BroadcastEvent?>(null) }
    var showActions by remember { mutableStateOf(false) }
    var showStart by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val visible = remember(state.events, state.query) {
        if (state.query.isBlank()) state.events
        else state.events.filter { e -> listOfNotNull(e.spec.action, e.spec.data).any { it.contains(state.query, true) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.broadcast_monitor_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { viewModel.refresh(); scope.launch { listState.animateScrollToItem(0) } }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.intent_monitor_refresh)) }
                    IconButton(onClick = { if (state.running) viewModel.stop() else showStart = true }) {
                        if (state.running) Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.intent_monitor_stop))
                        else Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.intent_monitor_resume))
                    }
                    IconButton(onClick = viewModel::clear, enabled = state.count > 0) { Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.log_clear)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "actions") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.broadcast_monitor_subscribed, state.active.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showActions = true }) { Text(stringResource(R.string.broadcast_monitor_choose)) }
                    }
                    if (state.active.isEmpty()) {
                        Text(stringResource(R.string.broadcast_monitor_none_subscribed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                            state.active.sorted().forEach { a -> FilterChip(selected = true, onClick = { viewModel.toggle(a) }, label = { Text(shortAction(a), style = MaterialTheme.typography.labelMedium) }) }
                        }
                    }
                    val status = if (state.running) R.string.intent_monitor_listening else R.string.intent_monitor_paused
                    Text(
                        stringResource(status, "%,d".format(visible.size), "%,d".format(state.count), "%,d".format(state.capacity)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    OutlinedTextField(value = state.query, onValueChange = viewModel::setQuery, label = { Text(stringResource(R.string.search)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                }
                HorizontalDivider()
            }
            if (visible.isEmpty()) {
                item { EmptyState(Icons.Default.Sensors, stringResource(R.string.broadcast_monitor_empty), stringResource(R.string.broadcast_monitor_empty_hint), modifier = Modifier.padding(top = 16.dp)) }
            }
            items(visible, key = { it.id }) { event ->
                Column(modifier = Modifier.fillMaxWidth().clickable { selected = event }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(shortAction(event.spec.action ?: stringResource(R.string.intent_no_action)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (event.spec.extras.isNotEmpty()) Tag(stringResource(R.string.intent_monitor_extras_tag), MaterialTheme.colorScheme.tertiary)
                    }
                    Text(
                        listOfNotNull(event.time.fullDateTime(context), event.spec.data).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showActions) {
        BroadcastActionsSheet(
            catalogue = viewModel.catalogue,
            active = state.active,
            custom = custom,
            onCustomChange = { custom = it },
            onToggle = { action, scheme -> viewModel.toggle(action, scheme) },
            onAddCustom = { if (custom.isNotBlank()) { viewModel.toggle(custom.trim()); custom = "" } },
            onDismiss = { showActions = false },
        )
    }

    if (showStart) {
        BroadcastStartSheet(
            currentCapacity = state.capacity,
            capacities = viewModel.capacities,
            hasActions = state.active.isNotEmpty(),
            onStart = { cap -> viewModel.start(cap); showStart = false },
            onDismiss = { showStart = false },
        )
    }

    selected?.let { event ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(event.time.fullDateTime(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IntentDetails(event.spec)
                Text(stringResource(R.string.broadcast_monitor_full_payload), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BroadcastStartSheet(currentCapacity: Int, capacities: List<Int>, hasActions: Boolean, onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var capacity by remember { mutableIntStateOf(currentCapacity.takeIf { it in capacities } ?: capacities.first()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.broadcast_monitor_start_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.broadcast_monitor_start_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!hasActions) Text(stringResource(R.string.broadcast_monitor_no_actions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BroadcastActionsSheet(
    catalogue: List<com.snatik.storage.core.intents.BroadcastAction>,
    active: Set<String>,
    custom: String,
    onCustomChange: (String) -> Unit,
    onToggle: (String, String?) -> Unit,
    onAddCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(stringResource(R.string.broadcast_monitor_choose), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
                    OutlinedTextField(value = custom, onValueChange = onCustomChange, label = { Text(stringResource(R.string.monitor_custom)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.weight(1f))
                    TextButton(onClick = onAddCustom, enabled = custom.isNotBlank()) { Text(stringResource(R.string.monitor_add)) }
                }
                val extra = active.filter { a -> catalogue.none { it.action == a } }
                extra.forEach { a -> CustomActionRow(a, onRemove = { onToggle(a, null) }) }
            }
            catalogue.groupBy { it.group }.forEach { (group, actions) ->
                item(key = "g:$group") {
                    Text(group.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 2.dp))
                }
                items(actions, key = { it.action }) { a -> ActionRow(a, checked = a.action in active, onToggle = { onToggle(a.action, a.dataScheme) }) }
            }
        }
    }
}

@Composable
private fun ActionRow(action: com.snatik.storage.core.intents.BroadcastAction, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(action.label, style = MaterialTheme.typography.bodyLarge)
            Text(action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(action.action, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun CustomActionRow(action: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onRemove).padding(start = 24.dp, end = 24.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.broadcast_monitor_custom_label), style = MaterialTheme.typography.bodyLarge)
            Text(action, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        Switch(checked = true, onCheckedChange = { onRemove() })
    }
}

private fun shortAction(action: String): String = action.substringAfterLast('.').removePrefix("ACTION_")
