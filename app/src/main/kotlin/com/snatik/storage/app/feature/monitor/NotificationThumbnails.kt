package com.snatik.storage.app.feature.monitor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * On-disk cache of notification images so recorded rows keep their large icon and big-picture
 * attachment across app restarts (the in-memory [NotificationImageCache] only covers the current
 * process). Images are downscaled and kept to a bounded number of files — old ones are pruned as
 * new ones arrive, and the whole store is cleared when the recording is cleared.
 */
object NotificationThumbnails {

    private const val DIR = "notif_thumbs"
    private const val MAX_FILES = 400          // ~200 notifications (large + big each)
    private const val LARGE_MAX = 256
    private const val PICTURE_MAX = 768

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    private fun hash(key: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun largeFile(context: Context, key: String) = File(dir(context), hash(key) + "_l.png")
    private fun pictureFile(context: Context, key: String) = File(dir(context), hash(key) + "_b.jpg")

    /** Persist whatever images this notification carries, then prune the store back to its cap. */
    fun save(context: Context, key: String, images: NotificationImages) {
        runCatching {
            images.largeIcon?.let { write(largeFile(context, key), it, LARGE_MAX, png = true) }
            images.bigPicture?.let { write(pictureFile(context, key), it, PICTURE_MAX, png = false) }
            prune(context)
        }
    }

    /** Load persisted images for a record, or null if none are stored. */
    fun get(context: Context, key: String): NotificationImages? {
        val large = largeFile(context, key).let { if (it.exists()) BitmapFactory.decodeFile(it.path) else null }
        val picture = pictureFile(context, key).let { if (it.exists()) BitmapFactory.decodeFile(it.path) else null }
        return if (large != null || picture != null) NotificationImages(large, picture) else null
    }

    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }

    private fun write(file: File, bitmap: Bitmap, max: Int, png: Boolean) {
        val scaled = scale(bitmap, max)
        FileOutputStream(file).use {
            if (png) scaled.compress(Bitmap.CompressFormat.PNG, 100, it)
            else scaled.compress(Bitmap.CompressFormat.JPEG, 82, it)
        }
    }

    private fun scale(bitmap: Bitmap, max: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= max && h <= max) return bitmap
        val ratio = minOf(max.toFloat() / w, max.toFloat() / h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
    }

    /** Keep only the newest [MAX_FILES] files by modification time. */
    private fun prune(context: Context) {
        val files = dir(context).listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedByDescending { it.lastModified() }.drop(MAX_FILES).forEach { it.delete() }
    }
}
