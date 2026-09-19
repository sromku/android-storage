package com.snatik.storage.app.feature.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Converts images (HEIC/AVIF/RAW/PNG/...) to JPEG via Coil-decoded bitmaps, saved to the gallery. */
object MediaConvert {

    private const val RELATIVE_DIR = "Pictures/Storage Studio"

    /** Converts each non-video item to a JPEG in Pictures/Storage Studio. Returns how many succeeded. */
    suspend fun toJpeg(context: Context, items: List<MediaItem>, quality: Int = 95): Int =
        withContext(Dispatchers.IO) {
            items.count { !it.isVideo && convertOne(context, it, quality) }
        }

    private suspend fun convertOne(context: Context, item: MediaItem, quality: Int): Boolean {
        val loader = coil3.SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(mediaModel(item))
            .size(Size.ORIGINAL)
            .allowHardware(false)
            .build()
        val bmp = runCatching { loader.execute(request).image?.toBitmap() }.getOrNull() ?: return false
        return save(context, item, bmp, quality)
    }

    private fun save(context: Context, item: MediaItem, bmp: Bitmap, quality: Int): Boolean = runCatching {
        val base = item.name.substringBeforeLast('.', item.name).ifBlank { "image_${item.id}" }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$base.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Storage Studio").apply { mkdirs() }
                put(MediaStore.Images.Media.DATA, File(dir, "$base.jpg").absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) } ?: return false
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    }.getOrDefault(false)
}
