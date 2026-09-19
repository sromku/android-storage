package com.snatik.storage.app.feature.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Options for a batch JPEG conversion. */
data class ConvertOptions(
    val quality: Int = 95,
    /** Longest-edge cap in px; 0 keeps the original size. */
    val maxDim: Int = 0,
    /** Copy the source's EXIF (date, camera, GPS) into the output. Off = a clean, metadata-free JPEG. */
    val keepMetadata: Boolean = false,
)

/** Converts images (HEIC/AVIF/RAW/PNG/...) to JPEG via Coil-decoded bitmaps, saved to the gallery. */
object MediaConvert {

    private const val RELATIVE_DIR = "Pictures/Storage Studio"

    suspend fun toJpeg(context: Context, items: List<MediaItem>, options: ConvertOptions = ConvertOptions()): Int =
        withContext(Dispatchers.IO) {
            items.count { !it.isVideo && convertOne(context, it, options) }
        }

    private suspend fun convertOne(context: Context, item: MediaItem, options: ConvertOptions): Boolean {
        val loader = coil3.SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(mediaModel(item))
            .size(Size.ORIGINAL)
            .allowHardware(false)
            .build()
        var bmp = runCatching { loader.execute(request).image?.toBitmap() }.getOrNull() ?: return false
        if (options.maxDim > 0 && maxOf(bmp.width, bmp.height) > options.maxDim) {
            val scale = options.maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            bmp = runCatching { bmp.scale((bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1)) }.getOrDefault(bmp)
        }
        return save(context, item, bmp, options)
    }

    private fun save(context: Context, item: MediaItem, bmp: Bitmap, options: ConvertOptions): Boolean = runCatching {
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
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, options.quality, it) } ?: return false
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        // Re-encoding strips metadata by default; optionally copy the key EXIF back from the source.
        if (options.keepMetadata && item.path.isNotBlank()) {
            runCatching { copyExif(item.path, resolver, uri) }
        }
        true
    }.getOrDefault(false)

    private fun copyExif(srcPath: String, resolver: android.content.ContentResolver, dstUri: android.net.Uri) {
        val src = ExifInterface(srcPath)
        resolver.openFileDescriptor(dstUri, "rw")?.use { pfd ->
            val dst = ExifInterface(pfd.fileDescriptor)
            for (tag in COPY_TAGS) src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
            dst.saveAttributes()
        }
    }

    private val COPY_TAGS = listOf(
        ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
    )
}
