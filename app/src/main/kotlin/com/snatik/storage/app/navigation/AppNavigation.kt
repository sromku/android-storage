package com.snatik.storage.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.snatik.storage.app.feature.apps.AppDetailScreen
import com.snatik.storage.app.feature.apps.AppsScreen
import com.snatik.storage.app.feature.browser.BrowserScreen
import com.snatik.storage.app.feature.capture.CaptureScreen
import com.snatik.storage.app.feature.capture.FileDiffScreen
import com.snatik.storage.app.feature.capture.RecordingScreen
import com.snatik.storage.app.feature.capture.SnapshotDiffScreen
import com.snatik.storage.app.feature.capture.SnapshotScreen
import com.snatik.storage.app.feature.data.DataScreen
import com.snatik.storage.app.feature.data.DatabaseScreen
import com.snatik.storage.app.feature.data.DbTableScreen
import com.snatik.storage.app.feature.data.PrefsScreen
import com.snatik.storage.app.feature.data.ProviderQueryScreen
import com.snatik.storage.app.feature.disk.DiskUsageScreen
import com.snatik.storage.app.feature.home.HomeScreen
import com.snatik.storage.app.feature.tools.ToolsScreen
import com.snatik.storage.app.feature.network.NetworkScreen
import com.snatik.storage.app.feature.dashboard.DashboardScreen
import com.snatik.storage.app.feature.dashboard.PermissionMatrixScreen
import com.snatik.storage.app.feature.dashboard.AppOpsTimelineScreen
import com.snatik.storage.app.feature.search.SearchScreen
import com.snatik.storage.app.feature.monitor.NotificationMonitorScreen
import com.snatik.storage.app.feature.monitor.ProviderWatchScreen
import com.snatik.storage.app.feature.monitor.ClipboardScreen
import com.snatik.storage.app.feature.insights.InsightsScreen
import com.snatik.storage.app.feature.disk.SunburstScreen
import com.snatik.storage.app.feature.apps.AppStorageScreen
import com.snatik.storage.app.feature.system.SystemScreen
import com.snatik.storage.app.feature.viewer.ElfViewerScreen
import com.snatik.storage.app.feature.timemachine.TimeMachineScreen
import com.snatik.storage.app.feature.benchmark.BenchmarkScreen
import com.snatik.storage.app.feature.palette.Command
import com.snatik.storage.app.feature.palette.CommandPaletteScreen
import com.snatik.storage.app.feature.dashboard.PermissionFootprintScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.automirrored.filled.Send
import com.snatik.storage.app.feature.api.ApiScreen
import com.snatik.storage.app.feature.transfer.ReceiveScreen
import com.snatik.storage.app.feature.transfer.SendToScreen
import com.snatik.storage.app.feature.intents.BroadcastHistoryScreen
import com.snatik.storage.app.feature.intents.BroadcastMonitorScreen
import com.snatik.storage.app.feature.intents.DeepLinkScreen
import com.snatik.storage.app.feature.intents.IntentBuilderScreen
import com.snatik.storage.app.feature.intents.IntentLogScreen
import com.snatik.storage.app.feature.intents.IntentsScreen
import com.snatik.storage.app.feature.viewer.HexViewerScreen
import com.snatik.storage.app.feature.viewer.ImageViewerScreen
import com.snatik.storage.app.feature.viewer.JsonViewerScreen
import com.snatik.storage.app.feature.viewer.XmlViewerScreen
import com.snatik.storage.app.feature.viewer.ApkViewerScreen
import com.snatik.storage.app.feature.viewer.ArchiveViewerScreen
import com.snatik.storage.app.feature.viewer.TextViewerScreen
import com.snatik.storage.core.fs.FileKind
import com.snatik.storage.core.fs.FsEntry

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(Route.Home)

    fun push(route: NavKey) = backStack.add(route)
    fun pop() { backStack.removeLastOrNull() }

    fun switchTab(tab: TopLevel) {
        val root: Route = when (tab) {
            TopLevel.STORAGE -> Route.Home
            TopLevel.APPS -> Route.Apps
            TopLevel.DATA -> Route.Data
            TopLevel.TOOLS -> Route.Tools
        }
        if (backStack.size == 1 && backStack.first()::class == root::class) return
        backStack.add(root)
        while (backStack.size > 1) backStack.removeAt(0)
    }

    fun openBrowser(label: String, path: String) = push(Route.Browser(rootPath = path, rootLabel = label, path = path))

    fun openDirectory(from: Route.Browser, path: String) {
        // Breadcrumb taps go back to an entry we already have; anything else is pushed.
        val index = backStack.indexOfLast { it is Route.Browser && it.rootPath == from.rootPath && it.path == path }
        if (index >= 0) {
            while (backStack.lastIndex > index) backStack.removeLastOrNull()
        } else {
            push(from.copy(path = path))
        }
    }

    fun openFile(entry: FsEntry, forceKind: FileKind? = null) {
        when (forceKind ?: entry.kind) {
            FileKind.DATABASE -> push(Route.Database(entry.path))
            FileKind.APK -> push(Route.ApkViewer(entry.path))
            FileKind.ARCHIVE -> push(Route.ArchiveViewer(entry.path))
            FileKind.JSON -> push(Route.JsonViewer(entry.path))
            FileKind.XML -> if (entry.parentPath?.endsWith("/shared_prefs") == true) push(Route.Prefs(entry.path)) else push(Route.XmlViewer(entry.path))
            FileKind.IMAGE -> push(Route.ImageViewer(entry.path))
            FileKind.TEXT, FileKind.CODE -> push(Route.TextViewer(entry.path))
            else -> if (entry.name.endsWith(".so") || entry.name.contains(".so.")) push(Route.ElfViewer(entry.path)) else push(Route.HexViewer(entry.path))
        }
    }

    // Open a bare path (from search results) by classifying its name.
    fun openPath(path: String) {
        val name = path.substringAfterLast('/')
        val entry = FsEntry(
            path = path, name = name, isDirectory = false, size = -1, lastModified = 0,
            isHidden = name.startsWith('.'), isSymlink = false, canRead = true, canWrite = false, childCount = null,
        )
        openFile(entry)
    }

    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Route.Home> {
                HomeScreen(
                    onOpenVolume = ::openBrowser,
                    onOpenDiskUsage = { label, path -> push(Route.DiskUsage(label, path)) },
                    onSwitchTab = ::switchTab,
                )
            }
            entry<Route.Apps> {
                AppsScreen(onOpenApp = { push(Route.AppDetail(it)) }, onSwitchTab = ::switchTab)
            }
            entry<Route.AppDetail> { route ->
                AppDetailScreen(
                    packageName = route.packageName,
                    onBack = ::pop,
                    onBrowse = ::openBrowser,
                    onDiskUsage = { label, path -> push(Route.DiskUsage(label, path)) },
                    onNetwork = { pkg -> push(Route.Network(pkg)) },
                    onStorage = { pkg -> push(Route.AppStorage(pkg)) },
                )
            }
            entry<Route.DiskUsage> { route ->
                DiskUsageScreen(route = route, onBack = ::pop, onBrowse = { path -> openBrowser(route.label, path) }, onSunburst = { push(Route.Sunburst(route.label, route.path)) })
            }
            entry<Route.Browser> { route ->
                BrowserScreen(
                    route = route,
                    onBack = ::pop,
                    onOpenDirectory = { path -> openDirectory(route, path) },
                    onOpenFile = { entry -> openFile(entry) },
                    onViewAsText = { entry -> openFile(entry, FileKind.TEXT) },
                    onViewAsHex = { entry -> openFile(entry, FileKind.OTHER) },
                    onOpenAsDatabase = { entry -> push(Route.Database(entry.path)) },
                    onOpenAsPrefs = { entry -> push(Route.Prefs(entry.path)) },
                    onDiskUsage = { push(Route.DiskUsage(route.path.substringAfterLast('/').ifEmpty { route.rootLabel }, route.path)) },
                    onSnapshot = { push(Route.Capture(newSnapshotPath = route.path)) },
                    onSendTo = { paths -> push(Route.SendTo(paths)) },
                )
            }
            entry<Route.Data> {
                DataScreen(onOpenProvider = { title, uri -> push(Route.ProviderQuery(uri, title)) }, onSwitchTab = ::switchTab)
            }
            entry<Route.ProviderQuery> { route ->
                ProviderQueryScreen(route = route, onBack = ::pop)
            }
            entry<Route.Database> { route ->
                DatabaseScreen(path = route.path, onBack = ::pop, onOpenTable = { table -> push(Route.DbTable(route.path, table)) })
            }
            entry<Route.DbTable> { route ->
                DbTableScreen(route = route, onBack = ::pop)
            }
            entry<Route.Prefs> { route ->
                PrefsScreen(path = route.path, onBack = ::pop)
            }
            entry<Route.Tools> {
                ToolsScreen(
                    onSwitchTab = ::switchTab,
                    onOpenIntents = { push(Route.Intents) },
                    onOpenCapture = { push(Route.Capture()) },
                    onOpenReceive = { push(Route.Receive) },
                    onOpenApi = { push(Route.Api) },
                    onOpenNetwork = { push(Route.Network()) },
                    onOpenDashboard = { push(Route.Dashboard) },
                    onOpenMatrix = { push(Route.PermissionMatrix) },
                    onOpenTimeline = { push(Route.AppOpsTimeline) },
                    onOpenSearch = { push(Route.Search) },
                    onOpenNotifications = { push(Route.Notifications) },
                    onOpenProviderWatch = { push(Route.ProviderWatch) },
                    onOpenClipboard = { push(Route.Clipboard) },
                    onOpenInsights = { push(Route.Insights) },
                    onOpenSystem = { push(Route.System) },
                    onOpenTimeMachine = { push(Route.TimeMachine) },
                    onOpenBenchmark = { push(Route.Benchmark) },
                    onOpenPermFootprint = { push(Route.PermissionFootprint) },
                    onOpenPalette = { push(Route.CommandPalette) },
                )
            }
            entry<Route.Network> { route -> NetworkScreen(onBack = ::pop, initialQuery = route.query) }
            entry<Route.Dashboard> { DashboardScreen(onBack = ::pop) }
            entry<Route.PermissionMatrix> { PermissionMatrixScreen(onBack = ::pop, onOpenApp = { push(Route.AppDetail(it)) }) }
            entry<Route.AppOpsTimeline> { AppOpsTimelineScreen(onBack = ::pop, onOpenApp = { push(Route.AppDetail(it)) }) }
            entry<Route.Search> { SearchScreen(onBack = ::pop, onOpenPath = ::openPath) }
            entry<Route.Notifications> { NotificationMonitorScreen(onBack = ::pop) }
            entry<Route.ProviderWatch> { ProviderWatchScreen(onBack = ::pop) }
            entry<Route.Clipboard> { ClipboardScreen(onBack = ::pop) }
            entry<Route.Insights> { InsightsScreen(onBack = ::pop, onOpenPath = ::openPath) }
            entry<Route.Sunburst> { route -> SunburstScreen(route = route, onBack = ::pop) }
            entry<Route.AppStorage> { route -> AppStorageScreen(packageName = route.packageName, onBack = ::pop) }
            entry<Route.System> { SystemScreen(onBack = ::pop) }
            entry<Route.TimeMachine> { TimeMachineScreen(onBack = ::pop) }
            entry<Route.Benchmark> { BenchmarkScreen(onBack = ::pop) }
            entry<Route.PermissionFootprint> { PermissionFootprintScreen(onBack = ::pop, onOpenApp = { push(Route.AppDetail(it)) }) }
            entry<Route.CommandPalette> {
                val open: (Route) -> Unit = { r -> pop(); push(r) }
                val commands = listOf(
                    Command("Storage", "Volumes and file browser", "files volumes browse", Icons.Default.Dashboard) { pop(); switchTab(TopLevel.STORAGE) },
                    Command("Apps", "Installed apps", "apps packages", Icons.Default.GridOn) { pop(); switchTab(TopLevel.APPS) },
                    Command("Data", "Content providers", "providers sqlite data", Icons.Default.Dashboard) { pop(); switchTab(TopLevel.DATA) },
                    Command("Dashboard", "Device health", "battery memory selinux uptime", Icons.Default.Dashboard) { open(Route.Dashboard) },
                    Command("Permission matrix", "Apps vs dangerous permissions", "permissions grant", Icons.Default.GridOn) { open(Route.PermissionMatrix) },
                    Command("Permission vs footprint", "Rank apps by permissions and size", "permissions size risk", Icons.Default.GridOn) { open(Route.PermissionFootprint) },
                    Command("App-ops timeline", "Recent sensitive access", "location camera mic appops", Icons.Default.History) { open(Route.AppOpsTimeline) },
                    Command("Network", "Per-app connections", "network sockets connections", Icons.Default.Lan) { open(Route.Network()) },
                    Command("Search", "Find files by name or content", "search find grep", Icons.Default.Search) { open(Route.Search) },
                    Command("Notification monitor", "Log notifications", "notifications", Icons.Default.NotificationsActive) { open(Route.Notifications) },
                    Command("Provider watch", "Watch content providers", "provider observer changes", Icons.Default.Sensors) { open(Route.ProviderWatch) },
                    Command("Clipboard", "Clipboard history", "clipboard clip", Icons.Default.ContentPaste) { open(Route.Clipboard) },
                    Command("Storage insights", "Duplicates, empties, ghosts", "duplicates reclaim insights", Icons.Default.Insights) { open(Route.Insights) },
                    Command("System", "Mounts, ZRAM, smaps", "mounts partitions zram smaps kernel", Icons.Default.Memory) { open(Route.System) },
                    Command("Time Machine", "Storage growth forecast", "telemetry growth forecast", Icons.Default.Timeline) { open(Route.TimeMachine) },
                    Command("Benchmark", "Read/write throughput", "benchmark speed iops", Icons.Default.Speed) { open(Route.Benchmark) },
                    Command("Intents", "Build and watch intents", "intents broadcast deeplink", Icons.AutoMirrored.Filled.Send) { open(Route.Intents) },
                    Command("Capture", "Snapshots and recording", "snapshot diff record", Icons.Default.FiberManualRecord) { open(Route.Capture()) },
                    Command("Receive files", "HTTP drop and transfer", "transfer receive files", Icons.Default.Wifi) { open(Route.Receive) },
                    Command("Agent API", "REST and MCP server", "api mcp agent", Icons.Default.Api) { open(Route.Api) },
                )
                CommandPaletteScreen(commands = commands, onBack = ::pop)
            }
            entry<Route.ElfViewer> { route -> ElfViewerScreen(path = route.path, onBack = ::pop, onViewAsHex = { push(Route.HexViewer(route.path)) }) }
            entry<Route.Intents> {
                IntentsScreen(
                    onBack = ::pop,
                    onOpenBuilder = { push(Route.IntentBuilder()) },
                    onOpenPreset = { id -> push(Route.IntentBuilder(presetId = id)) },
                    onOpenLog = { push(Route.IntentLog) },
                    onOpenMonitor = { push(Route.BroadcastMonitor) },
                    onOpenHistory = { push(Route.BroadcastHistory) },
                    onOpenDeepLink = { push(Route.DeepLink) },
                )
            }
            entry<Route.IntentBuilder> { route -> IntentBuilderScreen(route = route, onBack = ::pop) }
            entry<Route.IntentLog> {
                IntentLogScreen(onBack = ::pop, onResend = { json -> push(Route.IntentBuilder(specJson = json)) })
            }
            entry<Route.BroadcastMonitor> { BroadcastMonitorScreen(onBack = ::pop) }
            entry<Route.BroadcastHistory> { BroadcastHistoryScreen(onBack = ::pop) }
            entry<Route.DeepLink> { DeepLinkScreen(onBack = ::pop) }
            entry<Route.Capture> { route ->
                CaptureScreen(
                    route = route,
                    onBack = ::pop,
                    onOpenSnapshot = { id -> push(Route.Snapshot(id)) },
                    onOpenRecording = { id -> push(Route.Recording(id)) },
                )
            }
            entry<Route.Snapshot> { route ->
                SnapshotScreen(id = route.id, onBack = ::pop, onCompare = { a, b -> push(Route.SnapshotDiff(a, b)) })
            }
            entry<Route.SnapshotDiff> { route ->
                SnapshotDiffScreen(route = route, onBack = ::pop, onOpenFile = { path -> push(Route.FileDiff(route.aId, route.bId, path)) })
            }
            entry<Route.FileDiff> { route -> FileDiffScreen(route = route, onBack = ::pop) }
            entry<Route.Recording> { route -> RecordingScreen(id = route.id, onBack = ::pop) }
            entry<Route.Api> { ApiScreen(onBack = ::pop) }
            entry<Route.Receive> { ReceiveScreen(onBack = ::pop, onOpenInbox = { path -> openBrowser("Storage Received", path) }) }
            entry<Route.SendTo> { route -> SendToScreen(route = route, onBack = ::pop) }
            entry<Route.TextViewer> { route ->
                TextViewerScreen(path = route.path, onBack = ::pop, onViewAsHex = { push(Route.HexViewer(route.path)) })
            }
            entry<Route.HexViewer> { route ->
                HexViewerScreen(path = route.path, onBack = ::pop)
            }
            entry<Route.ImageViewer> { route ->
                ImageViewerScreen(path = route.path, onBack = ::pop)
            }
            entry<Route.JsonViewer> { route ->
                JsonViewerScreen(path = route.path, onBack = ::pop, onViewAsText = { push(Route.TextViewer(route.path)) })
            }
            entry<Route.XmlViewer> { route ->
                XmlViewerScreen(path = route.path, onBack = ::pop, onViewAsText = { push(Route.TextViewer(route.path)) })
            }
            entry<Route.ApkViewer> { route -> ApkViewerScreen(path = route.path, onBack = ::pop, onOpenPath = ::openPath) }
            entry<Route.ArchiveViewer> { route -> ArchiveViewerScreen(path = route.path, onBack = ::pop) }
        },
    )
}
