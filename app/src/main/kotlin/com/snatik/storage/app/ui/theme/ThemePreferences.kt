package com.snatik.storage.app.ui.theme

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DarkMode { SYSTEM, LIGHT, DARK }

/** COLORFUL = the app's own vibrant palette; DYNAMIC = Material You wallpaper colors (Android 12+). */
enum class ColorMode { COLORFUL, DYNAMIC }

data class ThemeSettings(val darkMode: DarkMode = DarkMode.LIGHT, val colorMode: ColorMode = ColorMode.COLORFUL)

/** Persisted theme choice, exposed as a flow so the whole app re-themes instantly on change. */
class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        ThemeSettings(
            darkMode = runCatching { DarkMode.valueOf(prefs.getString("dark_mode", "") ?: "") }.getOrDefault(DarkMode.LIGHT),
            colorMode = runCatching { ColorMode.valueOf(prefs.getString("color_mode", "") ?: "") }.getOrDefault(ColorMode.COLORFUL),
        ),
    )
    val state: StateFlow<ThemeSettings> = _state.asStateFlow()

    fun setDarkMode(mode: DarkMode) {
        _state.update { it.copy(darkMode = mode) }
        prefs.edit { putString("dark_mode", mode.name) }
    }

    fun setColorMode(mode: ColorMode) {
        _state.update { it.copy(colorMode = mode) }
        prefs.edit { putString("color_mode", mode.name) }
    }
}
