package com.snatik.storage.app.feature.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One image or video, as indexed by MediaStore. */
data class MediaItem(
    val id: Long,
    val uri: Uri,        // content:// uri, for thumbnail loading
    val path: String,    // file path, for the viewers / metadata / share
    val name: String,
    val isVideo: Boolean,
    val size: Long,
    val takenAt: Long,   // epoch ms, best-effort (date-taken, else date-modified)
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mime: String,
    val bucket: String,  // containing folder's display name
)

/** Reads the device's photos and videos from MediaStore, newest first. */
class MediaRepository(private val context: Context) {

    /** Last-loaded ordered list, so the pager can open by start-id without re-querying. */
    @Volatile
    var cached: List<MediaItem> = emptyList()
        private set

    /** The list the full-screen pager should page through (the currently-visible, filtered set). */
    @Volatile
    var pagerItems: List<MediaItem> = emptyList()

    suspend fun all(): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sort = "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        val out = ArrayList<MediaItem>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, args, sort)
        }.getOrNull()?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val dataC = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val nameC = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeC = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val mimeC = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val typeC = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val modC = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val takenC = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val wC = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val hC = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val durC = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
            val bucketC = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)

            fun longOr(col: Int, default: Long = 0L) = if (col >= 0) c.getLong(col) else default
            fun intOr(col: Int, default: Int = 0) = if (col >= 0) c.getInt(col) else default
            fun strOr(col: Int) = if (col >= 0) c.getString(col).orEmpty() else ""

            while (c.moveToNext()) {
                val id = c.getLong(idC)
                val isVideo = c.getInt(typeC) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val taken = longOr(takenC)
                val modifiedMs = longOr(modC) * 1000
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    path = strOr(dataC),
                    name = strOr(nameC),
                    isVideo = isVideo,
                    size = longOr(sizeC),
                    takenAt = if (taken > 0) taken else modifiedMs,
                    durationMs = longOr(durC),
                    width = intOr(wC),
                    height = intOr(hC),
                    mime = strOr(mimeC),
                    bucket = strOr(bucketC),
                )
            }
        }
        cached = out
        out
    }
}
