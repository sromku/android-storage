package com.snatik.storage.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Playful, icon-derived palette: Google blue primary, green + purple accents, warm red error.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B62F0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE6FF),
    onPrimaryContainer = Color(0xFF06204F),
    inversePrimary = Color(0xFFAFC6FF),

    secondary = Color(0xFF1E8E3E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDEFD4),
    onSecondaryContainer = Color(0xFF052F19),

    tertiary = Color(0xFF7A45FF),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8DEFF),
    onTertiaryContainer = Color(0xFF250B64),

    error = Color(0xFFD93025),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCDAD5),
    onErrorContainer = Color(0xFF410B06),

    background = Color(0xFFFBFCFF),
    onBackground = Color(0xFF141821),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141821),
    surfaceVariant = Color(0xFFE5EAF4),
    onSurfaceVariant = Color(0xFF474E5C),
    surfaceTint = Color(0xFF1B62F0),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F6FC),
    surfaceContainer = Color(0xFFEDF1FA),
    surfaceContainerHigh = Color(0xFFE6ECF7),
    surfaceContainerHighest = Color(0xFFDFE7F3),

    outline = Color(0xFF8A91A1),
    outlineVariant = Color(0xFFC8CEDB),
)

// Kept for completeness; the app is light-first on this theme (see StorageTheme).
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC0FF),
    onPrimary = Color(0xFF00306E),
    primaryContainer = Color(0xFF13448F),
    onPrimaryContainer = Color(0xFFD8E4FF),
    secondary = Color(0xFF7FD79A),
    onSecondary = Color(0xFF00391C),
    secondaryContainer = Color(0xFF17692F),
    onSecondaryContainer = Color(0xFF9BF4B4),
    tertiary = Color(0xFFCDBEFF),
    onTertiary = Color(0xFF3B1B8C),
    tertiaryContainer = Color(0xFF5330B0),
    onTertiaryContainer = Color(0xFFE8DEFF),
    error = Color(0xFFFFB4A9),
    background = Color(0xFF11151C),
    surface = Color(0xFF161B24),
    onSurface = Color(0xFFE4E9F2),
    surfaceVariant = Color(0xFF2A303C),
    onSurfaceVariant = Color(0xFFBEC5D3),
)

/** Rounder than stock M3 — softer cards, sheets, chips and charts. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Monospace style for paths, hashes and hex. */
val MonoStyle: TextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)

private val AppTypography = Typography()

@Composable
fun StorageTheme(settings: ThemeSettings = ThemeSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        settings.colorMode == ColorMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, typography = AppTypography, content = content)
}
