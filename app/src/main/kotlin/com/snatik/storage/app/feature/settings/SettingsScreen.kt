package com.snatik.storage.app.feature.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.ColorMode
import com.snatik.storage.app.ui.theme.DarkMode
import com.snatik.storage.app.ui.theme.ThemePreferences
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit = {},
    prefs: ThemePreferences = koinInject(),
    netPrefs: com.snatik.storage.app.feature.network.NetworkPreferences = koinInject(),
    sink: com.snatik.storage.core.apps.ExternalSink = koinInject(),
) {
    val settings by prefs.state.collectAsStateWithLifecycle()
    val resolveHosts by netPrefs.resolveHosts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SettingGroup(Icons.Default.Palette, stringResource(R.string.settings_theme)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SegmentedField(stringResource(R.string.settings_appearance)) {
                        val modes = listOf(
                            DarkMode.SYSTEM to stringResource(R.string.theme_system_short),
                            DarkMode.LIGHT to stringResource(R.string.theme_light),
                            DarkMode.DARK to stringResource(R.string.theme_dark),
                        )
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = settings.darkMode == mode,
                                onClick = { prefs.setDarkMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                    val dynAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    SegmentedField(stringResource(R.string.settings_color)) {
                        SegmentedButton(
                            selected = settings.colorMode == ColorMode.COLORFUL,
                            onClick = { prefs.setColorMode(ColorMode.COLORFUL) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text(stringResource(R.string.color_colorful), maxLines = 1) }
                        SegmentedButton(
                            selected = settings.colorMode == ColorMode.DYNAMIC,
                            enabled = dynAvailable,
                            onClick = { if (dynAvailable) prefs.setColorMode(ColorMode.DYNAMIC) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text(stringResource(R.string.color_dynamic_short), maxLines = 1) }
                    }
                    if (!dynAvailable) Text(stringResource(R.string.color_dynamic_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingGroup(Icons.Default.Lan, stringResource(R.string.settings_network)) {
                SwitchRow(
                    stringResource(R.string.settings_resolve_hosts),
                    resolveHosts,
                    stringResource(R.string.settings_resolve_hosts_sub),
                ) { netPrefs.setResolveHosts(it) }
            }
            SettingGroup(Icons.Default.CloudUpload, stringResource(R.string.settings_collector)) {
                ExternalCollector(sink, snackbar)
            }
            SettingGroup(Icons.Default.Info, stringResource(R.string.settings_about)) {
                NavRow(
                    stringResource(R.string.settings_licenses),
                    stringResource(R.string.settings_licenses_sub),
                    onClick = onOpenLicenses,
                )
            }
        }
    }
}

@Composable
private fun NavRow(label: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ExternalCollector(sink: com.snatik.storage.core.apps.ExternalSink, snackbar: SnackbarHostState) {
    val enabled by sink.enabledFlow.collectAsStateWithLifecycle()
    val url by sink.url.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var field by remember { mutableStateOf(url) }
    var testing by remember { mutableStateOf(false) }
    val okMsg = stringResource(R.string.settings_collector_ok)
    val failMsg = stringResource(R.string.settings_collector_fail)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_collector_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwitchRowInline(stringResource(R.string.settings_collector_enable), enabled) { sink.setEnabled(it) }
        OutlinedTextField(
            value = field,
            onValueChange = { field = it; sink.setUrl(it) },
            label = { Text(stringResource(R.string.settings_collector_url)) },
            placeholder = { Text("http://192.168.1.20:8899") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.settings_collector_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                testing = true
                scope.launch {
                    val ok = sink.test().isSuccess
                    testing = false
                    snackbar.showSnackbar(if (ok) okMsg else failMsg)
                }
            },
            enabled = !testing && field.isNotBlank(),
        ) { Text(stringResource(R.string.settings_collector_test)) }
    }
}

@Composable
private fun SwitchRowInline(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedField(label: String, content: @Composable androidx.compose.material3.SingleChoiceSegmentedButtonRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, subtitle: String? = null, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingGroup(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}
