package com.snatik.storage.app.feature.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/** Writes camera imports (FTP or PTP/IP) into Pictures/Storage Studio/Camera so they join the gallery. */
object CameraMediaStore {

    private val VIDEO_EXTS = setOf("mp4", "mov", "mts", "m2ts", "avi", "mkv")

    fun begin(context: Context, name: String): StoredItem? = runCatching {
        val resolver = context.contentResolver
        val video = name.substringAfterLast('.', "").lowercase() in VIDEO_EXTS
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val subDir = "${if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES}/Storage Studio/Camera"
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
                CameraSync.addFile(name, bytes)
            }
            override fun abort() {
                runCatching { out.close() }
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }.getOrNull()
}
