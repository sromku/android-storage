package com.snatik.storage.app.feature.media

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Photo-viewer preferences. [openFullResolution]: when on, opening a large photo goes straight into
 * the full-resolution (tiled) viewer so zooming is sharp without tapping "Full resolution" each time.
 */
class ViewerPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("viewer", Context.MODE_PRIVATE)

    private val _openFullResolution = MutableStateFlow(prefs.getBoolean(KEY_FULL_RES, false))
    val openFullResolution: StateFlow<Boolean> = _openFullResolution.asStateFlow()

    fun setOpenFullResolution(enabled: Boolean) {
        _openFullResolution.update { enabled }
        prefs.edit { putBoolean(KEY_FULL_RES, enabled) }
    }

    private companion object {
        const val KEY_FULL_RES = "open_full_resolution"
    }
}
