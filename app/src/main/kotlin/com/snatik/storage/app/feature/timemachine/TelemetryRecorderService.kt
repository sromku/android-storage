package com.snatik.storage.app.feature.timemachine

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
import com.snatik.storage.app.navigation.NAV_TARGET_EXTRA
import com.snatik.storage.app.navigation.NAV_TIME_MACHINE
import com.snatik.storage.core.apps.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Records device and per-app storage in the background: a sticky foreground service that captures a
 * snapshot on start and then every [TelemetryRepository.intervalSec], so Time Machine keeps a real
 * history (and its forecast) even while the app is closed. The ongoing notification tells the user
 * this recording is active; a boot receiver restarts it after a reboot.
 */
class TelemetryRecorderService : Service() {

    private val repo: TelemetryRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var count = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            repo.setEnabled(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        repo.setEnabled(true)
        repo.setRunning(true)
        startForegroundCompat(notification())
        scope.launch { captureLoop() }
        return START_STICKY
    }

    private suspend fun captureLoop() {
        while (scope.isActive) {
            runCatching { repo.capture() }
            count = runCatching { repo.report().snapshots }.getOrDefault(count + 1)
            runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
            delay(repo.intervalSec.coerceAtLeast(60) * 1000L)
        }
    }

    override fun onDestroy() {
        repo.setRunning(false)
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
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.tm_channel), NotificationManager.IMPORTANCE_LOW))
        val openIntent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .putExtra(NAV_TARGET_EXTRA, NAV_TIME_MACHINE)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, TelemetryRecorderService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(getString(R.string.tm_notif_title))
            .setContentText(getString(R.string.tm_notif_text, count))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.recorder_stop), stop)
            .build()
    }

    companion object {
        private const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "telemetry_recorder"
        private const val NOTIFICATION_ID = 51

        fun start(context: Context) {
            val intent = Intent(context, TelemetryRecorderService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TelemetryRecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
