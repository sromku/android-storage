package com.snatik.storage.app.feature.video

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VideoStudioState(
    val clips: List<VideoClip> = emptyList(),
    val selectedId: Long = -1,
    val overlays: List<VideoOverlay> = emptyList(),
    val selectedOverlayId: Long = -1,
    val audioTracks: List<AudioTrack> = emptyList(),
    val selectedAudioId: Long = -1,
    val activeAudio: Boolean = false, // true => the selected music track is what cut/delete act on
    val videoVolume: Float = 1f,
    val playing: Boolean = false,
    val positionMs: Long = 0,       // playhead on the global timeline
    val totalMs: Long = 0,
    val exporting: Boolean = false,
    val exportProgress: Int = 0,
    val message: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
)

@UnstableApi
class VideoStudioViewModel(application: Application, private val path: String) : AndroidViewModel(application) {

    val player: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        // Preload the next item so a boundary between clips of different speeds (which stay separate
        // items) plays through without a stall. Safe now that scrubbing mode is only used single-item.
        runCatching { setPreloadConfiguration(ExoPlayer.PreloadConfiguration(3_000_000L)) }
    }


    private val _state = MutableStateFlow(VideoStudioState())
    val state: StateFlow<VideoStudioState> = _state.asStateFlow()

    private val uri: Uri = Uri.fromFile(File(path))
    private var nextId = 1L
    private var transformer: Transformer? = null
    // Declared before init(): a property initializer here runs AFTER the init block, so if this lived
    // below init it would wipe the value that init's rebuildPlaylist() sets, leaving segments empty.
    private var segments: List<PlaySegment> = emptyList()

    init {
        val r = MediaMetadataRetriever()
        val durationMs: Long
        var vw = 0; var vh = 0
        try {
            r.setDataSource(path)
            durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            vw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = vw; vw = vh; vh = t } // display orientation
        } finally { r.release() }
        val clip = VideoClip(id = nextId++, uri = uri, startMs = 0, endMs = durationMs)
        _state.value = _state.value.copy(clips = listOf(clip), selectedId = clip.id, totalMs = durationMs, videoWidth = vw, videoHeight = vh)
        rebuildPlaylist(seekToGlobalMs = 0)
        player.playWhenReady = false

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _state.value = _state.value.copy(playing = isPlaying) }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { applyCurrentSpeed() }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    // Video finished: it's the master clock, so stop every audio track and park the
                    // playhead at the end.
                    player.playWhenReady = false
                    audioPlayers.values.forEach { it.playWhenReady = false }
                    _state.value = _state.value.copy(positionMs = _state.value.totalMs)
                }
            }
        })
        applyCurrentSpeed()
        // Playhead ticker.
        viewModelScope.launch {
            while (true) {
                if (player.isPlaying) {
                    val pos = globalPosition()
                    _state.value = _state.value.copy(positionMs = pos, selectedId = clipIdAt(pos))
                    syncAudio()
                }
                delay(33)
            }
        }
    }

    private fun clips() = _state.value.clips

    /**
     * The player's playlist, where contiguous clips of the same source at the same speed are merged into
     * one item. Splitting a clip you don't delete then plays through as a single uninterrupted source, so
     * there's no decoder re-init (and no pause) at the cut. Only real jumps - a deleted section or a
     * speed change - stay as separate items.
     */
    private data class PlaySegment(val uri: Uri, val startMs: Long, val endMs: Long, val speed: Float, val globalStartMs: Long) {
        fun toMediaItem(): androidx.media3.common.MediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs).setEndPositionMs(endMs).build(),
            ).build()
    }

    private fun buildSegments(): List<PlaySegment> {
        val cs = clips()
        val segs = ArrayList<PlaySegment>()
        var globalMs = 0L
        var i = 0
        while (i < cs.size) {
            val first = cs[i]
            var end = first.endMs
            var j = i + 1
            while (j < cs.size && cs[j].uri == cs[j - 1].uri && cs[j].speed == first.speed && cs[j].startMs == cs[j - 1].endMs) {
                end = cs[j].endMs; j++
            }
            segs.add(PlaySegment(first.uri, first.startMs, end, first.speed, globalMs))
            globalMs += ((end - first.startMs) / first.speed).toLong()
            i = j
        }
        return segs
    }

    /** Sum of clip durations before [index]. */
    private fun prefix(index: Int): Long = clips().take(index).sumOf { it.durationMs }

    /** Index of the clip that the global timeline [globalMs] falls in. */
    private fun clipIndexAt(globalMs: Long): Int {
        val cs = clips()
        if (cs.isEmpty()) return 0
        var remaining = globalMs.coerceIn(0, _state.value.totalMs)
        var index = 0
        while (index < cs.lastIndex && remaining > cs[index].durationMs) { remaining -= cs[index].durationMs; index++ }
        return index
    }

    private fun clipIdAt(globalMs: Long): Long = clips().getOrNull(clipIndexAt(globalMs))?.id ?: _state.value.selectedId

    private fun globalPosition(): Long {
        if (segments.isEmpty()) return 0
        val idx = player.currentMediaItemIndex.coerceIn(0, segments.lastIndex)
        val seg = segments[idx]
        // player.currentPosition is source-clip time; divide by speed to get timeline time.
        val local = (player.currentPosition / seg.speed).toLong()
        return (seg.globalStartMs + local).coerceIn(0, _state.value.totalMs)
    }

    /** The player's live position on the global timeline (for waiting out a seek before dropping the proxy). */
    fun currentGlobalPosition(): Long = globalPosition()

    /** Match the player's playback speed to the clip currently under the playhead. */
    private fun applyCurrentSpeed() {
        if (segments.isEmpty()) return
        val idx = player.currentMediaItemIndex.coerceIn(0, segments.lastIndex)
        val speed = segments[idx].speed
        // Only touch playback params when the speed actually changes; resetting it on every clip
        // transition caused a visible hitch between same-speed cuts.
        if (kotlin.math.abs(player.playbackParameters.speed - speed) > 0.001f) {
            runCatching { player.playbackParameters = androidx.media3.common.PlaybackParameters(speed) }
        }
    }

    private fun rebuildPlaylist(seekToGlobalMs: Long) {
        endScrubbingMode() // scrubbing mode can't handle a playlist change (setMediaItems) - it throws
        segments = buildSegments()
        player.setMediaItems(segments.map { it.toMediaItem() })
        player.prepare()
        seekToGlobal(seekToGlobalMs)
        applyCurrentSpeed()
        _state.value = _state.value.copy(totalMs = clips().sumOf { it.durationMs })
    }

    /**
     * While dragging the timeline: only move the playhead and selection. The heavy ExoPlayer seek is
     * skipped so scrubbing stays instant; the UI paints a cached low-res frame instead, and the real
     * seek runs once on release ([seekToGlobal]).
     */
    fun setScrubPosition(globalMs: Long) {
        if (clips().isEmpty()) return
        val clamped = globalMs.coerceIn(0, _state.value.totalMs)
        _state.value = _state.value.copy(positionMs = clamped, selectedId = clipIdAt(clamped))
    }

    private var scrubbing = false

    /** Lightweight seek while dragging: turn on ExoPlayer scrubbing mode (optimizes rapid frame-accurate
     * seeks) and seek only the video player, so the preview tracks the finger smoothly, like playback. */
    fun scrubSeek(globalMs: Long) {
        if (segments.isEmpty()) return
        // Scrubbing mode only works with a single media item - with cuts, a scrub that crosses a clip
        // boundary crashes ExoPlayer 1.8 (evaluateMediaItemTransitionReason). Use it only when there's
        // one segment; otherwise fall back to plain seeks.
        if (segments.size == 1) {
            if (!scrubbing) { scrubbing = true; runCatching { player.setScrubbingModeEnabled(true) } }
        } else {
            endScrubbingMode()
        }
        val clamped = globalMs.coerceIn(0, _state.value.totalMs)
        val index = segments.indexOfLast { it.globalStartMs <= clamped }.coerceIn(0, segments.lastIndex)
        val seg = segments[index]
        val sourceLocal = ((clamped - seg.globalStartMs) * seg.speed).toLong().coerceIn(0, seg.endMs - seg.startMs)
        player.seekTo(index, sourceLocal)
        _state.value = _state.value.copy(positionMs = clamped, selectedId = clipIdAt(clamped))
    }

    private fun endScrubbingMode() {
        if (scrubbing) { scrubbing = false; runCatching { player.setScrubbingModeEnabled(false) } }
    }

    /** Map a global timeline ms to (segment index, local ms) and seek the player there (frame-accurate). */
    fun seekToGlobal(globalMs: Long) {
        if (segments.isEmpty()) return
        endScrubbingMode() // leaving a drag: back to normal playback seeks
        val clamped = globalMs.coerceIn(0, _state.value.totalMs)
        val index = segments.indexOfLast { it.globalStartMs <= clamped }.coerceIn(0, segments.lastIndex)
        val seg = segments[index]
        val sourceLocal = ((clamped - seg.globalStartMs) * seg.speed).toLong().coerceIn(0, seg.endMs - seg.startMs)
        player.seekTo(index, sourceLocal)
        applyCurrentSpeed()
        // The clip under the playhead is always the selected one, so split/delete/speed act where you are.
        _state.value = _state.value.copy(positionMs = clamped, selectedId = clipIdAt(clamped))
        syncAudio()
    }

    fun playPause() {
        endScrubbingMode()
        if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) seekToGlobal(0); player.play() }
        syncAudio()
    }
    /** Tapping a clip moves the playhead to its start and selects it (forced, so a boundary tap picks
     * the tapped clip and not the one that ends there). */
    fun select(id: Long) {
        val i = clips().indexOfFirst { it.id == id }
        if (i < 0) { _state.value = _state.value.copy(selectedId = id, activeAudio = false); return }
        seekToGlobal(prefix(i))
        _state.value = _state.value.copy(selectedId = id, activeAudio = false) // focus the video track
    }

    /** Split the selected music track, or the clip under the playhead, into two at the playhead. */
    fun splitAtPlayhead() {
        if (_state.value.activeAudio && _state.value.selectedAudioId != -1L) { splitAudioAtPlayhead(); return }
        val cs = clips().toMutableList()
        if (cs.isEmpty()) return
        var remaining = _state.value.positionMs
        var index = 0
        while (index < cs.lastIndex && remaining > cs[index].durationMs) { remaining -= cs[index].durationMs; index++ }
        val clip = cs[index]
        // remaining is timeline-local; convert to a source-time cut point.
        val cutMs = clip.startMs + (remaining * clip.speed).toLong()
        if (cutMs <= clip.startMs + 40 || cutMs >= clip.endMs - 40) return // too close to an edge
        val left = clip.copy(id = nextId++, endMs = cutMs, origStartMs = clip.startMs, origEndMs = cutMs)
        val right = clip.copy(id = nextId++, startMs = cutMs, origStartMs = cutMs, origEndMs = clip.endMs)
        cs[index] = left
        cs.add(index + 1, right)
        _state.value = _state.value.copy(clips = cs, selectedId = right.id)
        rebuildPlaylist(seekToGlobalMs = _state.value.positionMs)
    }

    /** Insert another video at the playhead, splitting the current clip so an ad drops into the middle. */
    fun insertClipAtPlayhead(insertUri: android.net.Uri) {
        val durationMs = runCatching {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(getApplication(), insertUri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L }
            finally { r.release() }
        }.getOrDefault(0L)
        if (durationMs <= 0) { _state.value = _state.value.copy(message = "Could not read that video"); return }
        val newClip = VideoClip(id = nextId++, uri = insertUri, startMs = 0, endMs = durationMs)

        val cs = clips().toMutableList()
        if (cs.isEmpty()) { _state.value = _state.value.copy(clips = listOf(newClip), selectedId = newClip.id); rebuildPlaylist(0); return }
        var remaining = _state.value.positionMs
        var index = 0
        while (index < cs.lastIndex && remaining > cs[index].durationMs) { remaining -= cs[index].durationMs; index++ }
        val clip = cs[index]
        val cutSource = clip.startMs + (remaining * clip.speed).toLong()
        val insertBefore = _state.value.positionMs
        when {
            cutSource <= clip.startMs + 40 -> cs.add(index, newClip)
            cutSource >= clip.endMs - 40 -> cs.add(index + 1, newClip)
            else -> {
                cs[index] = clip.copy(id = nextId++, endMs = cutSource, origStartMs = clip.startMs, origEndMs = cutSource)
                cs.add(index + 1, newClip)
                cs.add(index + 2, clip.copy(id = nextId++, startMs = cutSource, origStartMs = cutSource, origEndMs = clip.endMs))
            }
        }
        _state.value = _state.value.copy(clips = cs, selectedId = newClip.id)
        rebuildPlaylist(seekToGlobalMs = insertBefore)
    }

    /** Set the selected clip's playback speed (0.25x..4x); the timeline and output stretch/shrink. */
    fun setClipSpeed(id: Long, speed: Float) {
        val cs = clips().toMutableList()
        val i = cs.indexOfFirst { it.id == id }
        if (i < 0) return
        cs[i] = cs[i].copy(speed = speed.coerceIn(0.25f, 4f))
        _state.value = _state.value.copy(clips = cs)
        rebuildPlaylist(seekToGlobalMs = prefix(i))
    }

    fun deleteSelected() {
        if (_state.value.activeAudio && _state.value.selectedAudioId != -1L) { removeAudio(_state.value.selectedAudioId); return }
        val cs = clips()
        if (cs.size <= 1) return // keep at least one clip
        val id = _state.value.selectedId
        val remaining = cs.filterNot { it.id == id }
        _state.value = _state.value.copy(clips = remaining, selectedId = remaining.first().id)
        rebuildPlaylist(seekToGlobalMs = 0)
    }

    /** Cut the selected music track in two at the playhead (each half keeps its own trim + player). */
    private fun splitAudioAtPlayhead() {
        val pos = _state.value.positionMs
        val tracks = _state.value.audioTracks.toMutableList()
        val i = tracks.indexOfFirst { it.id == _state.value.selectedAudioId }
        if (i < 0) return
        val t = tracks[i]
        val trackEnd = t.startMs + t.playMs
        if (pos <= t.startMs + 40 || pos >= trackEnd - 40) return // playhead not inside this track
        val cutSource = t.clipStartMs + (pos - t.startMs) // source-time cut point
        val left = t.copy(id = nextAudioId++, clipEndMs = cutSource)
        val right = t.copy(id = nextAudioId++, startMs = pos, clipStartMs = cutSource)
        tracks[i] = left
        tracks.add(i + 1, right)
        _state.value = _state.value.copy(audioTracks = tracks, selectedAudioId = right.id, activeAudio = true)
        reconcileAudioPlayers()
        syncAudio()
    }

    /** Create players for any new audio tracks and release players for removed ones. */
    private fun reconcileAudioPlayers() {
        val ids = _state.value.audioTracks.map { it.id }.toSet()
        audioPlayers.keys.filter { it !in ids }.toList().forEach { audioPlayers.remove(it)?.release() }
        _state.value.audioTracks.forEach { t ->
            if (!audioPlayers.containsKey(t.id)) {
                audioPlayers[t.id] = ExoPlayer.Builder(getApplication()).build().apply {
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(t.uri)); volume = t.volume; prepare()
                }
            }
        }
    }

    /**
     * Trim a clip's in/out point live while dragging a handle. Only updates state (the filmstrip and
     * lengths follow instantly); the player is rebuilt once on [commitTrim] when the finger lifts, so a
     * drag no longer rebuilds the playlist on every pixel (which reset the player and made cuts jump).
     */
    fun trimStartDelta(id: Long, deltaMs: Long) = trimDelta(id, deltaMs, start = true)
    fun trimEndDelta(id: Long, deltaMs: Long) = trimDelta(id, deltaMs, start = false)

    private fun trimDelta(id: Long, deltaMs: Long, start: Boolean) {
        val cs = clips().toMutableList()
        val i = cs.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = cs[i]
        cs[i] = if (start) c.copy(startMs = (c.startMs + deltaMs).coerceIn(0, c.endMs - 100))
                else c.copy(endMs = (c.endMs + deltaMs).coerceAtLeast(c.startMs + 100))
        _state.value = _state.value.copy(clips = cs, selectedId = id, totalMs = cs.sumOf { it.durationMs })
    }

    /** Apply the trimmed ranges to the player once the trim drag ends. */
    fun commitTrim() {
        val i = clips().indexOfFirst { it.id == _state.value.selectedId }.coerceAtLeast(0)
        rebuildPlaylist(seekToGlobalMs = prefix(i))
    }

    /** Double-tap a trim handle: restore the clip to its original (pre-trim) extent. */
    fun resetClipTrim(id: Long) {
        val cs = clips().toMutableList()
        val i = cs.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = cs[i]
        if (c.startMs == c.origStartMs && c.endMs == c.origEndMs) return // already full
        cs[i] = c.copy(startMs = c.origStartMs, endMs = c.origEndMs)
        _state.value = _state.value.copy(clips = cs)
        rebuildPlaylist(seekToGlobalMs = prefix(i))
    }

    // ---- Overlays ----

    private var nextOverlayId = 1L

    fun addTextOverlay() {
        val ov = VideoOverlay(
            id = nextOverlayId++, kind = OverlayKind.TEXT, text = "Text",
            xNorm = 0.5f, yNorm = 0.3f, startMs = 0, endMs = _state.value.totalMs,
        )
        _state.value = _state.value.copy(overlays = _state.value.overlays + ov, selectedOverlayId = ov.id)
    }

    fun addImageOverlay(uri: android.net.Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        val full = android.graphics.BitmapFactory.decodeStream(input) ?: return@runCatching null
                        val cap = 720
                        val longest = maxOf(full.width, full.height)
                        if (longest > cap) {
                            val s = cap.toFloat() / longest
                            full.scale((full.width * s).toInt().coerceAtLeast(1), (full.height * s).toInt().coerceAtLeast(1))
                        } else full
                    }
                }.getOrNull()
            } ?: run { _state.value = _state.value.copy(message = "Could not load image"); return@launch }
            val ov = VideoOverlay(
                id = nextOverlayId++, kind = OverlayKind.IMAGE, imageUri = uri, bitmap = bmp,
                xNorm = 0.5f, yNorm = 0.5f, sizeFraction = 0.3f, startMs = 0, endMs = _state.value.totalMs,
            )
            _state.value = _state.value.copy(overlays = _state.value.overlays + ov, selectedOverlayId = ov.id)
        }
    }

    fun selectOverlay(id: Long) { _state.value = _state.value.copy(selectedOverlayId = id) }

    private fun mutateOverlay(id: Long, f: (VideoOverlay) -> VideoOverlay) {
        _state.value = _state.value.copy(overlays = _state.value.overlays.map { if (it.id == id) f(it) else it })
    }

    fun setOverlayPosition(id: Long, xNorm: Float, yNorm: Float) =
        mutateOverlay(id) { it.copy(xNorm = xNorm.coerceIn(0f, 1f), yNorm = yNorm.coerceIn(0f, 1f)) }

    fun setOverlayText(id: Long, text: String) = mutateOverlay(id) { it.copy(text = text) }
    fun setOverlayColor(id: Long, color: Int) = mutateOverlay(id) { it.copy(color = color) }
    fun setOverlaySize(id: Long, sizeFraction: Float) = mutateOverlay(id) { it.copy(sizeFraction = sizeFraction) }
    fun toggleOverlayBackground(id: Long) = mutateOverlay(id) { it.copy(background = !it.background) }
    fun setOverlayFont(id: Long, font: String) = mutateOverlay(id) { it.copy(font = font) }
    fun setOverlayBold(id: Long, on: Boolean) = mutateOverlay(id) { it.copy(bold = on) }
    fun setOverlayItalic(id: Long, on: Boolean) = mutateOverlay(id) { it.copy(italic = on) }
    fun setOverlayOutline(id: Long, on: Boolean) = mutateOverlay(id) { it.copy(outline = on) }
    fun setOverlayOutlineColor(id: Long, color: Int) = mutateOverlay(id) { it.copy(outline = true, outlineColor = color) }
    fun setOverlayBgColor(id: Long, color: Int) = mutateOverlay(id) { it.copy(background = true, bgColor = color) }
    fun setOverlayRotation(id: Long, degrees: Float) = mutateOverlay(id) { it.copy(rotationDegrees = degrees) }
    fun setOverlayAnimIn(id: Long, anim: TextAnim) = mutateOverlay(id) { it.copy(animIn = anim) }
    fun setOverlayAnimOut(id: Long, anim: TextAnim) = mutateOverlay(id) { it.copy(animOut = anim) }
    fun setOverlayAnimInMs(id: Long, ms: Long) = mutateOverlay(id) { it.copy(animInMs = ms.coerceIn(100, 3000)) }
    fun setOverlayAnimOutMs(id: Long, ms: Long) = mutateOverlay(id) { it.copy(animOutMs = ms.coerceIn(100, 3000)) }
    fun setOverlayAnimOutReverse(id: Long, on: Boolean) = mutateOverlay(id) { it.copy(animOutReverse = on) }

    /** Set the selected overlay's visible window to start / end at the current playhead. */
    fun setOverlayStartHere(id: Long) = mutateOverlay(id) { it.copy(startMs = _state.value.positionMs.coerceAtMost(it.endMs - 100)) }
    fun setOverlayEndHere(id: Long) = mutateOverlay(id) { it.copy(endMs = _state.value.positionMs.coerceAtLeast(it.startMs + 100)) }

    fun deleteOverlay(id: Long) {
        _state.value = _state.value.copy(overlays = _state.value.overlays.filterNot { it.id == id }, selectedOverlayId = -1)
    }

    /** Crop the overlay's window start by [deltaMs] (drag the pill's left edge on the timeline). */
    fun trimOverlayStart(id: Long, deltaMs: Long) = mutateOverlay(id) {
        val end = if (it.endMs >= _state.value.totalMs) _state.value.totalMs else it.endMs
        it.copy(startMs = (it.startMs + deltaMs).coerceIn(0, end - 100))
    }

    /** Crop the overlay's window end by [deltaMs] (drag the pill's right edge on the timeline). */
    fun trimOverlayEnd(id: Long, deltaMs: Long) = mutateOverlay(id) {
        val end = if (it.endMs >= _state.value.totalMs) _state.value.totalMs else it.endMs
        it.copy(endMs = (end + deltaMs).coerceIn(it.startMs + 100, _state.value.totalMs))
    }

    /** Shift an overlay's whole time window by [deltaMs] (drag on the timeline lane). */
    fun shiftOverlay(id: Long, deltaMs: Long) {
        val total = _state.value.totalMs
        mutateOverlay(id) {
            val end = if (it.endMs >= total) total else it.endMs
            val len = end - it.startMs
            val ns = (it.startMs + deltaMs).coerceIn(0, (total - len).coerceAtLeast(0))
            it.copy(startMs = ns, endMs = ns + len)
        }
    }

    // ---- Music / audio ----

    private var nextAudioId = 1L
    private val audioPlayers = HashMap<Long, ExoPlayer>()

    fun addAudio(uri: android.net.Uri) {
        val name = if (uri.scheme == "file") File(uri.path ?: "").name
        else runCatching {
            getApplication<Application>().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "Audio"
        val durationMs = runCatching {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(getApplication(), uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L }
            finally { r.release() }
        }.getOrDefault(0L)
        val track = AudioTrack(id = nextAudioId++, uri = uri, name = name, startMs = _state.value.positionMs, durationMs = durationMs, clipStartMs = 0, clipEndMs = durationMs)
        _state.value = _state.value.copy(audioTracks = _state.value.audioTracks + track, selectedAudioId = track.id)
        audioPlayers[track.id] = ExoPlayer.Builder(getApplication()).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            volume = track.volume
            prepare()
        }
        syncAudio()
    }

    fun selectAudio(id: Long) { _state.value = _state.value.copy(selectedAudioId = id, activeAudio = true) }

    /** Hide the music editor without deleting the track (deselect). */
    fun clearAudioSelection() { _state.value = _state.value.copy(selectedAudioId = -1, activeAudio = false) }

    fun removeAudio(id: Long) {
        audioPlayers.remove(id)?.release()
        _state.value = _state.value.copy(audioTracks = _state.value.audioTracks.filterNot { it.id == id }, selectedAudioId = -1, activeAudio = false)
    }

    fun setAudioVolume(id: Long, v: Float) {
        val vol = v.coerceIn(0f, 1f)
        _state.value = _state.value.copy(audioTracks = _state.value.audioTracks.map { if (it.id == id) it.copy(volume = vol) else it })
        audioPlayers[id]?.volume = vol
    }

    fun setAudioStart(id: Long, startMs: Long) {
        _state.value = _state.value.copy(audioTracks = _state.value.audioTracks.map { if (it.id == id) it.copy(startMs = startMs.coerceIn(0, _state.value.totalMs)) else it })
        syncAudio()
    }

    /** Shift an audio track's start by [deltaMs] (drag on the timeline lane). */
    fun setAudioStartDelta(id: Long, deltaMs: Long) {
        val track = _state.value.audioTracks.find { it.id == id } ?: return
        setAudioStart(id, track.startMs + deltaMs)
    }

    /** Cut the audio's in-point (drag its left edge): reveal a later part and keep the right edge fixed. */
    fun trimAudioStart(id: Long, deltaMs: Long) {
        _state.value = _state.value.copy(
            audioTracks = _state.value.audioTracks.map { t ->
                if (t.id != id) t else {
                    val newIn = (t.clipStartMs + deltaMs).coerceIn(0, t.outMs - 100)
                    t.copy(clipStartMs = newIn, startMs = (t.startMs + (newIn - t.clipStartMs)).coerceAtLeast(0))
                }
            },
        )
        syncAudio()
    }

    /** Cut the audio's out-point (drag its right edge). */
    fun trimAudioEnd(id: Long, deltaMs: Long) {
        _state.value = _state.value.copy(
            audioTracks = _state.value.audioTracks.map { t ->
                if (t.id != id) t else t.copy(clipEndMs = (t.outMs + deltaMs).coerceIn(t.clipStartMs + 100, t.durationMs))
            },
        )
        syncAudio()
    }

    fun setVideoVolume(v: Float) {
        val vol = v.coerceIn(0f, 1f)
        _state.value = _state.value.copy(videoVolume = vol)
        player.volume = vol
    }

    /** Keep every audio track in sync with the playhead, respecting its start offset. */
    private fun syncAudio() {
        val pos = _state.value.positionMs
        _state.value.audioTracks.forEach { track ->
            val mp = audioPlayers[track.id] ?: return@forEach
            val local = pos - track.startMs // position within the track's placed (trimmed) span
            // The video is the master clock: no track plays before its start, past its trimmed length,
            // or past the end of the video.
            val inRange = local >= 0 && pos < _state.value.totalMs && local < track.playMs
            if (inRange) {
                val sourcePos = track.clipStartMs + local // map to the source, honouring the in-point
                if (kotlin.math.abs(mp.currentPosition - sourcePos) > 140) mp.seekTo(sourcePos)
                mp.playWhenReady = player.playWhenReady
            } else {
                mp.playWhenReady = false
            }
        }
    }

    fun export() {
        if (_state.value.exporting) return
        val app = getApplication<Application>()
        val out = File(app.cacheDir, "studio_${System.currentTimeMillis()}.mp4")
        _state.value = _state.value.copy(exporting = true, exportProgress = 0, message = null)
        player.pause()
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                val name = File(path).nameWithoutExtension + "_edit.mp4"
                val uri = VideoExporter.publish(app, out, name)
                out.delete()
                _state.value = _state.value.copy(exporting = false, exportProgress = 100, message = if (uri != null) "Saved to Movies/Storage Studio" else "Export saved but could not be published")
            }
            override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                out.delete()
                _state.value = _state.value.copy(exporting = false, message = "Export failed: ${exception.message}")
            }
        }
        audioPlayers.values.forEach { it.pause() }
        transformer = VideoExporter.start(app, clips(), _state.value.overlays, _state.value.audioTracks, _state.value.videoVolume, out, listener)
        // Poll progress.
        viewModelScope.launch {
            val holder = ProgressHolder()
            while (_state.value.exporting) {
                val t = transformer ?: break
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _state.value = _state.value.copy(exportProgress = holder.progress)
                }
                delay(200)
            }
        }
    }

    fun cancelExport() {
        transformer?.cancel()
        transformer = null
        _state.value = _state.value.copy(exporting = false, message = "Export cancelled")
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    override fun onCleared() {
        runCatching { transformer?.cancel() }
        audioPlayers.values.forEach { it.release() }
        player.release()
    }
}
