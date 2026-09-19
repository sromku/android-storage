package com.snatik.storage.app.feature.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Makes a metadata-free copy of a photo for sharing, so location and camera EXIF never leave
 * the device. Preserves the original bytes for formats ExifInterface can rewrite (JPEG/PNG/WebP)
 * and otherwise re-encodes the pixels, which drops all metadata by construction.
 */
object MediaShare {

    private val rewritable = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

    suspend fun stripToCache(context: Context, item: MediaItem): File? = withContext(Dispatchers.IO) {
        val src = File(item.path)
        if (!src.isFile) return@withContext null
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val dst = File(dir, item.name.ifBlank { "image_${item.id}" })

        val mime = item.mime.lowercase()
        val ok = if (mime in rewritable) stripInPlace(src, dst) else false
        if (ok) dst else reencode(src, dst)
    }

    /** Copy the file untouched, then null out every EXIF tag. Keeps original image quality. */
    private fun stripInPlace(src: File, dst: File): Boolean = runCatching {
        src.copyTo(dst, overwrite = true)
        val exif = ExifInterface(dst.absolutePath)
        allExifTags().forEach { runCatching { exif.setAttribute(it, null) } }
        exif.saveAttributes()
        true
    }.getOrElse {
        dst.delete()
        false
    }

    /** Fallback for formats we can't rewrite: decode and re-encode, which carries no metadata. */
    private fun reencode(src: File, dst: File): File? = runCatching {
        val bmp = BitmapFactory.decodeFile(src.absolutePath) ?: return null
        val jpeg = File(dst.parentFile, dst.nameWithoutExtension + ".jpg")
        FileOutputStream(jpeg).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bmp.recycle()
        jpeg
    }.getOrNull()

    private fun allExifTags(): List<String> =
        ExifInterface::class.java.declaredFields
            .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
            .mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }
            .distinct()
}
