package com.snatik.storage.app.feature.media

import android.graphics.Bitmap
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
import coil3.size.pxOrElse
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.RandomAccessFile

/** Coil model for a camera-raw file, previewed via its embedded JPEG (with a full-decode fallback). */
data class RawImageModel(val path: String)

private val RAW_EXTENSIONS = setOf(
    "dng", "cr2", "cr3", "nef", "nrw", "arw", "sr2", "srf", "rw2",
    "raf", "orf", "srw", "pef", "dcr", "kdc", "x3f", "raw", "3fr", "mef", "mos", "iiq",
)

/** Cap on the decoded preview's long edge when the request is unbounded, to stay memory-safe. */
private const val MAX_TARGET_PX = 4096
private const val UNBOUNDED_TARGET_PX = 2560

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
        val target = targetPx()

        // Largest embedded JPEG preview: RAW files carry a full- or near-full-size JPEG in their
        // TIFF structure. This is what makes a RAW look sharp at full screen (the EXIF thumbnail is
        // only ~160px). We decode it sampled to the viewer's resolution.
        largestEmbeddedJpeg(file)?.let { data ->
            decodeSampled(data, target)?.let { bmp ->
                return ImageFetchResult(image = bmp.asImage(), isSampled = true, dataSource = DataSource.DISK)
            }
        }

        // Fallback 1: the small EXIF thumbnail, for RAW variants whose preview we can't locate.
        runCatching {
            ExifInterface(file.absolutePath).thumbnailBytes?.let { data ->
                decodeSampled(data, target)?.let { bmp ->
                    return ImageFetchResult(image = bmp.asImage(), isSampled = true, dataSource = DataSource.DISK)
                }
            }
        }

        // Fallback 2: let the platform decode the file directly (e.g. DNG on capable devices).
        val bmp = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val sample = maxOf(1, maxOf(info.size.width, info.size.height) / target)
                decoder.setTargetSampleSize(sample.coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull() ?: return null
        return ImageFetchResult(image = bmp.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun targetPx(): Int {
        val t = maxOf(options.size.width.pxOrElse { 0 }, options.size.height.pxOrElse { 0 })
        return if (t <= 0) UNBOUNDED_TARGET_PX else t.coerceAtMost(MAX_TARGET_PX)
    }

    private fun decodeSampled(data: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }

    class Factory : Fetcher.Factory<RawImageModel> {
        override fun create(data: RawImageModel, options: Options, imageLoader: ImageLoader): Fetcher =
            RawImageFetcher(data, options)
    }
}

/**
 * Walks the TIFF/EP IFD chain (and SubIFDs) of a RAW file and returns the bytes of the largest
 * embedded JPEG (tags JPEGInterchangeFormat 0x0201 / JPEGInterchangeFormatLength 0x0202). Works
 * for the common RAW dialects that are little/big-endian TIFF containers (ARW, NEF, DNG, ...).
 */
private fun largestEmbeddedJpeg(file: File): ByteArray? = runCatching {
    RandomAccessFile(file, "r").use { raf ->
        val len = raf.length()
        val head = ByteArray(8)
        raf.seek(0); raf.readFully(head)
        val little = head[0].toInt() and 0xFF == 0x49 && head[1].toInt() and 0xFF == 0x49
        val big = head[0].toInt() and 0xFF == 0x4D && head[1].toInt() and 0xFF == 0x4D
        if (!little && !big) return@use null

        fun u16(b: ByteArray, o: Int): Int =
            if (little) (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
            else ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
        fun u32(b: ByteArray, o: Int): Long =
            if (little) (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
                ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
            else ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
                ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

        var bestOff = -1L
        var bestLen = -1
        val visited = HashSet<Long>()
        val queue = ArrayDeque<Long>()
        queue.add(u32(head, 4))
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 64) {
            val off = queue.removeFirst()
            if (off <= 0 || off + 2 > len || !visited.add(off)) continue
            raf.seek(off)
            val cntB = ByteArray(2); raf.readFully(cntB)
            val count = u16(cntB, 0)
            if (count <= 0 || count > 2000 || off + 2 + count.toLong() * 12 + 4 > len) continue
            val entries = ByteArray(count * 12); raf.readFully(entries)

            var jOff = -1L
            var jLen = -1
            for (i in 0 until count) {
                val e = i * 12
                val tag = u16(entries, e)
                val type = u16(entries, e + 2)
                val num = u32(entries, e + 4)
                val valOff = u32(entries, e + 8)
                when (tag) {
                    0x0201 -> jOff = valOff
                    0x0202 -> jLen = valOff.toInt()
                    0x014A -> if (type == 4 && num >= 1) { // SubIFDs
                        if (num == 1L) queue.add(valOff)
                        else if (valOff + num * 4 <= len) {
                            raf.seek(valOff)
                            val sub = ByteArray((num * 4).toInt()); raf.readFully(sub)
                            for (k in 0 until num.toInt()) queue.add(u32(sub, k * 4))
                        }
                    }
                }
            }
            if (jOff in 1 until len && jLen in 1 until (len - jOff + 1).toInt() && jLen > bestLen) {
                bestOff = jOff; bestLen = jLen
            }
            raf.seek(off + 2 + count.toLong() * 12)
            val nextB = ByteArray(4); raf.readFully(nextB)
            queue.add(u32(nextB, 0))
        }

        if (bestOff < 0 || bestLen <= 0) return@use null
        val data = ByteArray(bestLen)
        raf.seek(bestOff); raf.readFully(data)
        // Sanity: must start with a JPEG SOI marker.
        if (data.size < 3 || data[0].toInt() and 0xFF != 0xFF || data[1].toInt() and 0xFF != 0xD8) null else data
    }
}.getOrNull()

class RawImageKeyer : Keyer<RawImageModel> {
    override fun key(data: RawImageModel, options: Options): String =
        "raw:${data.path}:${options.size.width}x${options.size.height}"
}

/** Coil model for a media item: an embedded-preview path for RAW, otherwise the content uri. */
fun mediaModel(item: MediaItem): Any =
    if (isRawMedia(item.name, item.mime) && item.path.isNotBlank()) RawImageModel(item.path) else item.uri

/**
 * Extracts a RAW file's full-resolution embedded JPEG to a cache file, so it can be opened in the
 * tiled viewer for true pixel-level zoom (Sony ARW etc. embed a full-sensor-resolution JPEG).
 * Returns the cache path, or null if no embedded JPEG could be found.
 */
suspend fun extractEmbeddedJpegToCache(context: android.content.Context, item: MediaItem): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val src = File(item.path)
        if (!src.isFile) return@withContext null
        val bytes = largestEmbeddedJpeg(src) ?: return@withContext null
        val dir = File(context.cacheDir, "raw_full").apply { mkdirs() }
        val base = item.name.substringBeforeLast('.', item.name).ifBlank { "raw_${item.id}" }
        val dst = File(dir, "$base.jpg")
        runCatching { dst.writeBytes(bytes); dst.absolutePath }.getOrNull()
    }
