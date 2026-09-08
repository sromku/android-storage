package com.snatik.storage.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2C6E5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2EFE9),
    onPrimaryContainer = Color(0xFF0F2A22),
    secondary = Color(0xFF5D6864),
    background = Color(0xFFF4F6F3),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A201D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FBFA3),
    onPrimary = Color(0xFF0F1512),
    primaryContainer = Color(0xFF1F2F29),
    onPrimaryContainer = Color(0xFFCDE9DD),
    secondary = Color(0xFF98A49E),
    background = Color(0xFF131715),
    surface = Color(0xFF1B201E),
    onSurface = Color(0xFFE3E8E4),
)

@Composable
fun StorageTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
