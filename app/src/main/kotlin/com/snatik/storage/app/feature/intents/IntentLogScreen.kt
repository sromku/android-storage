package com.snatik.storage.app.feature.intents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.intents.CapturedIntent
import com.snatik.storage.core.intents.IntentLog
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class IntentLogViewModel(private val log: IntentLog) : ViewModel() {
    val entries = log.entries
    init { viewModelScope.launch { log.load() } }
    fun clear() = viewModelScope.launch { log.clear() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentLogScreen(onBack: () -> Unit, onResend: (String) -> Unit, viewModel: IntentLogViewModel = koinViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<CapturedIntent?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { IconButton(onClick = viewModel::clear, enabled = entries.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.log_clear)) } },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(Icons.Default.Radar, stringResource(R.string.log_empty), stringResource(R.string.log_empty_hint), modifier = Modifier.padding(padding))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { selected = entry }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.spec.action ?: stringResource(R.string.intent_no_action), style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(entry.time.relativeTime(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val detail = listOfNotNull(entry.spec.type, entry.spec.data, entry.referrer?.let { stringResource(R.string.sink_from, it) }, stringResource(R.string.intent_extras_count, entry.spec.extras.size)).joinToString("  ·  ")
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    selected?.let { entry ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntentDetails(entry.spec, entry.clipDescription)
                TextButton(onClick = { selected = null; onResend(entry.spec.toJson()) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.log_resend)) }
            }
        }
    }
}
