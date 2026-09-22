package com.snatik.storage.app.feature.camera

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Writes camera imports (FTP or PTP/IP) into the gallery. With no destination the default is
 * Pictures/Storage Studio/Camera; when the user picks a folder we write there directly (the app holds
 * all-files access) and re-index it so it still shows up everywhere.
 */
object CameraMediaStore {

    private val VIDEO_EXTS = setOf("mp4", "mov", "mts", "m2ts", "avi", "mkv")

    fun begin(context: Context, name: String, destDir: File? = null): StoredItem? =
        if (destDir != null) beginInDir(context, name, destDir) else beginDefault(context, name)

    /** Direct-file write into any chosen folder, then a media scan so the gallery follows. */
    private fun beginInDir(context: Context, name: String, destDir: File): StoredItem? = runCatching {
        if (!destDir.exists()) destDir.mkdirs()
        val target = File(destDir, name)
        val out = FileOutputStream(target)
        object : StoredItem {
            override val output: OutputStream = out
            override fun commit(bytes: Long) {
                runCatching { out.close() }
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null) { _, uri ->
                    CameraSync.addFile(name, bytes, uri = uri?.toString(), path = target.absolutePath)
                }
            }
            override fun abort() {
                runCatching { out.close() }
                runCatching { target.delete() }
            }
        }
    }.getOrNull()

    private fun beginDefault(context: Context, name: String): StoredItem? = runCatching {
        val resolver = context.contentResolver
        val video = name.substringAfterLast('.', "").lowercase() in VIDEO_EXTS
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val subDir = "${if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES}/Storage Studio/Camera"
        val absPath = File(Environment.getExternalStorageDirectory(), "$subDir/$name").absolutePath
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, subDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val base = File(Environment.getExternalStorageDirectory(), subDir).apply { mkdirs() }
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
                CameraSync.addFile(name, bytes, uri = uri.toString(), path = absPath)
            }
            override fun abort() {
                runCatching { out.close() }
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }.getOrNull()
}
