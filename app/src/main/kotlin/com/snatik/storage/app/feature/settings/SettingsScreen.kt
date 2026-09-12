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
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.ColorMode
import com.snatik.storage.app.ui.theme.DarkMode
import com.snatik.storage.app.ui.theme.ThemePreferences
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, prefs: ThemePreferences = koinInject()) {
    val settings by prefs.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SettingGroup(Icons.Default.Brightness6, stringResource(R.string.settings_appearance)) {
                OptionRow(stringResource(R.string.theme_system), settings.darkMode == DarkMode.SYSTEM) { prefs.setDarkMode(DarkMode.SYSTEM) }
                OptionRow(stringResource(R.string.theme_light), settings.darkMode == DarkMode.LIGHT) { prefs.setDarkMode(DarkMode.LIGHT) }
                OptionRow(stringResource(R.string.theme_dark), settings.darkMode == DarkMode.DARK) { prefs.setDarkMode(DarkMode.DARK) }
            }
            SettingGroup(Icons.Default.Palette, stringResource(R.string.settings_color)) {
                OptionRow(stringResource(R.string.color_colorful), settings.colorMode == ColorMode.COLORFUL, stringResource(R.string.color_colorful_sub)) { prefs.setColorMode(ColorMode.COLORFUL) }
                val dynAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                OptionRow(
                    stringResource(R.string.color_dynamic),
                    settings.colorMode == ColorMode.DYNAMIC,
                    if (dynAvailable) stringResource(R.string.color_dynamic_sub) else stringResource(R.string.color_dynamic_unavailable),
                    enabled = dynAvailable,
                ) { if (dynAvailable) prefs.setColorMode(ColorMode.DYNAMIC) }
            }
        }
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

@Composable
private fun OptionRow(label: String, selected: Boolean, subtitle: String? = null, enabled: Boolean = true, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onSelect) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = if (enabled) onSelect else null)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
