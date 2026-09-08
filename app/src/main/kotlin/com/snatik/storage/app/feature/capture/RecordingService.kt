package com.snatik.storage.app.feature.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.snatik.storage.app.MainActivity
import com.snatik.storage.app.R
import com.snatik.storage.core.capture.RecordSource
import com.snatik.storage.core.capture.RecordingConfig
import com.snatik.storage.core.capture.RecordingEngine
import com.snatik.storage.core.capture.ScreenRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File

/** Keeps a recording alive in the background and owns the screen projection when one is used. */
class RecordingService : Service() {

    private val engine: RecordingEngine by inject()
    private val screen: ScreenRecorder by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { stopRecording() }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val config = RecordingConfig(
                    name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
                    sources = intent.getStringArrayExtra(EXTRA_SOURCES).orEmpty().map { RecordSource.valueOf(it) }.toSet(),
                    logcatFilter = intent.getStringExtra(EXTRA_LOGCAT_FILTER).orEmpty(),
                    logcatPackage = intent.getStringExtra(EXTRA_LOGCAT_PACKAGE),
                    watchPaths = intent.getStringArrayExtra(EXTRA_WATCH_PATHS).orEmpty().toList(),
                )
                val wantsScreen = RecordSource.SCREEN in config.sources
                val notification = notification(config.name, config.sources)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val type = if (wantsScreen) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    startForeground(NOTIFICATION_ID, notification, type)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                scope.launch {
                    var videoPath: String? = null
                    if (wantsScreen) {
                        val resultCode = intent.getIntExtra(EXTRA_PROJECTION_RESULT, 0)
                        @Suppress("DEPRECATION")
                        val data = intent.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)
                        if (data != null) {
                            runCatching {
                                val projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
                                    ?: error("No projection")
                                val output = File(filesDir, "recordings").apply { mkdirs() }.let { File(it, "screen-${System.currentTimeMillis()}.mp4") }
                                screen.start(projection, output)
                                videoPath = output.absolutePath
                            }
                        }
                    }
                    runCatching { engine.start(config, videoPath) }.onFailure { stopSelf() }
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun stopRecording() {
        if (screen.isRecording) screen.stop()
        engine.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(name: String, sources: Set<RecordSource>): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.notification_title, name.ifBlank { getString(R.string.recording_now) }))
            .setContentText(getString(R.string.notification_text, sources.joinToString(", ") { it.name.lowercase() }))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.recording_stop), stop)
            .build()
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_NAME = "name"
        const val EXTRA_SOURCES = "sources"
        const val EXTRA_LOGCAT_FILTER = "logcat_filter"
        const val EXTRA_LOGCAT_PACKAGE = "logcat_package"
        const val EXTRA_WATCH_PATHS = "watch_paths"
        const val EXTRA_PROJECTION_RESULT = "projection_result"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context, config: RecordingConfig, projectionResult: Int = 0, projectionData: Intent? = null) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_NAME, config.name)
                .putExtra(EXTRA_SOURCES, config.sources.map { it.name }.toTypedArray())
                .putExtra(EXTRA_LOGCAT_FILTER, config.logcatFilter)
                .putExtra(EXTRA_LOGCAT_PACKAGE, config.logcatPackage)
                .putExtra(EXTRA_WATCH_PATHS, config.watchPaths.toTypedArray())
                .putExtra(EXTRA_PROJECTION_RESULT, projectionResult)
                .putExtra(EXTRA_PROJECTION_DATA, projectionData)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
