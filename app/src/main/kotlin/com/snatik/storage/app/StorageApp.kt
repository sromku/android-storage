package com.snatik.storage.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.snatik.storage.app.di.appModule
import com.snatik.storage.app.ui.components.AppIconFetcher
import com.snatik.storage.app.ui.components.AppIconKeyer
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.android.ext.android.get
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.apps.AppEventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class StorageApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@StorageApp)
            modules(appModule)
        }
        get<PrivilegeManager>().start()
        // App-event history: reconcile once now (catches changes since last launch) and keep a
        // periodic background job running so changes are picked up even without opening the app.
        AppEventLog.schedule(this)
        val log = get<AppEventLog>()
        get<CoroutineScope>().launch { runCatching { log.reconcile() } }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
                add(AppIconFetcher.Factory(this@StorageApp))
                add(com.snatik.storage.app.feature.media.RawImageFetcher.Factory())
                add(com.snatik.storage.app.feature.media.RawImageKeyer())
                add(AppIconKeyer())
            }
            .crossfade(true)
            .build()
}
