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

    val player: ExoPlayer = ExoPlayer.Builder(application).build()


    private val _state = MutableStateFlow(VideoStudioState())
    val state: StateFlow<VideoStudioState> = _state.asStateFlow()

    private val uri: Uri = Uri.fromFile(File(path))
    private var nextId = 1L
    private var transformer: Transformer? = null

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
        })
        applyCurrentSpeed()
        // Playhead ticker.
        viewModelScope.launch {
            while (true) {
                if (player.isPlaying) { _state.value = _state.value.copy(positionMs = globalPosition()); syncAudio() }
                delay(33)
            }
        }
    }

    private fun clips() = _state.value.clips

    /** Sum of clip durations before [index]. */
    private fun prefix(index: Int): Long = clips().take(index).sumOf { it.durationMs }

    private fun globalPosition(): Long {
        val cs = clips()
        if (cs.isEmpty()) return 0
        val idx = player.currentMediaItemIndex.coerceIn(0, cs.lastIndex)
        // player.currentPosition is source-clip time; divide by speed to get timeline time.
        val local = (player.currentPosition / cs[idx].speed).toLong()
        return (prefix(idx) + local).coerceIn(0, _state.value.totalMs)
    }

    /** Match the player's playback speed to the clip currently under the playhead. */
    private fun applyCurrentSpeed() {
        val cs = clips()
        val idx = player.currentMediaItemIndex.coerceIn(0, cs.lastIndex.coerceAtLeast(0))
        val speed = cs.getOrNull(idx)?.speed ?: 1f
        runCatching { player.playbackParameters = androidx.media3.common.PlaybackParameters(speed) }
    }

    private fun rebuildPlaylist(seekToGlobalMs: Long) {
        val cs = clips()
        player.setMediaItems(cs.map { it.toMediaItem() })
        player.prepare()
        seekToGlobal(seekToGlobalMs)
        applyCurrentSpeed()
        _state.value = _state.value.copy(totalMs = cs.sumOf { it.durationMs })
    }

    /** Map a global timeline ms to (clip index, local ms) and seek the player there. */
    fun seekToGlobal(globalMs: Long) {
        val cs = clips()
        if (cs.isEmpty()) return
        var remaining = globalMs.coerceIn(0, _state.value.totalMs)
        var index = 0
        while (index < cs.lastIndex && remaining > cs[index].durationMs) { remaining -= cs[index].durationMs; index++ }
        val sourceLocal = (remaining * cs[index].speed).toLong().coerceIn(0, cs[index].sourceDurationMs)
        player.seekTo(index, sourceLocal)
        applyCurrentSpeed()
        _state.value = _state.value.copy(positionMs = globalMs.coerceIn(0, _state.value.totalMs))
        syncAudio()
    }

    fun playPause() {
        if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) seekToGlobal(0); player.play() }
        syncAudio()
    }
    fun select(id: Long) { _state.value = _state.value.copy(selectedId = id) }

    /** Split the clip under the playhead into two, so the middle can be cut out or an ad inserted. */
    fun splitAtPlayhead() {
        val cs = clips().toMutableList()
        if (cs.isEmpty()) return
        var remaining = _state.value.positionMs
        var index = 0
        while (index < cs.lastIndex && remaining > cs[index].durationMs) { remaining -= cs[index].durationMs; index++ }
        val clip = cs[index]
        // remaining is timeline-local; convert to a source-time cut point.
        val cutMs = clip.startMs + (remaining * clip.speed).toLong()
        if (cutMs <= clip.startMs + 40 || cutMs >= clip.endMs - 40) return // too close to an edge
        val left = clip.copy(id = nextId++, endMs = cutMs)
        val right = clip.copy(id = nextId++, startMs = cutMs)
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
                cs[index] = clip.copy(id = nextId++, endMs = cutSource)
                cs.add(index + 1, newClip)
                cs.add(index + 2, clip.copy(id = nextId++, startMs = cutSource))
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
        val cs = clips()
        if (cs.size <= 1) return // keep at least one clip
        val id = _state.value.selectedId
        val remaining = cs.filterNot { it.id == id }
        _state.value = _state.value.copy(clips = remaining, selectedId = remaining.first().id)
        rebuildPlaylist(seekToGlobalMs = 0)
    }

    /** Trim the selected clip's in/out points (source ms). */
    fun trimSelected(newStartMs: Long, newEndMs: Long) {
        val cs = clips().toMutableList()
        val i = cs.indexOfFirst { it.id == _state.value.selectedId }
        if (i < 0) return
        val c = cs[i]
        val s = newStartMs.coerceIn(0, newEndMs - 100)
        val e = newEndMs.coerceAtLeast(s + 100)
        cs[i] = c.copy(startMs = s, endMs = e)
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

    /** Set the selected overlay's visible window to start / end at the current playhead. */
    fun setOverlayStartHere(id: Long) = mutateOverlay(id) { it.copy(startMs = _state.value.positionMs.coerceAtMost(it.endMs - 100)) }
    fun setOverlayEndHere(id: Long) = mutateOverlay(id) { it.copy(endMs = _state.value.positionMs.coerceAtLeast(it.startMs + 100)) }

    fun deleteOverlay(id: Long) {
        _state.value = _state.value.copy(overlays = _state.value.overlays.filterNot { it.id == id }, selectedOverlayId = -1)
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
        val name = runCatching {
            getApplication<Application>().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "Audio"
        val durationMs = runCatching {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(getApplication(), uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L }
            finally { r.release() }
        }.getOrDefault(0L)
        val track = AudioTrack(id = nextAudioId++, uri = uri, name = name, startMs = _state.value.positionMs, durationMs = durationMs)
        _state.value = _state.value.copy(audioTracks = _state.value.audioTracks + track, selectedAudioId = track.id)
        audioPlayers[track.id] = ExoPlayer.Builder(getApplication()).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            volume = track.volume
            prepare()
        }
        syncAudio()
    }

    fun selectAudio(id: Long) { _state.value = _state.value.copy(selectedAudioId = id) }

    fun removeAudio(id: Long) {
        audioPlayers.remove(id)?.release()
        _state.value = _state.value.copy(audioTracks = _state.value.audioTracks.filterNot { it.id == id }, selectedAudioId = -1)
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
            val local = pos - track.startMs
            val inRange = local >= 0 && (track.durationMs == 0L || local < track.durationMs)
            if (inRange) {
                if (kotlin.math.abs(mp.currentPosition - local) > 140) mp.seekTo(local)
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
