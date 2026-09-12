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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.snatik.storage.core.apps.AppFootprint
import com.snatik.storage.core.apps.AppStorageAnalyzer
import com.snatik.storage.core.apps.CompileMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs

private val AppFootprint.oatBytes get() = slices.firstOrNull { it.label.contains("OAT") }?.bytes ?: 0L

/** The result of one compile action, for the before/after footprint diff. */
data class ArtOp(
    val mode: CompileMode,
    val beforeOat: Long,
    val afterOat: Long,
    val beforeTotal: Long,
    val afterTotal: Long,
    val newStatus: String?,
    val millis: Long,
)

data class ArtUiState(
    val loading: Boolean = true,
    val shellAvailable: Boolean = false,
    val status: String? = null,
    val footprint: AppFootprint? = null,
    val busy: Boolean = false,
    val runningMode: CompileMode? = null,
    val ops: List<ArtOp> = emptyList(),
    val error: String? = null,
)

class ArtViewModel(
    private val packageName: String,
    private val actions: AppActions,
    private val analyzer: AppStorageAnalyzer,
) : ViewModel() {
    private val _state = MutableStateFlow(ArtUiState(shellAvailable = actions.available))
    val state: StateFlow<ArtUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val status = runCatching { actions.compilationFilter(packageName) }.getOrNull()
            val footprint = runCatching { analyzer.analyze(packageName) }.getOrNull()
            _state.update { it.copy(loading = false, status = status, footprint = footprint) }
        }
    }

    fun run(mode: CompileMode) {
        if (_state.value.busy) return
        viewModelScope.launch {
            val before = _state.value.footprint ?: runCatching { analyzer.analyze(packageName) }.getOrNull()
            _state.update { it.copy(busy = true, runningMode = mode, error = null) }
            val start = System.currentTimeMillis()
            try {
                actions.compile(packageName, mode)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, runningMode = null, error = e.message ?: e.toString()) }
                return@launch
            }
            val after = runCatching { analyzer.analyze(packageName) }.getOrNull()
            val status = runCatching { actions.compilationFilter(packageName) }.getOrNull()
            val op = ArtOp(
                mode = mode,
                beforeOat = before?.oatBytes ?: 0, afterOat = after?.oatBytes ?: 0,
                beforeTotal = before?.total ?: 0, afterTotal = after?.total ?: 0,
                newStatus = status, millis = System.currentTimeMillis() - start,
            )
            _state.update { it.copy(busy = false, runningMode = null, status = status, footprint = after, ops = listOf(op) + it.ops) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtScreen(packageName: String, label: String, onBack: () -> Unit, viewModel: ArtViewModel = koinViewModel(parameters = { parametersOf(packageName) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            Text(stringResource(R.string.art_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.art_optimizations), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            MODES.forEach { info ->
                ModeCard(info, running = state.runningMode == info.mode, enabled = !state.busy) { viewModel.run(info.mode) }
            }

            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            if (state.ops.isNotEmpty()) {
                Text(stringResource(R.string.art_recent), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                state.ops.forEach { OpRow(it) }
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
            val fp = state.footprint
            if (fp != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Metric(stringResource(R.string.art_compiled), fp.oatBytes.readableSize())
                    Metric(stringResource(R.string.art_app_total), fp.total.readableSize())
                }
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
private fun OpRow(op: ArtOp) {
    val oatDelta = op.afterOat - op.beforeOat
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(op.mode.name.lowercase().replace('_', '-'), style = MonoStyle, fontWeight = FontWeight.Medium)
                Text("→ ${op.newStatus ?: "?"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text("· ${op.millis} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DeltaText(stringResource(R.string.art_compiled), op.beforeOat, op.afterOat)
                DeltaText(stringResource(R.string.art_app_total), op.beforeTotal, op.afterTotal)
            }
        }
    }
}

@Composable
private fun DeltaText(label: String, before: Long, after: Long) {
    val delta = after - before
    val color = when {
        delta < 0 -> MaterialTheme.colorScheme.secondary // reclaimed
        delta > 0 -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = if (delta > 0) "+" else if (delta < 0) "−" else "±"
    Text(
        "$label: ${before.readableSize()} → ${after.readableSize()}  ($sign${abs(delta).readableSize()})",
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

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
