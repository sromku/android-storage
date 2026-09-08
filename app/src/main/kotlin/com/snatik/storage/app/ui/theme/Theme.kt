package com.snatik.storage.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2C6E5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2EFE9),
    onPrimaryContainer = Color(0xFF0F2A22),
    secondary = Color(0xFF5D6864),
    secondaryContainer = Color(0xFFE4E9E6),
    background = Color(0xFFF4F6F3),
    surface = Color(0xFFF9FAF8),
    onSurface = Color(0xFF1A201D),
    surfaceVariant = Color(0xFFE8ECE9),
    onSurfaceVariant = Color(0xFF4E5855),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FBFA3),
    onPrimary = Color(0xFF0F1512),
    primaryContainer = Color(0xFF1F2F29),
    onPrimaryContainer = Color(0xFFCDE9DD),
    secondary = Color(0xFF98A49E),
    secondaryContainer = Color(0xFF2A3430),
    background = Color(0xFF131715),
    surface = Color(0xFF171C1A),
    onSurface = Color(0xFFE3E8E4),
    surfaceVariant = Color(0xFF262D2A),
    onSurfaceVariant = Color(0xFFB4BFB9),
)

/** Monospace style for paths, hashes and hex. */
val MonoStyle: TextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)

private val AppTypography = Typography()

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
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
