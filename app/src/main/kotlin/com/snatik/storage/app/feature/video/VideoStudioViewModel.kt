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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class VideoStudioState(
    val clips: List<VideoClip> = emptyList(),
    val selectedId: Long = -1,
    val playing: Boolean = false,
    val positionMs: Long = 0,       // playhead on the global timeline
    val totalMs: Long = 0,
    val exporting: Boolean = false,
    val exportProgress: Int = 0,
    val message: String? = null,
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
        val durationMs = runCatching {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(path); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L }
            finally { r.release() }
        }.getOrDefault(0L)
        val clip = VideoClip(id = nextId++, uri = uri, startMs = 0, endMs = durationMs)
        _state.value = _state.value.copy(clips = listOf(clip), selectedId = clip.id, totalMs = durationMs)
        rebuildPlaylist(seekToGlobalMs = 0)
        player.playWhenReady = false

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _state.value = _state.value.copy(playing = isPlaying) }
        })
        // Playhead ticker.
        viewModelScope.launch {
            while (true) {
                if (player.isPlaying) _state.value = _state.value.copy(positionMs = globalPosition())
                delay(33)
            }
        }
    }

    private fun clips() = _state.value.clips

    /** Sum of clip durations before [index]. */
    private fun prefix(index: Int): Long = clips().take(index).sumOf { it.durationMs }

    private fun globalPosition(): Long {
        val idx = player.currentMediaItemIndex.coerceIn(0, (clips().size - 1).coerceAtLeast(0))
        return (prefix(idx) + player.currentPosition).coerceIn(0, _state.value.totalMs)
    }

    private fun rebuildPlaylist(seekToGlobalMs: Long) {
        val cs = clips()
        player.setMediaItems(cs.map { it.toMediaItem() })
        player.prepare()
        seekToGlobal(seekToGlobalMs)
        _state.value = _state.value.copy(totalMs = cs.sumOf { it.durationMs })
    }

    /** Map a global timeline ms to (clip index, local ms) and seek the player there. */
    fun seekToGlobal(globalMs: Long) {
        val cs = clips()
        if (cs.isEmpty()) return
        var remaining = globalMs.coerceIn(0, _state.value.totalMs)
        var index = 0
        while (index < cs.lastIndex && remaining > cs[index].durationMs) { remaining -= cs[index].durationMs; index++ }
        player.seekTo(index, remaining.coerceIn(0, cs[index].durationMs))
        _state.value = _state.value.copy(positionMs = globalMs.coerceIn(0, _state.value.totalMs))
    }

    fun playPause() { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) seekToGlobal(0); player.play() } }
    fun select(id: Long) { _state.value = _state.value.copy(selectedId = id) }

    /** Split the clip under the playhead into two, so the middle can be cut out or an ad inserted. */
    fun splitAtPlayhead() {
        val cs = clips().toMutableList()
        if (cs.isEmpty()) return
        var remaining = _state.value.positionMs
        var index = 0
        while (index < cs.lastIndex && remaining > cs[index].durationMs) { remaining -= cs[index].durationMs; index++ }
        val clip = cs[index]
        val cutMs = clip.startMs + remaining
        if (cutMs <= clip.startMs + 40 || cutMs >= clip.endMs - 40) return // too close to an edge
        val left = clip.copy(id = nextId++, endMs = cutMs)
        val right = clip.copy(id = nextId++, startMs = cutMs)
        cs[index] = left
        cs.add(index + 1, right)
        _state.value = _state.value.copy(clips = cs, selectedId = right.id)
        rebuildPlaylist(seekToGlobalMs = _state.value.positionMs)
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
        transformer = VideoExporter.start(app, clips(), out, listener)
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
        player.release()
    }
}
