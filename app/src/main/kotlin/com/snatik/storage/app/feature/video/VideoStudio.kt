package com.snatik.storage.app.feature.video

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File

/**
 * One segment on the timeline: a source video clipped to [startMs]..[endMs]. Splitting a clip makes
 * two clips over the same source; deleting the middle one cuts that part; the clips play and export
 * back to back, which is how cutting, merging and inserting an ad clip all work.
 */
data class VideoClip(
    val id: Long,
    val uri: Uri,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

/** Builds the edited MediaItem playlist (for preview) from the timeline clips. */
fun VideoClip.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(uri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build(),
        )
        .build()

@UnstableApi
object VideoExporter {

    /**
     * Export [clips] concatenated into one MP4 at [outFile], re-encoding once at a high bitrate so the
     * result stays sharp. Returns the running [Transformer] so the caller can poll progress / cancel.
     */
    fun start(
        context: Context,
        clips: List<VideoClip>,
        overlays: List<VideoOverlay>,
        outFile: File,
        listener: Transformer.Listener,
    ): Transformer {
        val (w, h) = clips.firstOrNull()?.let { frameSize(it.uri.path) } ?: (1080 to 1920)
        var globalMs = 0L
        val items = clips.map { clip ->
            val clipStart = globalMs
            val clipEnd = globalMs + clip.durationMs
            globalMs = clipEnd
            val builder = EditedMediaItem.Builder(clip.toMediaItem())
            if (overlays.isNotEmpty()) {
                val textureOverlays = overlaysForClip(overlays, clipStart, clipEnd, h, w)
                if (textureOverlays.isNotEmpty()) {
                    val effect = OverlayEffect(ImmutableList.copyOf(textureOverlays))
                    builder.setEffects(Effects(emptyList(), listOf(effect)))
                }
            }
            builder.build()
        }
        val sequence = EditedMediaItemSequence(items)
        val composition = Composition.Builder(sequence).build()
        val transformer = Transformer.Builder(context).addListener(listener).build()
        transformer.start(composition, outFile.absolutePath)
        return transformer
    }

    private fun frameSize(path: String?): Pair<Int, Int> {
        if (path == null) return 1080 to 1920
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            w to h
        } catch (e: Exception) { 1080 to 1920 } finally { r.release() }
    }

    /** Publish a finished export file into the gallery (Movies/Storage Studio). Returns the uri. */
    fun publish(context: Context, file: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Storage Studio")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
        }.getOrDefault(false)
        if (!ok) { runCatching { resolver.delete(uri, null, null) }; return null }
        if (Build.VERSION.SDK_INT >= 29) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        return uri
    }
}
