package com.snatik.storage.app.feature.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs

// Decoded waveforms (peak amplitude per bucket, 0..1), keyed by uri, so a track's silhouette is
// computed once and reused across recompositions and re-selection.
private val waveformCache = android.util.LruCache<String, FloatArray>(24)

fun cachedWaveform(uri: Uri): FloatArray? = waveformCache.get(uri.toString())

/**
 * Decode [uri]'s audio to PCM and reduce it to [buckets] peak amplitudes (0..1) for a timeline
 * waveform. Runs off the main thread; returns null if the file has no decodable audio.
 */
fun extractWaveform(context: Context, uri: Uri, buckets: Int = 320): FloatArray? {
    waveformCache.get(uri.toString())?.let { return it }
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    return try {
        extractor.setDataSource(context, uri, null)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { trackIndex = i; format = f; break }
        }
        val fmt = format ?: return null
        if (trackIndex < 0) return null
        extractor.selectTrack(trackIndex)
        val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durationUs <= 0L) return null
        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
        codec = MediaCodec.createDecoderByType(mime).apply { configure(fmt, null, null, 0); start() }

        val peaks = FloatArray(buckets)
        val info = MediaCodec.BufferInfo()
        var inputEos = false
        var outputEos = false
        var guard = 0
        while (!outputEos && guard++ < 200_000) {
            if (!inputEos) {
                val inIndex = codec.dequeueInputBuffer(5000)
                if (inIndex >= 0) {
                    val buf = codec.getInputBuffer(inIndex)
                    val size = if (buf != null) extractor.readSampleData(buf, 0) else -1
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEos = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0); extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 5000)
            if (outIndex >= 0) {
                val buf = codec.getOutputBuffer(outIndex)
                if (buf != null && info.size > 0) {
                    val shorts = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    var peak = 0
                    while (shorts.hasRemaining()) {
                        val v = abs(shorts.get().toInt())
                        if (v > peak) peak = v
                        // Sample every 4th short - plenty for a silhouette and much faster on long tracks.
                        val skip = minOf(3, shorts.remaining())
                        if (skip > 0) shorts.position(shorts.position() + skip)
                    }
                    val bucket = ((info.presentationTimeUs.toDouble() / durationUs) * buckets).toInt().coerceIn(0, buckets - 1)
                    val amp = peak / 32768f
                    if (amp > peaks[bucket]) peaks[bucket] = amp
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEos = true
            }
        }
        // Normalize to the loudest peak so quiet tracks still show a full silhouette.
        val max = peaks.maxOrNull() ?: 0f
        if (max > 0f) for (i in peaks.indices) peaks[i] = peaks[i] / max
        waveformCache.put(uri.toString(), peaks)
        peaks
    } catch (e: Exception) {
        null
    } finally {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor.release() }
    }
}
