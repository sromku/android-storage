package com.snatik.storage.app.feature.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.snatik.storage.app.R
import java.io.File
import java.io.OutputStream
import java.net.NetworkInterface

/**
 * Runs the embedded FTP receiver as a foreground service so camera transfers keep going with the
 * screen off. Received files are written straight into MediaStore (Pictures/Storage Studio/Camera)
 * so they appear in the gallery, RAW viewer, and everything else.
 */
class CameraSyncService : Service() {

    private var server: FtpServer? = null
    private var destDir: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        destDir = intent?.getStringExtra(EXTRA_DEST)?.takeIf { it.isNotBlank() }?.let { File(it) }
        start()
        return START_STICKY
    }

    private fun start() {
        if (server != null) return
        val host = wifiIpv4() ?: run {
            CameraSync.setError(getString(R.string.camera_no_wifi))
            stopSelf()
            return
        }
        val port = CameraSyncState.DEFAULT_PORT
        val user = CameraSyncState.DEFAULT_USER
        val pass = CameraSyncState.DEFAULT_PASS

        startForeground(NOTIF_ID, notification(host, port))

        server = FtpServer(
            port = port,
            user = user,
            pass = pass,
            advertiseHost = host,
            sink = MediaStoreSink(this, destDir),
        ).also { it.start() }
        CameraSync.setRunning(true, host, port, user, pass, dest = destDir?.name ?: DEFAULT_DEST_LABEL)
    }

    private fun stopEverything() {
        server?.stop()
        server = null
        CameraSync.setStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        CameraSync.setStopped()
        super.onDestroy()
    }

    private fun notification(host: String, port: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.camera_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.camera_notif_title))
            .setContentText("ftp://$host:$port")
            .setOngoing(true)
            .build()
    }

    private fun wifiIpv4(): String? {
        // Prefer the active Wi-Fi/hotspot interface; fall back to any site-local IPv4.
        runCatching {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val ip = wm?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
        }
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList().forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                nif.inetAddresses.toList().forEach { addr ->
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains('.') == true && addr.isSiteLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
        }
        return null
    }

    private class MediaStoreSink(private val context: Context, private val destDir: File?) : StoreSink {
        override fun begin(dir: String, name: String): StoredItem? = CameraMediaStore.begin(context, name, destDir)
    }

    companion object {
        const val ACTION_STOP = "com.snatik.storage.app.camera.STOP"
        const val EXTRA_DEST = "dest_dir"
        const val DEFAULT_DEST_LABEL = "Storage Studio/Camera"
        private const val CHANNEL = "camera_sync"
        private const val NOTIF_ID = 4711

        fun start(context: Context, destDir: String? = null) {
            val i = Intent(context, CameraSyncService::class.java)
            if (!destDir.isNullOrBlank()) i.putExtra(EXTRA_DEST, destDir)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CameraSyncService::class.java).setAction(ACTION_STOP))
        }
    }
}
