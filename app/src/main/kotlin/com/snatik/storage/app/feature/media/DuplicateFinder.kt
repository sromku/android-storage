package com.snatik.storage.app.feature.media

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds duplicate and near-duplicate photos with a 256-bit difference hash (dHash over a 16x16
 * grid). Images are grouped around a seed when within [THRESHOLD] differing bits - seed-based
 * clustering (not transitive union-find) so unrelated shots never chain into one giant blob.
 */
object DuplicateFinder {

    private const val SIDE = 16          // hash grid; 16x16 -> 256 bits
    private const val BITS = SIDE * SIDE
    private const val WORDS = BITS / 64  // 4 longs
    private const val THRESHOLD = 16     // out of 256 (~6%): tolerates re-saves/crops, not new scenes

    suspend fun findGroups(context: Context, items: List<MediaItem>): List<List<MediaItem>> =
        withContext(Dispatchers.IO) {
            val photos = items.filter { !it.isVideo }
            val hashes = arrayOfNulls<LongArray>(photos.size)
            for (i in photos.indices) hashes[i] = dHash(context, photos[i])

            val used = BooleanArray(photos.size)
            val groups = ArrayList<List<MediaItem>>()
            for (i in photos.indices) {
                val hi = hashes[i] ?: continue
                if (used[i]) continue
                val members = ArrayList<Int>().apply { add(i) }
                for (j in i + 1 until photos.size) {
                    val hj = hashes[j] ?: continue
                    if (used[j]) continue
                    if (distance(hi, hj) <= THRESHOLD) members.add(j)
                }
                if (members.size >= 2) {
                    members.forEach { used[it] = true }
                    groups.add(members.map { photos[it] }.sortedByDescending { it.size })
                }
            }
            groups.sortedByDescending { it.size }
        }

    private fun distance(a: LongArray, b: LongArray): Int {
        var d = 0
        for (w in 0 until WORDS) d += java.lang.Long.bitCount(a[w] xor b[w])
        return d
    }

    private suspend fun dHash(context: Context, item: MediaItem): LongArray? {
        val loader = coil3.SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(mediaModel(item))
            .size(Size(96, 96))
            .allowHardware(false)
            .build()
        val bmp = runCatching { loader.execute(request).image?.toBitmap() }.getOrNull() ?: return null
        val small = runCatching { bmp.scale(SIDE + 1, SIDE) }.getOrNull() ?: return null

        val hash = LongArray(WORDS)
        var bit = 0
        for (y in 0 until SIDE) {
            for (x in 0 until SIDE) {
                if (luma(small.getPixel(x, y)) > luma(small.getPixel(x + 1, y))) {
                    hash[bit ushr 6] = hash[bit ushr 6] or (1L shl (bit and 63))
                }
                bit++
            }
        }
        return hash
    }

    private fun luma(p: Int): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** Total bytes that could be reclaimed by keeping one copy per group. */
    fun reclaimable(groups: List<List<MediaItem>>): Long =
        groups.sumOf { g -> g.drop(1).sumOf { it.size } }
}
