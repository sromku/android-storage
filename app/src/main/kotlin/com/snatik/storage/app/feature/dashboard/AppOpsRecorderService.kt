package com.snatik.storage.app.feature.dashboard

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
import com.snatik.storage.core.apps.AppOpsRecorderStore
import com.snatik.storage.core.apps.AppOpsTimeline
import com.snatik.storage.core.apps.ExternalSink
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Records the device app-op timeline while the app is backgrounded or closed: a sticky foreground
 * service that polls `dumpsys appops` on an interval and de-dupes each poll's accesses into the
 * shared [AppOpsRecorderStore] (and, when configured, streams them to an external collector).
 */
class AppOpsRecorderService : Service() {

    private val privilege: PrivilegeManager by inject()
    private val store: AppOpsRecorderStore by inject()
    private val sink: ExternalSink by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var total = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat(notification())
        store.setRunning(true)
        sink.streaming("appops", true)
        scope.launch { pollLoop() }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            val exec = privilege.executor.value
            if (exec != null) {
                runCatching {
                    val pollTime = System.currentTimeMillis()
                    val text = exec.run("dumpsys appops", timeoutMs = 30_000).out
                    val accesses = AppOpsTimeline.parse(text)
                    val added = store.record(accesses, pollTime)
                    if (sink.enabled) runCatching { sink.sendAppOps(accesses, pollTime) }
                    if (added > 0) {
                        total += added
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
                    }
                }
            }
            delay(store.intervalSec.value.coerceAtLeast(10) * 1000L)
        }
    }

    override fun onDestroy() {
        store.setRunning(false)
        sink.streaming("appops", false)
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

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.recorder_channel), NotificationManager.IMPORTANCE_LOW))
        val openIntent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .putExtra(com.snatik.storage.app.navigation.NAV_TARGET_EXTRA, com.snatik.storage.app.navigation.NAV_APPOPS_TIMELINE)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, AppOpsRecorderService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(getString(R.string.recorder_notif_title))
            .setContentText(getString(R.string.recorder_notif_text, total))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.recorder_stop), stop)
            .build()
    }

    companion object {
        private const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "appops_recorder"
        private const val NOTIFICATION_ID = 47

        fun start(context: Context) {
            val intent = Intent(context, AppOpsRecorderService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AppOpsRecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
