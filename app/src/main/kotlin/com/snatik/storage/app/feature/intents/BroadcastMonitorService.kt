package com.snatik.storage.app.feature.intents

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.snatik.storage.app.MainActivity
import com.snatik.storage.app.R
import com.snatik.storage.core.intents.ActiveBroadcast
import com.snatik.storage.core.intents.BroadcastStore
import com.snatik.storage.core.intents.IntentSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the broadcast monitor receiving while the app is backgrounded or closed: a foreground
 * service that registers dynamic receivers for the store's subscribed actions and writes each
 * received broadcast (full intent, extras included) into the store. Sticky, and re-registers to
 * match the subscription set whenever it changes.
 */
class BroadcastMonitorService : Service() {

    private val store: BroadcastStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val receivers = HashMap<String, BroadcastReceiver>()
    private var captured = 0
    @Volatile private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            unregisterAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat(notification(captured))
        store.setRunning(true)
        scope.launch { store.active.collect { sync(it) } }
        return START_STICKY
    }

    private fun sync(active: List<ActiveBroadcast>) {
        val wanted = active.associateBy { it.action }
        (receivers.keys - wanted.keys).toList().forEach { action ->
            receivers.remove(action)?.let { runCatching { unregisterReceiver(it) } }
        }
        wanted.forEach { (action, ab) -> if (!receivers.containsKey(action)) register(action, ab.dataScheme) }
    }

    private fun register(action: String, scheme: String?) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                captured++
                scope.launch { store.add(IntentSpec.describe(intent)) }
                val now = System.currentTimeMillis()
                if (now - lastNotified > 1_000) {
                    lastNotified = now
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(captured))
                }
            }
        }
        val filter = IntentFilter(action).apply { scheme?.let { addDataScheme(it) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        receivers[action] = receiver
    }

    private fun unregisterAll() {
        receivers.values.forEach { runCatching { unregisterReceiver(it) } }
        receivers.clear()
    }

    override fun onDestroy() {
        store.setRunning(false)
        unregisterAll()
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

    private fun notification(count: Int): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.broadcast_monitor_channel), NotificationManager.IMPORTANCE_LOW))
        val openIntent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .putExtra(com.snatik.storage.app.navigation.NAV_TARGET_EXTRA, com.snatik.storage.app.navigation.NAV_BROADCAST_MONITOR)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, BroadcastMonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(getString(R.string.broadcast_monitor_notif_title))
            .setContentText(getString(R.string.broadcast_monitor_notif_text, count))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.intent_monitor_stop), stop)
            .build()
    }

    companion object {
        private const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "broadcast_monitor"
        private const val NOTIFICATION_ID = 44

        fun start(context: Context) {
            val intent = Intent(context, BroadcastMonitorService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BroadcastMonitorService::class.java).setAction(ACTION_STOP))
        }
    }
}
