package com.snatik.storage.app.feature.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
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
            sink = MediaStoreSink(this),
        ).also { it.start() }
        CameraSync.setRunning(true, host, port, user, pass)
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

    private class MediaStoreSink(private val context: Context) : StoreSink {
        override fun begin(dir: String, name: String): StoredItem? = runCatching {
            val resolver = context.contentResolver
            val video = name.substringAfterLast('.', "").lowercase() in VIDEO_EXTS
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES}/Storage Studio/Camera")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    val base = File(Environment.getExternalStoragePublicDirectory(if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES), "Storage Studio/Camera").apply { mkdirs() }
                    put(MediaStore.MediaColumns.DATA, File(base, name).absolutePath)
                }
            }
            val uri = resolver.insert(collection, values) ?: return null
            val out = resolver.openOutputStream(uri) ?: return null
            object : StoredItem {
                override val output: OutputStream = out
                override fun commit(bytes: Long) {
                    runCatching { out.close() }
                    if (Build.VERSION.SDK_INT >= 29) {
                        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    }
                    CameraSync.addFile(name, bytes)
                }
                override fun abort() {
                    runCatching { out.close() }
                    runCatching { resolver.delete(uri, null, null) }
                }
            }
        }.getOrNull()

        private companion object {
            val VIDEO_EXTS = setOf("mp4", "mov", "mts", "m2ts", "avi", "mkv")
        }
    }

    companion object {
        const val ACTION_STOP = "com.snatik.storage.app.camera.STOP"
        private const val CHANNEL = "camera_sync"
        private const val NOTIF_ID = 4711

        fun start(context: Context) {
            val i = Intent(context, CameraSyncService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CameraSyncService::class.java).setAction(ACTION_STOP))
        }
    }
}
