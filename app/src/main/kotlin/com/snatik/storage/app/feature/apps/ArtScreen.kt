package com.snatik.storage.app.feature.apps

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
            val raw = try {
                actions.compile(packageName, mode)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, runningMode = null, error = e.message ?: e.toString()) }
                return@launch
            }
            val files = com.snatik.storage.core.apps.parseDexoptResults(raw)
            val finalStatus = com.snatik.storage.core.apps.dexoptFinalStatus(raw)
            val status = runCatching { actions.compilationFilter(packageName) }.getOrNull()
            val storage = runCatching { apps.storage(packageName) }.getOrNull()
            val afterCode = storage?.appBytes ?: beforeCode
            val afterTotal = storage?.totalBytes ?: beforeTotal
            log.add(
                packageName,
                ArtOp(
                    mode = mode, fromStatus = fromStatus, toStatus = status,
                    artifactBefore = files.sumOf { it.sizeBeforeBytes }, artifactAfter = files.sumOf { it.sizeBytes },
                    beforeTotal = beforeTotal, afterTotal = afterTotal,
                    files = files, finalStatus = finalStatus, raw = raw, millis = System.currentTimeMillis() - start,
                ),
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
    onOpenOp: (ArtOp) -> Unit,
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
            val currentMode = modeForStatus(state.status)
            MODES.forEach { info ->
                // Debuggable apps can't be AOT-compiled, so the actions are inert — disable them.
                ModeCard(
                    info,
                    running = state.runningMode == info.mode,
                    enabled = !state.busy && !debuggable,
                    current = info.mode == currentMode,
                ) { viewModel.run(info.mode) }
            }

            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            if (ops.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.art_recent), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (ops.size > RECENT_LIMIT) {
                        TextButton(onClick = onViewAllOps) { Text(stringResource(R.string.art_view_all, ops.size)) }
                    }
                }
                ops.take(RECENT_LIMIT).forEach { op -> OpRow(op, onClick = { onOpenOp(op) }) }
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
private fun ModeCard(info: ModeInfo, running: Boolean, enabled: Boolean, current: Boolean, onClick: () -> Unit) {
    val iconBg = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val iconTint = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = if (current) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).alpha(if (enabled || current) 1f else 0.45f),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(42.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) {
                if (running) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = iconTint)
                else Icon(info.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(info.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(stringResource(info.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (current) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Text(
                        stringResource(R.string.art_current_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** Which optimization card matches the app's current compilation filter, so it can be badged. */
private fun modeForStatus(status: String?): CompileMode? = when (status) {
    "speed" -> CompileMode.SPEED
    "speed-profile" -> CompileMode.SPEED_PROFILE
    "everything", "everything-profile" -> CompileMode.EVERYTHING
    "verify" -> CompileMode.VERIFY
    else -> null
}

@Composable
fun OpRow(op: ArtOp, onClick: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
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
                if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DeltaRow(stringResource(R.string.art_artifacts), op.artifactBefore, op.artifactAfter)
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
fun ArtHistoryScreen(packageName: String, label: String, onBack: () -> Unit, onOpenOp: (ArtOp) -> Unit, log: ArtOpLog = koinInject()) {
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
            ops.forEach { op -> OpRow(op, onClick = { onOpenOp(op) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtOpDetailScreen(packageName: String, opTs: Long, label: String, onBack: () -> Unit, log: ArtOpLog = koinInject()) {
    val ops by log.ops(packageName).collectAsStateWithLifecycle()
    val op = ops.firstOrNull { it.ts == opTs }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(op?.mode?.name?.lowercase()?.replace('_', '-') ?: stringResource(R.string.compile_title), style = MaterialTheme.typography.titleMedium)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        if (op == null) {
            EmptyState(Icons.Default.Block, stringResource(R.string.art_op_gone), null)
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Summary
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(op.fromStatus ?: "?", style = MonoStyle, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("→", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(op.toStatus ?: "?", style = MonoStyle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    op.finalStatus?.let { Text(stringResource(R.string.art_final_status, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                    val wall = op.files.sumOf { it.wallMs }.takeIf { it > 0 } ?: op.millis
                    val cpu = op.files.sumOf { it.cpuMs }
                    Text(stringResource(R.string.art_timing, wall, cpu), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                }
            }

            if (op.files.isNotEmpty()) {
                Text(stringResource(R.string.art_dex_files, op.files.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                op.files.forEach { FileResultCard(it) }
            }

            RawOutputSection(op.raw)
        }
    }
}

@Composable
private fun FileResultCard(f: com.snatik.storage.core.apps.DexoptFileResult) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(f.simpleFile, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                val ok = f.status.equals("PERFORMED", true)
                Surface(color = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Text(f.status, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            DetailLine(stringResource(R.string.art_filter_abi), "${f.filter} · ${f.abi}")
            DeltaRow(stringResource(R.string.art_artifacts), f.sizeBeforeBytes, f.sizeBytes)
            DetailLine(stringResource(R.string.art_dex2oat), stringResource(R.string.art_timing, f.wallMs, f.cpuMs))
            if (f.flags != "[]") DetailLine(stringResource(R.string.art_flags), f.flags)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Strip the noisy async-job chatter dexopt prints so the log reads as pure compile output. */
private fun cleanRaw(raw: String): String =
    raw.lineSequence()
        .filterNot { val t = it.trim(); t.startsWith("Job running") || t.contains("pm art cancel") }
        .joinToString("\n")
        .trim()

/**
 * Collapsible terminal-style presentation of the verbose compile log. Collapsed by default
 * because the per-dex cards above already surface the meaningful fields; expanded, it shows the
 * cleaned log in a dark, horizontally-scrollable mono block with a copy action.
 */
@Composable
private fun RawOutputSection(raw: String) {
    val text = remember(raw) { cleanRaw(raw).ifBlank { "—" } }
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = !expanded },
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.art_raw_output), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.art_raw_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.art_hide_raw else R.string.art_show_raw),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            val clipboard = LocalClipboardManager.current
            var copied by remember { mutableStateOf(false) }
            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("compile -v", style = MonoStyle, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(text))
                            copied = true
                        }) {
                            Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.inversePrimary, modifier = Modifier.size(16.dp))
                            Text(
                                stringResource(if (copied) R.string.art_copied else R.string.art_copy),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.inversePrimary,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Text(
                            text,
                            style = MonoStyle,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        )
                    }
                }
            }
        }
    }
}
