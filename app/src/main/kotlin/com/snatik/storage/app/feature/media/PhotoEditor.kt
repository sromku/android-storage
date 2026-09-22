package com.snatik.storage.app.feature.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Non-destructive crop and resize for ordinary photos (JPEG/PNG/HEIF/WebP). Cropping keeps native
 * resolution; downscaling is a separate, explicit step. Every export writes a NEW file into
 * Pictures/Storage Studio/Edited, so the original is never touched.
 */
object PhotoEditor {

    /** Oriented (display) pixel size of the photo at [path]. */
    fun sourceSize(path: String): android.util.Size? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0) return null
        val swap = when (runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> true
            else -> false
        }
        if (swap) android.util.Size(opts.outHeight, opts.outWidth) else android.util.Size(opts.outWidth, opts.outHeight)
    }.getOrNull()

    /** A downsampled, correctly-oriented preview for the crop canvas. */
    suspend fun loadPreview(path: String, maxDim: Int = 2560): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(File(path))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > maxDim) {
                    val s = maxDim.toFloat() / longest
                    decoder.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                }
            }
        }.getOrNull()
    }

    /**
     * Crop [cropNorm] (left/top/right/bottom as fractions of the oriented image) at native resolution,
     * optionally downscale so the longest edge is [targetLongEdge] (null keeps native), and save a copy.
     * Returns the new item's uri, or null on failure.
     */
    suspend fun export(
        context: Context,
        srcPath: String,
        srcName: String,
        cropNorm: FloatArray, // [l, t, r, b]
        targetLongEdge: Int?,
        quality: Int = 95,
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val size = sourceSize(srcPath) ?: return@runCatching null
            val ow = size.width
            val oh = size.height
            val l = (cropNorm[0] * ow).toInt().coerceIn(0, ow - 1)
            val t = (cropNorm[1] * oh).toInt().coerceIn(0, oh - 1)
            val r = (cropNorm[2] * ow).toInt().coerceIn(l + 1, ow)
            val b = (cropNorm[3] * oh).toInt().coerceIn(t + 1, oh)
            val cropRect = Rect(l, t, r, b)

            val source = ImageDecoder.createSource(File(srcPath))
            var bmp = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                decoder.setCrop(cropRect) // in oriented output space, at native resolution
            }
            val cropLongest = maxOf(bmp.width, bmp.height)
            if (targetLongEdge != null && targetLongEdge in 1 until cropLongest) {
                val s = targetLongEdge.toFloat() / cropLongest
                val scaled = bmp.scale((bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1))
                if (scaled != bmp) bmp.recycle()
                bmp = scaled
            }

            val outName = "${srcName.substringBeforeLast('.')}_edit.jpg"
            val uri = saveCopy(context, bmp, outName, quality)
            bmp.recycle()
            uri
        }.getOrNull()
    }

    private fun saveCopy(context: Context, bmp: Bitmap, name: String, quality: Int): Uri? {
        val resolver = context.contentResolver
        val subDir = "${Environment.DIRECTORY_PICTURES}/Storage Studio/Edited"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, subDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val dir = File(Environment.getExternalStorageDirectory(), subDir).apply { mkdirs() }
                put(MediaStore.MediaColumns.DATA, File(dir, name).absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) } ?: false
        }.getOrDefault(false)
        if (!ok) { runCatching { resolver.delete(uri, null, null) }; return null }
        if (Build.VERSION.SDK_INT >= 29) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        return uri
    }

    /** Rough JPEG size estimate for [w]x[h] at [quality], for the "end size" hint. */
    fun estimateBytes(w: Int, h: Int, quality: Int): Long {
        val bpp = when {
            quality >= 95 -> 1.4
            quality >= 90 -> 0.9
            quality >= 80 -> 0.5
            else -> 0.3
        }
        return (w.toLong() * h.toLong() * bpp).toLong()
    }
}
