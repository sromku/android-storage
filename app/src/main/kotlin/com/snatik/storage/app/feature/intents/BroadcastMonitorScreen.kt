package com.snatik.storage.app.feature.intents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.fullDateTime
import com.snatik.storage.core.intents.BroadcastEvent
import com.snatik.storage.core.intents.BroadcastMonitor
import org.koin.androidx.compose.koinViewModel

class BroadcastMonitorViewModel(private val monitor: BroadcastMonitor) : ViewModel() {
    val events = monitor.events
    val active = monitor.active
    val catalogue = BroadcastMonitor.CATALOGUE
    fun toggle(action: String, scheme: String? = null) = monitor.setActive(action, action !in active.value, scheme)
    fun stopAll() = monitor.stopAll()
    fun clear() = monitor.clear()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastMonitorScreen(onBack: () -> Unit, viewModel: BroadcastMonitorViewModel = koinViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    var custom by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<BroadcastEvent?>(null) }
    var pickerOpen by remember { mutableStateOf(active.isEmpty()) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.monitor_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    TextButton(onClick = viewModel::stopAll, enabled = active.isNotEmpty()) { Text(stringResource(R.string.monitor_stop_all)) }
                    IconButton(onClick = viewModel::clear, enabled = events.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.log_clear)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "actions") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { pickerOpen = !pickerOpen }.padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.monitor_actions) + " · " + active.size, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(if (pickerOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                    if (!pickerOpen && active.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            active.sorted().forEach { a -> FilterChip(selected = true, onClick = { viewModel.toggle(a) }, label = { Text(a.substringAfterLast('.').removePrefix("ACTION_"), style = MaterialTheme.typography.labelMedium) }) }
                        }
                    }
                    if (pickerOpen) viewModel.catalogue.groupBy { it.group }.forEach { (group, actions) ->
                        Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            actions.forEach { a ->
                                FilterChip(selected = a.action in active, onClick = { viewModel.toggle(a.action, a.dataScheme) }, label = { Text(a.action.substringAfterLast('.').removePrefix("ACTION_"), style = MaterialTheme.typography.labelMedium) })
                            }
                        }
                    }
                    if (pickerOpen) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text(stringResource(R.string.monitor_custom)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.weight(1f))
                        TextButton(onClick = { if (custom.isNotBlank()) { viewModel.toggle(custom.trim()); custom = "" } }) { Text(stringResource(R.string.monitor_add)) }
                    }
                    val extra = active.filter { a -> viewModel.catalogue.none { it.action == a } }
                    if (pickerOpen && extra.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            extra.forEach { a -> FilterChip(selected = true, onClick = { viewModel.toggle(a) }, label = { Text(a, style = MaterialTheme.typography.labelMedium) }) }
                        }
                    }
                    Text(stringResource(R.string.monitor_events) + " · " + events.size, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
            }
            if (events.isEmpty()) {
                item { EmptyState(Icons.Default.Sensors, stringResource(R.string.monitor_empty), stringResource(R.string.monitor_empty_hint), modifier = Modifier.padding(top = 16.dp)) }
            }
            items(events, key = { it.id }) { event ->
                Column(modifier = Modifier.fillMaxWidth().clickable { selected = event }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(event.spec.action ?: stringResource(R.string.intent_no_action), style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(event.time.fullDateTime(context), event.spec.data, stringResource(R.string.intent_extras_count, event.spec.extras.size)).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    selected?.let { event ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(event.time.fullDateTime(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IntentDetails(event.spec)
            }
        }
    }
}
