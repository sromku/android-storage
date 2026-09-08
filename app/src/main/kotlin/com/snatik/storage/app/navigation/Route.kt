package com.snatik.storage.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {

    @Serializable
    data object Home : Route

    /** One directory of one volume. Each directory is its own back stack entry. */
    @Serializable
    data class Browser(val rootPath: String, val rootLabel: String, val path: String) : Route

    @Serializable
    data class TextViewer(val path: String) : Route

    @Serializable
    data class HexViewer(val path: String) : Route

    @Serializable
    data class ImageViewer(val path: String) : Route
}
