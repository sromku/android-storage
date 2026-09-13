package com.snatik.storage.app.di

import com.snatik.storage.Storage
import com.snatik.storage.app.feature.browser.BrowserPreferences
import com.snatik.storage.app.feature.browser.BrowserViewModel
import com.snatik.storage.app.feature.browser.FileClipboard
import com.snatik.storage.app.feature.home.HomeViewModel
import com.snatik.storage.app.feature.apps.AppsViewModel
import com.snatik.storage.app.feature.apps.AppDetailViewModel
import com.snatik.storage.app.feature.disk.DiskUsageViewModel
import com.snatik.storage.core.apps.AppActions
import com.snatik.storage.core.apps.AppWatchRepository
import com.snatik.storage.core.apps.NetworkInspector
import com.snatik.storage.app.feature.network.NetworkViewModel
import com.snatik.storage.core.apps.DeviceStatsRepository
import com.snatik.storage.core.apps.AppOpsTimeline
import com.snatik.storage.core.apps.PermissionMatrixRepository
import com.snatik.storage.core.apps.FileSearch
import com.snatik.storage.app.feature.search.SearchViewModel
import com.snatik.storage.app.feature.monitor.ProviderWatcher
import com.snatik.storage.app.feature.monitor.ClipboardInspector
import com.snatik.storage.app.feature.monitor.NotificationMonitorViewModel
import com.snatik.storage.app.feature.monitor.ProviderWatchViewModel
import com.snatik.storage.app.feature.monitor.ClipboardViewModel
import com.snatik.storage.core.apps.StorageInsights
import com.snatik.storage.core.apps.AppStorageAnalyzer
import com.snatik.storage.app.feature.insights.InsightsViewModel
import com.snatik.storage.app.feature.disk.SunburstViewModel
import com.snatik.storage.app.feature.apps.AppStorageViewModel
import com.snatik.storage.core.apps.SystemInspector
import com.snatik.storage.core.apps.ElfInspector
import com.snatik.storage.app.feature.system.SystemViewModel
import com.snatik.storage.app.feature.viewer.ElfViewModel
import com.snatik.storage.core.apps.TelemetryDatabase
import com.snatik.storage.core.apps.AppEventDatabase
import com.snatik.storage.core.apps.AppEventLog
import com.snatik.storage.app.feature.history.AppHistoryViewModel
import com.snatik.storage.core.apps.TelemetryRepository
import com.snatik.storage.app.feature.timemachine.TimeMachineViewModel
import com.snatik.storage.core.apps.StorageBenchmark
import com.snatik.storage.app.feature.benchmark.BenchmarkViewModel
import com.snatik.storage.app.feature.dashboard.PermissionFootprintViewModel
import com.snatik.storage.app.feature.dashboard.DashboardViewModel
import com.snatik.storage.app.feature.dashboard.PermissionMatrixViewModel
import com.snatik.storage.app.feature.dashboard.AppOpsTimelineViewModel
import com.snatik.storage.core.apps.AppRepository
import com.snatik.storage.core.apps.ManifestDecoder
import com.snatik.storage.core.fs.DiskScanner
import com.snatik.storage.core.data.ProviderRepository
import com.snatik.storage.core.data.ProviderQuery
import com.snatik.storage.core.data.SqliteInspector
import com.snatik.storage.app.feature.data.DatabaseSessions
import com.snatik.storage.app.feature.data.DataViewModel
import com.snatik.storage.app.feature.data.ProviderQueryViewModel
import com.snatik.storage.app.feature.data.DatabaseViewModel
import com.snatik.storage.app.feature.data.DbTableViewModel
import com.snatik.storage.app.feature.data.PrefsViewModel
import com.snatik.storage.app.feature.intents.BroadcastHistoryViewModel
import com.snatik.storage.app.feature.intents.BroadcastMonitorViewModel
import com.snatik.storage.app.feature.intents.DeepLinkViewModel
import com.snatik.storage.app.feature.intents.IntentBuilderViewModel
import com.snatik.storage.app.feature.intents.IntentsViewModel
import com.snatik.storage.core.intents.BroadcastHistory
import com.snatik.storage.core.intents.BroadcastMonitor
import com.snatik.storage.core.intents.IntentPresets
import com.snatik.storage.core.intents.IntentSender
import com.snatik.storage.core.capture.CaptureDatabase
import com.snatik.storage.core.capture.RecordingEngine
import com.snatik.storage.core.capture.ScreenRecorder
import com.snatik.storage.core.capture.SnapshotRepository
import com.snatik.storage.app.feature.capture.CaptureViewModel
import com.snatik.storage.app.feature.capture.FileDiffViewModel
import com.snatik.storage.app.feature.capture.RecordingViewModel
import com.snatik.storage.app.feature.capture.SnapshotDiffViewModel
import com.snatik.storage.app.feature.capture.SnapshotViewModel
import com.snatik.storage.app.feature.transfer.ReceiveViewModel
import com.snatik.storage.app.feature.transfer.SendToViewModel
import com.snatik.storage.app.feature.transfer.TransferHub
import com.snatik.storage.app.feature.api.ApiConfig
import com.snatik.storage.app.feature.api.ApiOperations
import com.snatik.storage.app.feature.api.ApiService
import com.snatik.storage.app.feature.api.ApiViewModel
import com.snatik.storage.app.feature.api.AuditLog
import org.koin.core.module.dsl.viewModelOf
import com.snatik.storage.app.feature.viewer.HexViewerViewModel
import com.snatik.storage.app.feature.viewer.TextViewerViewModel
import com.snatik.storage.app.feature.viewer.JsonTreeViewModel
import com.snatik.storage.app.feature.viewer.XmlTreeViewModel
import com.snatik.storage.app.feature.viewer.ApkViewModel
import com.snatik.storage.app.feature.viewer.MediaViewModel
import com.snatik.storage.app.feature.viewer.DecompileViewModel
import com.snatik.storage.app.feature.viewer.VectorRenderViewModel
import com.snatik.storage.app.feature.viewer.ApkAnalyzeViewModel
import com.snatik.storage.app.feature.viewer.ArchiveViewModel
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.fs.LocalFileSystem
import com.snatik.storage.core.fs.OperationRunner
import com.snatik.storage.core.fs.VolumeRepository
import com.snatik.storage.core.shell.DebuggablePackages
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.RoutedFileSystem
import com.snatik.storage.core.shell.ShizukuManager
import com.snatik.storage.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { Storage(androidContext()) }
    single { LocalFileSystem(get()) }
    single { ShizukuManager(androidContext(), BuildConfig.DEBUG) }
    single { PrivilegeManager(androidContext(), get(), get()) }
    single { DebuggablePackages(androidContext()) }
    single<FileSystem> { RoutedFileSystem(get<LocalFileSystem>(), get(), get()) }
    single { VolumeRepository(androidContext()) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { OperationRunner(get()) }
    single { FileClipboard() }
    single { BrowserPreferences(androidContext()) }
    single { com.snatik.storage.app.ui.theme.ThemePreferences(androidContext()) }
    single { AppRepository(androidContext()) }
    single { ManifestDecoder(androidContext()) }
    single { com.snatik.storage.core.apps.IntentFilterInspector(androidContext(), get()) }
    single { AppActions(get()) }
    single { AppWatchRepository(androidContext(), get(), get()) }
    single { NetworkInspector(androidContext(), get()) }
    single { com.snatik.storage.core.apps.AsnDb(androidContext()) }
    single { com.snatik.storage.app.feature.network.NetworkPreferences(androidContext()) }
    single { DeviceStatsRepository(androidContext(), get()) }
    single { AppOpsTimeline(androidContext(), get()) }
    single { PermissionMatrixRepository(androidContext()) }
    single { FileSearch(get<com.snatik.storage.core.fs.FileSystem>(), get()) }
    single { ProviderWatcher(androidContext()) }
    single { ClipboardInspector(androidContext()) }
    single { StorageInsights(get<com.snatik.storage.core.fs.FileSystem>(), get()) }
    single { AppStorageAnalyzer(get(), get()) }
    single { SystemInspector(get()) }
    single { ElfInspector(get<com.snatik.storage.core.fs.FileSystem>()) }
    single { TelemetryDatabase.create(androidContext()) }
    single { TelemetryRepository(androidContext(), get(), get()) }
    single { AppEventDatabase.create(androidContext()) }
    single { AppEventLog(androidContext(), get(), get()) }
    single { StorageBenchmark() }
    single { DiskScanner(get()) }
    single { ProviderRepository(androidContext()) }
    single { ProviderQuery(androidContext(), get()) }
    single { com.snatik.storage.app.feature.data.UriDiscovery(androidContext(), get()) }
    single { com.snatik.storage.app.feature.data.SavedQueryStore(androidContext(), get()) }
    single { com.snatik.storage.app.feature.data.UriDiscoveryStore(androidContext(), get()) }
    single { SqliteInspector(androidContext(), get()) }
    single { DatabaseSessions(get()) }
    single { IntentSender(androidContext()) }
    single { IntentPresets(androidContext()) }
    single { BroadcastMonitor(androidContext()) }
    single { BroadcastHistory() }
    single { CaptureDatabase.create(androidContext()) }
    single { SnapshotRepository(androidContext(), get(), get()) }
    single { RecordingEngine(androidContext(), get(), get(), get(), get()) }
    single { ScreenRecorder(androidContext()) }
    single { ApiConfig(androidContext()) }
    single { AuditLog() }
    single { ApiOperations(androidContext(), get<com.snatik.storage.core.fs.FileSystem>(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { ApiService(get(), get(), get()) }
    single { TransferHub(androidContext(), get<com.snatik.storage.core.fs.FileSystem>(), get<ApiService>().routes, get()) }

    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { (route: Route.Browser) -> BrowserViewModel(route, get(), get(), get(), get()) }
    viewModel { (path: String) -> TextViewerViewModel(path, get()) }
    viewModel { (path: String) -> HexViewerViewModel(path, get()) }
    viewModel { (path: String) -> JsonTreeViewModel(path, get()) }
    viewModel { (path: String) -> XmlTreeViewModel(path, get()) }
    viewModel { (path: String) -> ApkViewModel(path, androidContext(), get()) }
    viewModel { (path: String) -> MediaViewModel(path, androidContext(), get()) }
    viewModel { (path: String) -> DecompileViewModel(path, androidContext()) }
    viewModel { (path: String) -> VectorRenderViewModel(path, androidContext()) }
    viewModel { (path: String) -> ApkAnalyzeViewModel(path, androidContext(), get()) }
    viewModel { (path: String) -> ArchiveViewModel(path, androidContext()) }
    viewModelOf(::AppsViewModel)
    viewModel { (packageName: String) -> AppDetailViewModel(packageName, get(), get(), get(), get(), get(), get()) }
    viewModel { (path: String) -> DiskUsageViewModel(path, get()) }
    viewModel { DataViewModel(get(), get()) }
    viewModel { (route: Route.ProviderQuery) -> ProviderQueryViewModel(route, get(), get(), get(), get()) }
    viewModel { (route: Route.ProviderUris) -> com.snatik.storage.app.feature.data.ProviderUrisViewModel(route, androidContext(), get(), get(), get(), get(), get()) }
    viewModel { (path: String) -> DatabaseViewModel(path, get()) }
    viewModel { (route: Route.DbTable) -> DbTableViewModel(route, get()) }
    viewModel { (path: String) -> PrefsViewModel(path, get()) }
    viewModel { IntentsViewModel(androidContext(), get(), get(), get(), get()) }
    viewModel { com.snatik.storage.app.feature.intents.IntentDiscoverViewModel(get(), get()) }
    viewModel { com.snatik.storage.app.feature.intents.IntentExamplesViewModel(get()) }
    single { com.snatik.storage.core.intents.IntentMonitorStore(androidContext()) }
    viewModel { com.snatik.storage.app.feature.intents.IntentMonitorViewModel(androidContext(), get(), get()) }
    viewModel { (route: Route.IntentBuilder) -> IntentBuilderViewModel(route, get(), get(), get()) }
    viewModelOf(::BroadcastMonitorViewModel)
    viewModel { BroadcastHistoryViewModel(get(), get()) }
    viewModelOf(::DeepLinkViewModel)
    viewModel { (route: Route.Capture) -> CaptureViewModel(route, androidContext(), get(), get(), get()) }
    viewModel { (id: Long) -> SnapshotViewModel(id, get()) }
    viewModel { (route: Route.SnapshotDiff) -> SnapshotDiffViewModel(route, get()) }
    viewModel { (route: Route.FileDiff) -> FileDiffViewModel(route, get()) }
    viewModel { (id: Long) -> RecordingViewModel(id, get()) }
    viewModelOf(::ReceiveViewModel)
    viewModel { (route: Route.SendTo) -> SendToViewModel(route, get()) }
    viewModelOf(::ApiViewModel)
    viewModelOf(::NetworkViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::PermissionMatrixViewModel)
    viewModelOf(::AppOpsTimelineViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::NotificationMonitorViewModel)
    viewModelOf(::ProviderWatchViewModel)
    viewModelOf(::ClipboardViewModel)
    viewModelOf(::InsightsViewModel)
    viewModel { (path: String) -> SunburstViewModel(path, get()) }
    viewModel { (packageName: String) -> AppStorageViewModel(packageName, get()) }
    single { com.snatik.storage.app.feature.apps.ArtOpLog() }
    viewModel { (packageName: String) -> com.snatik.storage.app.feature.apps.ArtViewModel(packageName, get(), get(), get()) }
    viewModelOf(::SystemViewModel)
    viewModel { (path: String) -> ElfViewModel(path, get(), get()) }
    viewModel { (path: String) -> com.snatik.storage.app.feature.viewer.FontViewerViewModel(path, get(), androidContext()) }
    viewModel { (path: String) -> com.snatik.storage.app.feature.viewer.PdfViewerViewModel(path, get(), androidContext()) }
    viewModelOf(::TimeMachineViewModel)
    viewModel { BenchmarkViewModel(androidContext(), get()) }
    viewModelOf(::PermissionFootprintViewModel)
    viewModelOf(::AppHistoryViewModel)
}
