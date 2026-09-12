package com.snatik.storage.app.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.apps.AppActions
import com.snatik.storage.core.apps.AppRepository
import com.snatik.storage.core.apps.CompileMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs

data class ArtUiState(
    val loading: Boolean = true,
    val shellAvailable: Boolean = false,
    val status: String? = null,
    val codeBytes: Long? = null,
    val totalBytes: Long? = null,
    val busy: Boolean = false,
    val runningMode: CompileMode? = null,
    val error: String? = null,
)

class ArtViewModel(
    private val packageName: String,
    private val actions: AppActions,
    private val apps: AppRepository,
    private val log: ArtOpLog,
) : ViewModel() {
    private val _state = MutableStateFlow(ArtUiState(shellAvailable = actions.available))
    val state: StateFlow<ArtUiState> = _state.asStateFlow()
    val ops: StateFlow<List<ArtOp>> get() = log.ops(packageName)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val status = runCatching { actions.compilationFilter(packageName) }.getOrNull()
            val storage = runCatching { apps.storage(packageName) }.getOrNull()
            _state.update { it.copy(loading = false, status = status, codeBytes = storage?.appBytes, totalBytes = storage?.totalBytes) }
        }
    }

    fun run(mode: CompileMode) {
        if (_state.value.busy) return
        viewModelScope.launch {
            val fromStatus = _state.value.status
            val beforeCode = _state.value.codeBytes ?: 0
            val beforeTotal = _state.value.totalBytes ?: 0
            _state.update { it.copy(busy = true, runningMode = mode, error = null) }
            val start = System.currentTimeMillis()
            try {
                actions.compile(packageName, mode)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, runningMode = null, error = e.message ?: e.toString()) }
                return@launch
            }
            val status = runCatching { actions.compilationFilter(packageName) }.getOrNull()
            val storage = runCatching { apps.storage(packageName) }.getOrNull()
            val afterCode = storage?.appBytes ?: beforeCode
            val afterTotal = storage?.totalBytes ?: beforeTotal
            log.add(
                packageName,
                ArtOp(mode, fromStatus, status, beforeCode, afterCode, beforeTotal, afterTotal, System.currentTimeMillis() - start),
            )
            _state.update { it.copy(busy = false, runningMode = null, status = status, codeBytes = afterCode, totalBytes = afterTotal) }
        }
    }
}

/** Static description of each optimization for the action cards. */
private data class ModeInfo(val mode: CompileMode, val title: Int, val desc: Int, val icon: ImageVector)

private val MODES = listOf(
    ModeInfo(CompileMode.SPEED_PROFILE, R.string.art_speed_profile, R.string.art_speed_profile_desc, Icons.Default.Insights),
    ModeInfo(CompileMode.SPEED, R.string.art_speed, R.string.art_speed_desc, Icons.Default.Speed),
    ModeInfo(CompileMode.EVERYTHING, R.string.art_everything, R.string.art_everything_desc, Icons.Default.Bolt),
    ModeInfo(CompileMode.VERIFY, R.string.art_verify, R.string.art_verify_desc, Icons.Default.VerifiedUser),
    ModeInfo(CompileMode.RESET, R.string.art_reset, R.string.art_reset_desc, Icons.Default.RestartAlt),
)

private const val RECENT_LIMIT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtScreen(
    packageName: String,
    label: String,
    debuggable: Boolean,
    onBack: () -> Unit,
    onViewAllOps: () -> Unit,
    viewModel: ArtViewModel = koinViewModel(parameters = { parametersOf(packageName) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ops by viewModel.ops.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.compile_title), style = MaterialTheme.typography.titleMedium)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        if (!state.shellAvailable) {
            EmptyState(Icons.Default.Block, stringResource(R.string.watch_needs_shell), stringResource(R.string.shell_actions_hint))
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state)
            if (debuggable) NoteCard(stringResource(R.string.art_debuggable_note))
            Text(stringResource(R.string.art_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.art_optimizations), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            MODES.forEach { info ->
                ModeCard(info, running = state.runningMode == info.mode, enabled = !state.busy) { viewModel.run(info.mode) }
            }

            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            if (ops.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.art_recent), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (ops.size > RECENT_LIMIT) {
                        TextButton(onClick = onViewAllOps) { Text(stringResource(R.string.art_view_all, ops.size)) }
                    }
                }
                ops.take(RECENT_LIMIT).forEach { OpRow(it) }
            }
        }
    }
}

@Composable
private fun StatusCard(state: ArtUiState) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.art_current), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(state.status ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (state.busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(statusMeaning(state.status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 4.dp)) {
                Metric(stringResource(R.string.art_code), state.codeBytes?.readableSize() ?: "—")
                Metric(stringResource(R.string.art_app_total), state.totalBytes?.readableSize() ?: "—")
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
    }
}

@Composable
private fun NoteCard(text: String) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun ModeCard(info: ModeInfo, running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                if (running) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                else Icon(info.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(info.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(stringResource(info.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun OpRow(op: ArtOp) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(30.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(iconFor(op.mode), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(17.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(op.mode.name.lowercase().replace('_', '-'), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(op.fromStatus ?: "?", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("→", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val changed = op.fromStatus != op.toStatus
                        Text(op.toStatus ?: "?", style = MonoStyle, color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (changed) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
                Text("${op.millis} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DeltaRow(stringResource(R.string.art_code), op.beforeCode, op.afterCode)
            DeltaRow(stringResource(R.string.art_app_total), op.beforeTotal, op.afterTotal)
        }
    }
}

@Composable
private fun DeltaRow(label: String, before: Long, after: Long) {
    val delta = after - before
    val color = when {
        delta < 0 -> MaterialTheme.colorScheme.secondary
        delta > 0 -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = if (delta > 0) "+" else if (delta < 0) "−" else "±"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${before.readableSize()} → ${after.readableSize()}   $sign${abs(delta).readableSize()}", style = MaterialTheme.typography.labelMedium, color = color)
    }
}

private fun iconFor(mode: CompileMode): ImageVector = MODES.first { it.mode == mode }.icon

@Composable
private fun statusMeaning(status: String?): String = stringResource(
    when (status) {
        "speed" -> R.string.art_mean_speed
        "speed-profile" -> R.string.art_mean_profile
        "everything", "everything-profile" -> R.string.art_mean_everything
        "verify" -> R.string.art_mean_verify
        "quicken", "run-from-apk", "run-from-apk-fallback", "extract" -> R.string.art_mean_none
        else -> R.string.art_mean_unknown
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtHistoryScreen(packageName: String, label: String, onBack: () -> Unit, log: ArtOpLog = koinInject()) {
    val ops by log.ops(packageName).collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.art_recent), style = MaterialTheme.typography.titleMedium)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ops.forEach { OpRow(it) }
        }
    }
}
