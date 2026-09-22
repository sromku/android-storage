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
    val speed: Float = 1f,
) {
    /** Length of the trimmed source. */
    val sourceDurationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
    /** Length on the timeline (and in the output), after the speed change. */
    val durationMs: Long get() = (sourceDurationMs / speed).toLong().coerceAtLeast(0)
}

/**
 * A background music/sound track mixed under the video. [startMs] is where it begins on the timeline
 * (drag to shift), [durationMs] its source length, [volume] 0..1.
 */
data class AudioTrack(
    val id: Long,
    val uri: Uri,
    val name: String,
    val volume: Float = 0.8f,
    val startMs: Long = 0,
    val durationMs: Long = 0,
)

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
        audioTracks: List<AudioTrack>,
        videoVolume: Float,
        outFile: File,
        listener: Transformer.Listener,
    ): Transformer {
        val (w, h) = clips.firstOrNull()?.let { frameSize(it.uri.path) } ?: (1080 to 1920)
        val totalMs = clips.sumOf { it.durationMs }
        var globalMs = 0L
        val items = clips.map { clip ->
            val clipStart = globalMs
            val clipEnd = globalMs + clip.durationMs
            globalMs = clipEnd
            val builder = EditedMediaItem.Builder(clip.toMediaItem())
            val videoEffects = ArrayList<androidx.media3.common.Effect>()
            val audioProcessors = ArrayList<androidx.media3.common.audio.AudioProcessor>()
            if (clip.speed != 1f) {
                // Speed first, so overlays that follow are gated in the sped (timeline) time.
                videoEffects.add(androidx.media3.effect.SpeedChangeEffect(clip.speed))
                val sonic = androidx.media3.common.audio.SonicAudioProcessor()
                sonic.setSpeed(clip.speed)
                audioProcessors.add(sonic)
            }
            if (videoVolume != 1f) volumeProcessor(videoVolume)?.let { audioProcessors.add(it) }
            if (overlays.isNotEmpty()) {
                val textureOverlays = overlaysForClip(overlays, clipStart, clipEnd, h, w)
                if (textureOverlays.isNotEmpty()) videoEffects.add(OverlayEffect(ImmutableList.copyOf(textureOverlays)))
            }
            if (videoEffects.isNotEmpty() || audioProcessors.isNotEmpty()) {
                builder.setEffects(Effects(audioProcessors, videoEffects))
            }
            builder.build()
        }
        val videoSequence = EditedMediaItemSequence(items)

        val sequences = ArrayList<EditedMediaItemSequence>().apply { add(videoSequence) }
        if (totalMs > 0) {
            audioTracks.forEach { track ->
                val start = track.startMs.coerceIn(0, totalMs)
                val playMs = (totalMs - start).coerceAtLeast(0)
                if (playMs <= 0) return@forEach
                val audioItem = MediaItem.Builder()
                    .setUri(track.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(playMs).build(),
                    )
                    .build()
                val ab = EditedMediaItem.Builder(audioItem).setRemoveVideo(true)
                volumeProcessor(track.volume)?.let { ab.setEffects(Effects(listOf(it), emptyList())) }
                val seqBuilder = EditedMediaItemSequence.Builder()
                if (start > 0) seqBuilder.addGap(start * 1000) // leading silence to offset the track
                seqBuilder.addItem(ab.build())
                sequences.add(seqBuilder.build())
            }
        }

        val composition = Composition.Builder(sequences).build()
        val transformer = Transformer.Builder(context).addListener(listener).build()
        transformer.start(composition, outFile.absolutePath)
        return transformer
    }

    /** An audio processor that scales the signal by [gain] (0..1+), for volume / ducking. */
    private fun volumeProcessor(gain: Float): androidx.media3.common.audio.AudioProcessor? = runCatching {
        val p = androidx.media3.common.audio.ChannelMixingAudioProcessor()
        for (ch in 1..2) {
            p.putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix.create(ch, ch).scaleBy(gain))
        }
        p
    }.getOrNull()

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
