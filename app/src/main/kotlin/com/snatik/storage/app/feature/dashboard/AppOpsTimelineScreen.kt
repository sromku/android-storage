package com.snatik.storage.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.AppOpAccess
import com.snatik.storage.core.apps.AppOpState
import com.snatik.storage.core.apps.AppOpsTimeline
import com.snatik.storage.core.shell.PrivilegeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class AppOpsTimelineViewModel(private val timeline: AppOpsTimeline, private val privilege: PrivilegeManager) : ViewModel() {
    private val _entries = MutableStateFlow<List<AppOpAccess>?>(null)
    val entries: StateFlow<List<AppOpAccess>?> = _entries.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    val shell get() = privilege.executor.value != null
    init { load() }
    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _entries.value = timeline.recent()
            _loading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOpsTimelineScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit, viewModel: AppOpsTimelineViewModel = koinViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    var sensitiveOnly by rememberSaveable { mutableStateOf(true) }
    var backgroundOnly by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timeline_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { viewModel.load() }, enabled = !loading) {
                        if (loading && entries != null) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.timeline_refresh))
                    }
                    IconToggleButton(checked = sensitiveOnly, onCheckedChange = { sensitiveOnly = it }) {
                        Icon(Icons.Default.FilterAlt, contentDescription = stringResource(R.string.timeline_sensitive_only), tint = if (sensitiveOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showHelp = true }) { Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.timeline_help)) }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val list = entries
            when {
                !viewModel.shell && list == null -> EmptyState(Icons.Default.Terminal, stringResource(R.string.timeline_needs_shell), null)
                list == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> {
                    val bgCount = remember(list) { list.count { it.background } }
                    val visible = remember(list, sensitiveOnly, backgroundOnly) {
                        list.filter { (!sensitiveOnly || it.sensitive) && (!backgroundOnly || it.background) }
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Background-access banner: the headline forensic signal.
                        if (bgCount > 0) {
                            BackgroundBanner(bgCount, backgroundOnly) { backgroundOnly = !backgroundOnly }
                        }
                        if (visible.isEmpty()) {
                            EmptyState(Icons.Default.FilterAlt, stringResource(R.string.timeline_empty), null)
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                                items(visible, key = { "${it.packageName}|${it.op}|${it.absTime}" }) { e ->
                                    Entry(e) { selectedKey = "${e.packageName}|${e.op}|${e.absTime}" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val sel = selectedKey?.let { key -> entries?.firstOrNull { "${it.packageName}|${it.op}|${it.absTime}" == key } }
    if (sel != null) {
        AccessDetailSheet(sel, onOpenApp = { onOpenApp(it) }, onDismiss = { selectedKey = null })
    }
    if (showHelp) TimelineHelpSheet(onDismiss = { showHelp = false })
}

@Composable
private fun BackgroundBanner(count: Int, active: Boolean, onToggle: () -> Unit) {
    val color = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (active) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Text(
            pluralStringResource(R.plurals.timeline_bg_banner, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(if (active) R.string.timeline_bg_showing else R.string.timeline_bg_show),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@Composable
private fun Entry(e: AppOpAccess, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(e.packageName, size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(e.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                OpTag(e)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StateLabel(e.state)
                Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(humanAgo(e.agoMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.durationMs?.let {
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.timeline_held, humanDuration(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun OpTag(e: AppOpAccess) {
    val color = if (e.sensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        e.op.lowercase(),
        style = MonoStyle.copy(color = color),
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
    )
}

@Composable
private fun StateLabel(state: AppOpState) {
    val (labelRes, color) = when (state) {
        AppOpState.BACKGROUND -> R.string.timeline_state_background to MaterialTheme.colorScheme.error
        AppOpState.FOREGROUND -> R.string.timeline_state_foreground to MaterialTheme.colorScheme.onSurfaceVariant
        AppOpState.FOREGROUND_SERVICE -> R.string.timeline_state_fgservice to MaterialTheme.colorScheme.onSurfaceVariant
        AppOpState.PERSISTENT -> R.string.timeline_state_system to MaterialTheme.colorScheme.onSurfaceVariant
        AppOpState.UNKNOWN -> R.string.timeline_state_unknown to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bg = state == AppOpState.BACKGROUND
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (bg) FontWeight.SemiBold else FontWeight.Normal),
        color = color,
    )
}

/* ---------- Detail sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessDetailSheet(e: AppOpAccess, onOpenApp: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(e.packageName, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(e.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.packageName, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
            }
            HorizontalDivider()
            DetailRow(stringResource(R.string.timeline_detail_op), e.op)
            DetailRow(stringResource(R.string.timeline_detail_state), stringResource(stateLabelRes(e.state)), if (e.background) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            DetailRow(stringResource(R.string.timeline_detail_when), humanAgo(e.agoMs))
            SelectionContainer { DetailRow(stringResource(R.string.timeline_detail_exact), e.absTime) }
            e.durationMs?.let { DetailRow(stringResource(R.string.timeline_detail_duration), humanDuration(it)) }
            if (e.background) {
                Text(stringResource(R.string.timeline_bg_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenApp(e.packageName) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.timeline_open_app, e.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(1f))
    }
}

/* ---------- Help sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.timeline_help_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.timeline_help_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.timeline_help_states_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            HelpState(R.string.timeline_state_background, R.string.timeline_help_background, MaterialTheme.colorScheme.error)
            HelpState(R.string.timeline_state_foreground, R.string.timeline_help_foreground, MaterialTheme.colorScheme.onSurfaceVariant)
            HelpState(R.string.timeline_state_fgservice, R.string.timeline_help_fgservice, MaterialTheme.colorScheme.onSurfaceVariant)
            HelpState(R.string.timeline_state_system, R.string.timeline_help_system, MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.timeline_help_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun HelpState(labelRes: Int, bodyRes: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(stringResource(bodyRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(top = 2.dp))
    }
}

private fun stateLabelRes(state: AppOpState): Int = when (state) {
    AppOpState.BACKGROUND -> R.string.timeline_state_background
    AppOpState.FOREGROUND -> R.string.timeline_state_foreground
    AppOpState.FOREGROUND_SERVICE -> R.string.timeline_state_fgservice
    AppOpState.PERSISTENT -> R.string.timeline_state_system
    AppOpState.UNKNOWN -> R.string.timeline_state_unknown
}

private fun humanAgo(ms: Long): String {
    val s = ms / 1000
    return when { s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60}m ago"; s < 86400 -> "${s / 3600}h ago"; else -> "${s / 86400}d ago" }
}

private fun humanDuration(ms: Long): String {
    val s = ms / 1000
    return when { ms < 1000 -> "${ms}ms"; s < 60 -> "${s}s"; s < 3600 -> "${s / 60}m ${s % 60}s"; else -> "${s / 3600}h ${(s % 3600) / 60}m" }
}
