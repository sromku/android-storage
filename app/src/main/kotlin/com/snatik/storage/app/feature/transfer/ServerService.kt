package com.snatik.storage.app.feature.transfer

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
import com.snatik.storage.app.navigation.NAV_AGENT_API
import com.snatik.storage.app.navigation.NAV_TARGET_EXTRA
import com.snatik.storage.core.net.DEFAULT_PORT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the HTTP server (file transfer + Agent API) alive as a foreground service, so it stays
 * reachable when the app is backgrounded or closed. Both the Receive and Agent API screens start
 * and stop it; the ongoing notification shows the address and offers Stop.
 */
class ServerService : Service() {

    private val hub: TransferHub by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            hub.stopReceiving()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        hub.startReceiving()
        startForegroundCompat(notification(hub.server.state.value.addresses.firstOrNull()))
        // Refresh the notification once the server has bound and knows its address.
        scope.launch {
            hub.server.state.collect { st ->
                if (st.running) runCatching {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(st.addresses.firstOrNull()))
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
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

    private fun notification(address: String?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.server_channel), NotificationManager.IMPORTANCE_LOW))
        val where = address?.let { "$it:$DEFAULT_PORT" } ?: ":$DEFAULT_PORT"
        val openIntent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .putExtra(NAV_TARGET_EXTRA, NAV_AGENT_API)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, ServerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.server_notif_title))
            .setContentText(getString(R.string.server_notif_text, where))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.recorder_stop), stop)
            .build()
    }

    companion object {
        private const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "storage_server"
        private const val NOTIFICATION_ID = 61

        fun start(context: Context) {
            val intent = Intent(context, ServerService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ServerService::class.java).setAction(ACTION_STOP))
        }
    }
}
