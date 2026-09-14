package com.snatik.storage.app.feature.intents

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
import com.snatik.storage.core.intents.IntentMonitorParser
import com.snatik.storage.core.intents.IntentMonitorStore
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.lines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the intent monitor running while the app is backgrounded or closed: a foreground service
 * that streams ActivityTaskManager START lines from logcat via the privileged shell into the shared
 * [IntentMonitorStore]. Sticky, so the system restarts it after the process is killed.
 */
class IntentMonitorService : Service() {

    private val privilege: PrivilegeManager by inject()
    private val store: IntentMonitorStore by inject()
    private val sink: com.snatik.storage.core.apps.ExternalSink by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var counter = 0L
    private var captured = 0
    @Volatile private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat(notification(captured))
        store.setRunning(true)
        // Backfill: send the history already in the local store, so a fresh recording streams
        // everything (like the app-ops snapshot does), not just events from now on. De-duped by key.
        scope.launch {
            if (sink.enabled) runCatching {
                val history = store.recent.first()
                if (history.isNotEmpty()) sink.send("intents", history.map { intentJson(it) })
            }
        }
        scope.launch {
            privilege.executor.collectLatest { exec ->
                if (exec == null) return@collectLatest
                runCatching {
                    exec.lines("logcat -T 1 -s ActivityTaskManager:I")
                        .mapNotNull { line -> IntentMonitorParser.parse(line, counter++, System.currentTimeMillis()) }
                        .collect { i ->
                            store.add(i)
                            if (sink.enabled) runCatching { sink.send("intents", listOf(intentJson(i))) }
                            captured++
                            val now = System.currentTimeMillis()
                            if (now - lastNotified > 1_000) {
                                lastNotified = now
                                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(captured))
                            }
                        }
                }
            }
        }
        return START_STICKY
    }

    private fun intentJson(i: com.snatik.storage.core.intents.MonitoredIntent) = org.json.JSONObject()
        .put("key", "${i.time}|${i.action.orEmpty()}|${i.packageName.orEmpty()}|${i.className.orEmpty()}|${i.callerUid ?: ""}")
        .put("at_ms", i.time)
        .put("action", i.action ?: org.json.JSONObject.NULL)
        .put("data", i.data ?: org.json.JSONObject.NULL)
        .put("type", i.type ?: org.json.JSONObject.NULL)
        .put("categories", i.categories.joinToString(","))
        .put("flags", i.flags)
        .put("package", i.packageName ?: org.json.JSONObject.NULL)
        .put("class", i.className ?: org.json.JSONObject.NULL)
        .put("caller_uid", i.callerUid ?: org.json.JSONObject.NULL)
        .put("caller_package", i.callerPackage ?: org.json.JSONObject.NULL)
        .put("has_extras", i.hasExtras)

    override fun onDestroy() {
        store.setRunning(false)
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
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.intent_monitor_channel), NotificationManager.IMPORTANCE_LOW))
        val openIntent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .putExtra(com.snatik.storage.app.navigation.NAV_TARGET_EXTRA, com.snatik.storage.app.navigation.NAV_INTENT_MONITOR)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, IntentMonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.intent_monitor_notif_title))
            .setContentText(getString(R.string.intent_monitor_notif_text, count))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.intent_monitor_stop), stop)
            .build()
    }

    companion object {
        private const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "intent_monitor"
        private const val NOTIFICATION_ID = 43

        fun start(context: Context) {
            val intent = Intent(context, IntentMonitorService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, IntentMonitorService::class.java).setAction(ACTION_STOP))
        }
    }
}
