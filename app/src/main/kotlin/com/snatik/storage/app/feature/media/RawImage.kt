package com.snatik.storage.app.feature.media

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.pxOrElse
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Coil model for a camera-raw file, previewed via its embedded JPEG (with a full-decode fallback). */
data class RawImageModel(val path: String)

private val RAW_EXTENSIONS = setOf(
    "dng", "cr2", "cr3", "nef", "nrw", "arw", "sr2", "srf", "rw2",
    "raf", "orf", "srw", "pef", "dcr", "kdc", "x3f", "raw", "3fr", "mef", "mos", "iiq",
)

fun isRawMedia(name: String, mime: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext in RAW_EXTENSIONS) return true
    val m = mime.lowercase()
    return "dng" in m || "x-adobe" in m || m.startsWith("image/x-") && RAW_EXTENSIONS.any { it in m }
}

class RawImageFetcher(private val model: RawImageModel, private val options: Options) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val file = File(model.path)
        if (!file.isFile) return null

        // Embedded preview first: fast, and works for RAW formats the platform can't fully decode.
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            exif.thumbnailBytes?.let { bytes ->
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    return ImageFetchResult(image = bmp.asImage(), isSampled = true, dataSource = DataSource.DISK)
                }
            }
        }

        // Fallback: let the platform decode the file directly (DNG on capable devices).
        val target = maxOf(options.size.width.px(), options.size.height.px(), 512)
        val bmp = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val sample = maxOf(1, maxOf(info.size.width, info.size.height) / target)
                decoder.setTargetSampleSize(sample.coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull() ?: return null

        return ImageFetchResult(image = bmp.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun Dimension.px(): Int = pxOrElse { 0 }

    class Factory : Fetcher.Factory<RawImageModel> {
        override fun create(data: RawImageModel, options: Options, imageLoader: ImageLoader): Fetcher =
            RawImageFetcher(data, options)
    }
}

class RawImageKeyer : Keyer<RawImageModel> {
    override fun key(data: RawImageModel, options: Options): String =
        "raw:${data.path}:${options.size.width}x${options.size.height}"
}

/** Coil model for a media item: an embedded-preview path for RAW, otherwise the content uri. */
fun mediaModel(item: MediaItem): Any =
    if (isRawMedia(item.name, item.mime) && item.path.isNotBlank()) RawImageModel(item.path) else item.uri
