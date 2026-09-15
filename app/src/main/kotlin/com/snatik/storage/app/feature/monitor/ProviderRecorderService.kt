package com.snatik.storage.app.feature.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.snatik.storage.app.MainActivity
import com.snatik.storage.app.R
import com.snatik.storage.core.apps.ExternalSink
import com.snatik.storage.core.apps.ProviderRecorderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the content-provider observers alive while the app is backgrounded or closed: a sticky
 * foreground service that re-attaches the watched observers and lets [ProviderWatcher] persist each
 * change to the shared store (and stream it). The observers themselves live in the watcher; this
 * service just keeps the process up and flips the recording flag.
 */
class ProviderRecorderService : Service() {

    private val store: ProviderRecorderStore by inject()
    private val sink: ExternalSink by inject()
    private val watcher: ProviderWatcher by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat(notification(0))
        store.setRunning(true)
        sink.streaming("providers", true)
        watcher.ensureObservers()
        scope.launch {
            store.count.collectLatest { total ->
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(total))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        store.setRunning(false)
        sink.streaming("providers", false)
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(total: Int): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.provider_channel), NotificationManager.IMPORTANCE_LOW))
        val openIntent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .putExtra(com.snatik.storage.app.navigation.NAV_TARGET_EXTRA, com.snatik.storage.app.navigation.NAV_PROVIDER_WATCH)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, ProviderRecorderService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(getString(R.string.provider_notif_title))
            .setContentText(getString(R.string.provider_notif_text, total))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.recorder_stop), stop)
            .build()
    }

    companion object {
        private const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "provider_recorder"
        private const val NOTIFICATION_ID = 48

        fun start(context: Context) {
            val intent = Intent(context, ProviderRecorderService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ProviderRecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
