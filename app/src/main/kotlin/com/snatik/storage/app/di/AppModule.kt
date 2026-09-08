package com.snatik.storage.app.di

import com.snatik.storage.Storage
import com.snatik.storage.app.feature.browser.BrowserPreferences
import com.snatik.storage.app.feature.browser.BrowserViewModel
import com.snatik.storage.app.feature.browser.FileClipboard
import com.snatik.storage.app.feature.home.HomeViewModel
import com.snatik.storage.app.feature.viewer.HexViewerViewModel
import com.snatik.storage.app.feature.viewer.TextViewerViewModel
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.fs.LocalFileSystem
import com.snatik.storage.core.fs.OperationRunner
import com.snatik.storage.core.fs.VolumeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { Storage(androidContext()) }
    single<FileSystem> { LocalFileSystem(get()) }
    single { VolumeRepository(androidContext()) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { OperationRunner(get()) }
    single { FileClipboard() }
    single { BrowserPreferences(androidContext()) }

    viewModelOf(::HomeViewModel)
    viewModel { (route: Route.Browser) -> BrowserViewModel(route, get(), get(), get(), get()) }
    viewModel { (path: String) -> TextViewerViewModel(path, get()) }
    viewModel { (path: String) -> HexViewerViewModel(path, get()) }
}
