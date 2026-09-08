package com.snatik.storage.app.feature.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.ElfInspector
import com.snatik.storage.core.apps.ElfReport
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import com.snatik.storage.core.shell.shellQuote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Raw ownership and SELinux label, the data a native lstat/lgetxattr would return, via the shell. */
data class RawStat(val mode: String, val owner: String, val group: String, val selinux: String)

class ElfViewModel(private val path: String, private val inspector: ElfInspector, private val privilege: PrivilegeManager) : ViewModel() {
    private val _report = MutableStateFlow<ElfReport?>(null)
    val report: StateFlow<ElfReport?> = _report.asStateFlow()
    private val _raw = MutableStateFlow<RawStat?>(null)
    val raw: StateFlow<RawStat?> = _raw.asStateFlow()
    var loaded = false; private set

    init {
        viewModelScope.launch { _report.value = inspector.inspect(path); loaded = true }
        viewModelScope.launch { _raw.value = readRaw() }
    }

    private suspend fun readRaw(): RawStat? {
        val exec = privilege.executor.value ?: return null
        val q = path.shellQuote()
        val stat = runCatching { exec.run("stat -c '%a %U %G' $q 2>/dev/null", timeoutMs = 10_000).out.trim() }.getOrNull().orEmpty().split(' ')
        val ctx = runCatching { exec.run("ls -Zd $q 2>/dev/null", timeoutMs = 10_000).out.trim().split(Regex("\\s+")).firstOrNull { it.contains(":") } }.getOrNull().orEmpty()
        if (stat.size < 3 && ctx.isEmpty()) return null
        return RawStat(stat.getOrElse(0) { "" }, stat.getOrElse(1) { "" }, stat.getOrElse(2) { "" }, ctx)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElfViewerScreen(path: String, onBack: () -> Unit, onViewAsHex: () -> Unit, viewModel: ElfViewModel = koinViewModel { parametersOf(path) }) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val raw by viewModel.raw.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val r = report
            when {
                r == null && !viewModel.loaded -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                r == null -> EmptyState(Icons.Default.Warning, stringResource(R.string.elf_not_elf), null)
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { HeaderCard(r) }
                    if (r.looksPacked) item { PackedCard(r) }
                    raw?.let { rs -> item { RawCard(rs) } }
                    if (r.needed.isNotEmpty()) item { NeededCard(r.needed) }
                    if (r.sections.isNotEmpty()) item { SectionsCard(r) }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(r: ElfReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(if (r.is64Bit) "ELF64" else "ELF32", MaterialTheme.colorScheme.primary)
                Tag(r.machine, MaterialTheme.colorScheme.tertiary)
                if (r.stripped) Tag("stripped", MaterialTheme.colorScheme.secondary)
            }
            KeyVal(stringResource(R.string.elf_type), r.typeName)
            KeyVal(stringResource(R.string.elf_endian), if (r.littleEndian) "little" else "big")
            KeyVal(stringResource(R.string.elf_entry), "0x%X".format(r.entryPoint))
            r.soname?.let { KeyVal("SONAME", it) }
            r.buildId?.let { KeyVal(stringResource(R.string.elf_build_id), it) }
            KeyVal(stringResource(R.string.elf_entropy), "${r.overallEntropy} / 8.0")
        }
    }
}

@Composable
private fun PackedCard(r: ElfReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column {
                Text(r.packer ?: stringResource(R.string.elf_high_entropy), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.elf_packed_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RawCard(rs: RawStat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.elf_raw), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            if (rs.mode.isNotEmpty()) KeyVal(stringResource(R.string.elf_mode), rs.mode)
            if (rs.owner.isNotEmpty()) KeyVal(stringResource(R.string.elf_owner), "${rs.owner}:${rs.group}")
            if (rs.selinux.isNotEmpty()) KeyVal("SELinux", rs.selinux)
        }
    }
}

@Composable
private fun NeededCard(needed: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.elf_needed, needed.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            needed.forEach { Text(it, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SectionsCard(r: ElfReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.elf_sections, r.sections.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            r.sections.take(40).forEach { s ->
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.name, style = MonoStyle, modifier = Modifier.width(160.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(human(s.size), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                    if (s.entropy > 0) Text("H ${s.entropy}", style = MonoStyle, color = if (s.entropy > 7.2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun KeyVal(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, style = MonoStyle, modifier = Modifier.weight(1f))
    }
}

private fun human(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return "${kotlin.math.round(v * 10) / 10.0} ${units[i]}"
}
