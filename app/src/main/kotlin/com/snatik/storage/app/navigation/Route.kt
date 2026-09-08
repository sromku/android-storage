package com.snatik.storage.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {

    @Serializable
    data object Home : Route

    @Serializable
    data object Apps : Route

    /** One directory of one volume. Each directory is its own back stack entry. */
    @Serializable
    data class Browser(val rootPath: String, val rootLabel: String, val path: String) : Route

    @Serializable
    data class TextViewer(val path: String) : Route

    @Serializable
    data class HexViewer(val path: String) : Route

    @Serializable
    data class ImageViewer(val path: String) : Route

    @Serializable
    data class AppDetail(val packageName: String) : Route

    @Serializable
    data class DiskUsage(val label: String, val path: String) : Route

    @Serializable
    data object Data : Route

    @Serializable
    data class ProviderQuery(val uri: String, val title: String) : Route

    @Serializable
    data class Database(val path: String) : Route

    @Serializable
    data class DbTable(val path: String, val table: String) : Route

    @Serializable
    data class Prefs(val path: String) : Route

    @Serializable
    data object Intents : Route

    @Serializable
    data class IntentBuilder(val presetId: Long? = null, val specJson: String? = null) : Route

    @Serializable
    data object IntentLog : Route

    @Serializable
    data object BroadcastMonitor : Route

    @Serializable
    data object BroadcastHistory : Route

    @Serializable
    data object DeepLink : Route

    @Serializable
    data class Capture(val newSnapshotPath: String? = null) : Route

    @Serializable
    data class Snapshot(val id: Long) : Route

    @Serializable
    data class SnapshotDiff(val aId: Long, val bId: Long) : Route

    @Serializable
    data class FileDiff(val aId: Long, val bId: Long, val path: String) : Route

    @Serializable
    data class Recording(val id: Long) : Route
}

/** The destinations reachable from the bottom bar. */
enum class TopLevel { STORAGE, APPS, DATA, INTENTS, CAPTURE }
