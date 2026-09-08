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
import com.snatik.storage.app.feature.disk.DiskUsageScreen
import com.snatik.storage.app.feature.home.HomeScreen
import com.snatik.storage.app.feature.viewer.HexViewerScreen
import com.snatik.storage.app.feature.viewer.ImageViewerScreen
import com.snatik.storage.app.feature.viewer.TextViewerScreen
import com.snatik.storage.core.fs.FileKind
import com.snatik.storage.core.fs.FsEntry

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(Route.Home)

    fun push(route: NavKey) = backStack.add(route)
    fun pop() { backStack.removeLastOrNull() }

    fun switchTab(tab: TopLevel) {
        val root: Route = if (tab == TopLevel.STORAGE) Route.Home else Route.Apps
        if (backStack.size == 1 && backStack.first() == root) return
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
            FileKind.IMAGE -> push(Route.ImageViewer(entry.path))
            FileKind.TEXT, FileKind.CODE, FileKind.JSON, FileKind.XML -> push(Route.TextViewer(entry.path))
            else -> push(Route.HexViewer(entry.path))
        }
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
                )
            }
            entry<Route.DiskUsage> { route ->
                DiskUsageScreen(route = route, onBack = ::pop, onBrowse = { path -> openBrowser(route.label, path) })
            }
            entry<Route.Browser> { route ->
                BrowserScreen(
                    route = route,
                    onBack = ::pop,
                    onOpenDirectory = { path -> openDirectory(route, path) },
                    onOpenFile = { entry -> openFile(entry) },
                    onViewAsText = { entry -> openFile(entry, FileKind.TEXT) },
                    onViewAsHex = { entry -> openFile(entry, FileKind.OTHER) },
                    onDiskUsage = { push(Route.DiskUsage(route.path.substringAfterLast('/').ifEmpty { route.rootLabel }, route.path)) },
                )
            }
            entry<Route.TextViewer> { route ->
                TextViewerScreen(path = route.path, onBack = ::pop, onViewAsHex = { push(Route.HexViewer(route.path)) })
            }
            entry<Route.HexViewer> { route ->
                HexViewerScreen(path = route.path, onBack = ::pop)
            }
            entry<Route.ImageViewer> { route ->
                ImageViewerScreen(path = route.path, onBack = ::pop)
            }
        },
    )
}
