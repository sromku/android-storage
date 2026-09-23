package com.snatik.storage.app.feature.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.snatik.storage.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PX_PER_SECOND = 90 // timeline scale (dp per second of source)

/** Which kind of media the in-app file explorer is picking. */
private enum class FilePickKind { AUDIO, VIDEO }

/** Route entry: builds the studio view model for [path]. */
@UnstableApi
@Composable
fun VideoStudioRoute(path: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val factory = androidx.lifecycle.viewmodel.viewModelFactory {
        addInitializer(VideoStudioViewModel::class) { VideoStudioViewModel(app, path) }
    }
    val vm: VideoStudioViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = path, factory = factory)
    VideoStudioScreen(path, onBack, vm)
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoStudioScreen(path: String, onBack: () -> Unit, viewModel: VideoStudioViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { viewModel.addImageOverlay(it) } }

    LaunchedEffect(state.message) {
        state.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessage() }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    // Which kind of file the in-app explorer is picking (null = closed).
    var filePicker by remember { mutableStateOf<FilePickKind?>(null) }
    var confirmDelete by remember { mutableStateOf(false) } // gate deletes behind a confirm sheet

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.video_studio_title), color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up), tint = Color.White) } },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = stringResource(R.string.video_add), tint = Color.White)
                    }
                    Box(
                        Modifier
                            .padding(end = 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (state.exporting) Color.White.copy(alpha = 0.3f) else Color.White)
                            .clickable(enabled = !state.exporting) { viewModel.export() }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    ) {
                        Text(stringResource(R.string.video_export), color = Color.Black, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.fillMaxWidth().weight(1.3f), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setBackgroundColor(android.graphics.Color.BLACK)
                            setKeepContentOnPlayerReset(true) // hold the last frame through split/rebuild instead of flashing black
                            player = viewModel.player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                OverlayLayer(
                    state = state,
                    onMove = viewModel::setOverlayPosition,
                    onSelect = viewModel::selectOverlay,
                )
            }

            // Fixed transport bar: play/pause + tools stay put no matter how much you add below.
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.playPause() }) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                }
                Text(
                    "${fmt(state.positionMs)} / ${fmt(state.totalMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { filePicker = FilePickKind.VIDEO }) {
                    Icon(androidx.compose.material.icons.Icons.Default.LibraryAdd, contentDescription = stringResource(R.string.video_insert), tint = Color.White)
                }
                IconButton(onClick = { viewModel.splitAtPlayhead() }) {
                    Icon(Icons.Default.ContentCut, contentDescription = stringResource(R.string.video_split), tint = Color.White)
                }
                val canDelete = state.activeAudio || state.clips.size > 1
                IconButton(onClick = { confirmDelete = true }, enabled = canDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = if (canDelete) Color.White else Color.DarkGray)
                }
            }

            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                SpeedRow(state, onSetSpeed = { s -> viewModel.setClipSpeed(state.selectedId, s) })
                Timeline(
                    state,
                    onScrubPos = viewModel::scrubSeek, // seek the real player as you drag: frame-accurate, smooth like playback
                    onScrubEnd = { ms -> viewModel.seekToGlobal(ms) },
                    onSelect = viewModel::select,
                    onTrimStart = viewModel::trimStartDelta, onTrimEnd = viewModel::trimEndDelta, onTrimCommit = viewModel::commitTrim,
                    onSelectOverlay = viewModel::selectOverlay, onShiftOverlay = viewModel::shiftOverlay,
                    onTrimOverlayStart = viewModel::trimOverlayStart, onTrimOverlayEnd = viewModel::trimOverlayEnd,
                    onSelectAudio = viewModel::selectAudio, onShiftAudio = viewModel::setAudioStartDelta,
                    onTrimAudioStart = viewModel::trimAudioStart, onTrimAudioEnd = viewModel::trimAudioEnd,
                )
                OverlayBar(state, viewModel)
                AudioBar(state, viewModel, onHide = { viewModel.clearAudioSelection() }, onRequestDelete = { confirmDelete = true })
                Spacer(Modifier.height(8.dp))
            }
        }

        if (confirmDelete) {
            val isAudio = state.activeAudio
            ConfirmDeleteSheet(
                title = stringResource(if (isAudio) R.string.video_delete_music_q else R.string.video_delete_clip_q),
                onDismiss = { confirmDelete = false },
                onConfirm = { confirmDelete = false; viewModel.deleteSelected() },
            )
        }

        if (showAddSheet) {
            AddSheet(
                onDismiss = { showAddSheet = false },
                onAddText = { viewModel.addTextOverlay(); showAddSheet = false },
                onAddSticker = { showAddSheet = false; pickImage.launch("image/*") },
                onAddMusic = { showAddSheet = false; filePicker = FilePickKind.AUDIO },
                onAddVideo = { showAddSheet = false; filePicker = FilePickKind.VIDEO },
            )
        }

        filePicker?.let { kind ->
            val audio = kind == FilePickKind.AUDIO
            com.snatik.storage.app.ui.components.MediaPickerSheet(
                kind = if (audio) com.snatik.storage.app.ui.components.MediaPickKind.AUDIO else com.snatik.storage.app.ui.components.MediaPickKind.VIDEO,
                title = stringResource(if (audio) R.string.video_pick_music else R.string.video_pick_clip),
                onDismiss = { filePicker = null },
                onPick = { uri ->
                    filePicker = null
                    if (audio) viewModel.addAudio(uri) else viewModel.insertClipAtPlayhead(uri)
                },
            )
        }

        if (state.exporting) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(progress = { state.exportProgress / 100f }, color = MaterialTheme.colorScheme.primary)
                    Text("${stringResource(R.string.video_exporting)}  ${state.exportProgress}%", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                    TextButton(onClick = { viewModel.cancelExport() }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.cancel), color = Color.White) }
                }
            }
        }
    }
}

/** Bottom sheet listing everything that can be layered onto the video, opened from the top-bar +. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(
    onDismiss: () -> Unit,
    onAddText: () -> Unit,
    onAddSticker: () -> Unit,
    onAddMusic: () -> Unit,
    onAddVideo: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1B1B1B)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.video_add_to_video),
                color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            AddRow(androidx.compose.material.icons.Icons.Default.TextFields, stringResource(R.string.video_add_text), stringResource(R.string.video_add_text_desc), onAddText)
            AddRow(androidx.compose.material.icons.Icons.Default.AddPhotoAlternate, stringResource(R.string.video_add_sticker), stringResource(R.string.video_add_sticker_desc), onAddSticker)
            AddRow(androidx.compose.material.icons.Icons.Default.MusicNote, stringResource(R.string.video_add_music), stringResource(R.string.video_add_music_desc), onAddMusic)
            AddRow(androidx.compose.material.icons.Icons.Default.LibraryAdd, stringResource(R.string.video_insert), stringResource(R.string.video_insert_desc), onAddVideo)
        }
    }
}

@Composable
private fun AddRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SpeedRow(state: VideoStudioState, onSetSpeed: (Float) -> Unit) {
    val current = state.clips.find { it.id == state.selectedId }?.speed ?: 1f
    val options = listOf(0.25f, 0.5f, 1f, 2f, 4f)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.video_speed), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        options.forEach { s ->
            val sel = kotlin.math.abs(current - s) < 0.01f
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Color.White else Color.White.copy(alpha = 0.12f))
                    .clickable { onSetSpeed(s) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(speedLabel(s), color = if (sel) Color.Black else Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun speedLabel(s: Float): String = if (s == s.toLong().toFloat()) "${s.toLong()}x" else "${s}x"

@Composable
private fun Timeline(
    state: VideoStudioState,
    onScrubPos: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onTrimStart: (Long, Long) -> Unit,
    onTrimEnd: (Long, Long) -> Unit,
    onTrimCommit: () -> Unit,
    onSelectOverlay: (Long) -> Unit,
    onShiftOverlay: (Long, Long) -> Unit,
    onTrimOverlayStart: (Long, Long) -> Unit,
    onTrimOverlayEnd: (Long, Long) -> Unit,
    onSelectAudio: (Long) -> Unit,
    onShiftAudio: (Long, Long) -> Unit,
    onTrimAudioStart: (Long, Long) -> Unit,
    onTrimAudioEnd: (Long, Long) -> Unit,
) {
    val liveState by androidx.compose.runtime.rememberUpdatedState(state)
    val context = LocalContext.current
    val density = LocalDensity.current
    val pxPerMs = with(density) { PX_PER_SECOND.dp.toPx() } / 1000f
    val videoLane = 68.dp
    val laneH = 30.dp
    val audioLaneH = 48.dp // taller, so the waveform reads like a pro editor
    val overlayLane = if (state.overlays.isNotEmpty()) laneH + 4.dp else 0.dp
    // Pack non-overlapping audio pieces onto shared rows, so cutting one track keeps both halves on
    // the same lane instead of stacking a new lane per piece.
    val audioRows = remember(state.audioTracks) {
        val rows = ArrayList<MutableList<AudioTrack>>()
        state.audioTracks.sortedBy { it.startMs }.forEach { track ->
            val end = track.startMs + track.playMs
            val row = rows.firstOrNull { r -> r.none { o -> o.startMs < end && track.startMs < o.startMs + o.playMs } }
            if (row != null) row.add(track) else rows.add(mutableListOf(track))
        }
        rows
    }
    val audioLanes = if (audioRows.isNotEmpty()) (audioLaneH + 4.dp) * audioRows.size else 0.dp
    val totalHeight = videoLane + overlayLane + audioLanes
    val scroll = rememberScrollState()
    val sidePad = with(density) { (LocalConfiguration.current.screenWidthDp.dp.toPx() / 2f).toDp() }
    val totalDp = with(density) { (state.totalMs * pxPerMs).toDp() }

    LaunchedEffect(state.positionMs, state.playing) {
        if (state.playing) scroll.scrollTo((state.positionMs * pxPerMs).toInt())
    }
    LaunchedEffect(pxPerMs) {
        var wasScrubbing = false
        var lastV = scroll.value
        snapshotFlow { Triple(scroll.value, scroll.isScrollInProgress, liveState.playing) }.collect { (v, scrolling, playing) ->
            if (playing) { wasScrubbing = false; lastV = v; return@collect }
            val ms = (v / pxPerMs).toLong()
            when {
                v != lastV -> { lastV = v; onScrubPos(ms); wasScrubbing = true } // scroll moved: scrub the player
                wasScrubbing && !scrolling -> { onScrubEnd(ms); wasScrubbing = false } // settled: exact seek for playback
            }
        }
    }

    Box(Modifier.fillMaxWidth().height(totalHeight).padding(vertical = 2.dp)) {
        Column(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = sidePad)) {
            // Video lane.
            Row(Modifier.height(videoLane), verticalAlignment = Alignment.CenterVertically) {
                state.clips.forEach { clip ->
                    ClipView(
                        clip, clip.durationMs * pxPerMs, clip.id == state.selectedId && !state.activeAudio, pxPerMs,
                        onSelect = { onSelect(clip.id) },
                        onTrimStart = { d -> onTrimStart(clip.id, d) }, onTrimEnd = { d -> onTrimEnd(clip.id, d) }, onTrimCommit = onTrimCommit,
                    )
                    Spacer(Modifier.width(1.dp))
                }
            }
            // Overlay lane.
            if (state.overlays.isNotEmpty()) {
                Box(Modifier.width(totalDp.coerceAtLeast(1.dp)).height(laneH).padding(top = 4.dp)) {
                    state.overlays.forEach { ov ->
                        val end = if (ov.endMs >= state.totalMs) state.totalMs else ov.endMs
                        TrackPill(
                            label = ov.text.ifBlank { "•" },
                            startMs = ov.startMs, lengthMs = (end - ov.startMs).coerceAtLeast(200),
                            pxPerMs = pxPerMs, selected = ov.id == state.selectedOverlayId,
                            color = MaterialTheme.colorScheme.tertiary,
                            onSelect = { onSelectOverlay(ov.id) }, onShift = { d -> onShiftOverlay(ov.id, d) },
                            onTrimStart = { d -> onTrimOverlayStart(ov.id, d) }, onTrimEnd = { d -> onTrimOverlayEnd(ov.id, d) },
                        )
                    }
                }
            }
            // Audio lanes (waveform, trimmable like a clip); split pieces share a row when they fit.
            audioRows.forEach { rowTracks ->
                Box(Modifier.width(totalDp.coerceAtLeast(1.dp)).height(audioLaneH).padding(top = 4.dp)) {
                    rowTracks.forEach { track ->
                        androidx.compose.runtime.key(track.id) {
                        val wave by produceState<FloatArray?>(initialValue = cachedWaveform(track.uri), track.uri) {
                            value = withContext(Dispatchers.IO) { extractWaveform(context, track.uri) }
                        }
                        TrackPill(
                            label = track.name,
                            startMs = track.startMs, lengthMs = track.playMs.coerceAtLeast(200),
                            pxPerMs = pxPerMs, selected = track.id == state.selectedAudioId && state.activeAudio,
                            color = MaterialTheme.colorScheme.primary,
                            onSelect = { onSelectAudio(track.id) }, onShift = { d -> onShiftAudio(track.id, d) },
                            onTrimStart = { d -> onTrimAudioStart(track.id, d) }, onTrimEnd = { d -> onTrimAudioEnd(track.id, d) },
                            waveform = wave,
                            waveStartFrac = if (track.durationMs > 0) track.clipStartMs.toFloat() / track.durationMs else 0f,
                            waveEndFrac = if (track.durationMs > 0) track.outMs.toFloat() / track.durationMs else 1f,
                        )
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).width(2.dp).height(totalHeight).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun TrackPill(
    label: String,
    startMs: Long,
    lengthMs: Long,
    pxPerMs: Float,
    selected: Boolean,
    color: Color,
    onSelect: () -> Unit,
    onShift: (Long) -> Unit,
    onTrimStart: ((Long) -> Unit)? = null,
    onTrimEnd: ((Long) -> Unit)? = null,
    waveform: FloatArray? = null,
    waveStartFrac: Float = 0f,
    waveEndFrac: Float = 1f,
) {
    val density = LocalDensity.current
    val widthDp = with(density) { (lengthMs * pxPerMs).toDp() }.coerceAtLeast(28.dp)
    // pointerInput(Unit) captures its lambdas once; keep the latest so a reordered pill (audio rows
    // re-sort by start) drags the track you actually pressed, not a stale one.
    val curSelect by androidx.compose.runtime.rememberUpdatedState(onSelect)
    val curShift by androidx.compose.runtime.rememberUpdatedState(onShift)
    Box(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset((startMs * pxPerMs).toInt(), 0) }
            .width(widthDp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) color else color.copy(alpha = 0.4f))
            .then(
                if (waveform != null) Modifier.drawBehind {
                    val wf = waveform
                    if (wf.isNotEmpty()) {
                        val h = size.height; val w = size.width
                        val barW = 2.dp.toPx(); val stride = barW + 1.5.dp.toPx()
                        val n = (w / stride).toInt().coerceAtLeast(1)
                        val col = Color.White.copy(alpha = if (selected) 0.9f else 0.6f)
                        for (bi in 0 until n) {
                            val frac = bi.toFloat() / n
                            val src = waveStartFrac + frac * (waveEndFrac - waveStartFrac)
                            val amp = wf[(src * (wf.size - 1)).toInt().coerceIn(0, wf.size - 1)].coerceIn(0.04f, 1f)
                            val barH = amp * h * 0.82f
                            val x = bi * stride + barW / 2f
                            drawLine(col, androidx.compose.ui.geometry.Offset(x, (h - barH) / 2f), androidx.compose.ui.geometry.Offset(x, (h + barH) / 2f), strokeWidth = barW)
                        }
                    }
                } else Modifier,
            )
            .border(if (selected) 2.dp else 0.dp, Color.White, RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect)
            .pointerInput(Unit) {
                // Drag the body to move the whole window along the timeline.
                detectHorizontalDragGestures { change, drag -> change.consume(); curSelect(); curShift((drag / pxPerMs).toLong()) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 14.dp))
        // When selected, drag either edge to crop the window's start / end (like a clip's trim handles).
        if (selected && onTrimStart != null && onTrimEnd != null) {
            PillTrimHandle(Alignment.CenterStart) { dxPx -> curSelect(); onTrimStart((dxPx / pxPerMs).toLong()) }
            PillTrimHandle(Alignment.CenterEnd) { dxPx -> curSelect(); onTrimEnd((dxPx / pxPerMs).toLong()) }
        }
    }
}

@Composable
private fun BoxScope.PillTrimHandle(align: Alignment, onDrag: (Float) -> Unit) {
    val curDrag by androidx.compose.runtime.rememberUpdatedState(onDrag)
    Box(
        Modifier
            .align(align)
            .fillMaxHeight()
            .width(12.dp)
            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount -> change.consume(); curDrag(dragAmount) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight(0.5f).background(Color.Black.copy(alpha = 0.5f)))
    }
}

@Composable
private fun ClipView(
    clip: VideoClip,
    widthPx: Float,
    selected: Boolean,
    pxPerMs: Float,
    onSelect: () -> Unit,
    onTrimStart: (Long) -> Unit,
    onTrimEnd: (Long) -> Unit,
    onTrimCommit: () -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val widthDp = with(density) { widthPx.toDp() }.coerceAtLeast(24.dp)
    // A real filmstrip: one thumbnail cell per ~54dp of clip width, each sampled across the source range,
    // so you can read the motion as you scrub instead of one frame smeared over the whole clip.
    val cells = (widthDp.value / 54f).toInt().coerceIn(1, 24)
    val cellWidth = widthDp / cells
    val frames by produceState(initialValue = cachedFrames(clip.uri, clip.startMs, clip.endMs, cells), clip.uri, clip.startMs, clip.endMs, cells) {
        // Decode cell frames one by one and publish after each, so the strip fills in left-to-right
        // instead of staying black until every frame is ready.
        val times = frameTimes(clip.startMs, clip.endMs, cells)
        val acc = arrayOfNulls<Bitmap>(cells)
        times.forEachIndexed { i, t -> acc[i] = frameCache.get("${clip.uri}|$t") }
        value = acc.toList()
        if (acc.any { it == null }) {
            withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, clip.uri)
                    times.forEachIndexed { i, t ->
                        if (acc[i] == null) {
                            val bmp = runCatching { r.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 320) }.getOrNull()
                            if (bmp != null) { frameCache.put("${clip.uri}|$t", bmp); acc[i] = bmp; value = acc.toList() }
                        }
                    }
                } catch (e: Exception) {
                    // leave misses null
                } finally {
                    r.release()
                }
            }
        }
        launch(Dispatchers.IO) { warmRange(context, clip.uri, clip.startMs, clip.endMs) } // fill grid gaps for scrub/splits
    }
    Box(
        Modifier
            .width(widthDp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .border(width = if (selected) 2.dp else 0.dp, color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect),
    ) {
        Row(Modifier.fillMaxSize()) {
            repeat(cells) { i ->
                Box(Modifier.width(cellWidth).fillMaxHeight().background(Color(0xFF1E1E1E))) {
                    frames.getOrNull(i)?.let { bmp ->
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        Text(
            fmt(clip.durationMs),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomStart).padding(4.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        if (clip.speed != 1f) {
            Text(
                speedLabel(clip.speed),
                color = Color.Black,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart).padding(4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (selected) {
            // Each drag delta is applied to the clip's live in/out point (accumulates correctly); the
            // player is rebuilt once when the drag ends.
            TrimHandle(Alignment.CenterStart, onDragEnd = onTrimCommit) { dxPx -> onTrimStart((dxPx / pxPerMs).toLong()) }
            TrimHandle(Alignment.CenterEnd, onDragEnd = onTrimCommit) { dxPx -> onTrimEnd((dxPx / pxPerMs).toLong()) }
        }
    }
}

@Composable
private fun BoxScope.TrimHandle(align: Alignment, onDragEnd: () -> Unit = {}, onDrag: (Float) -> Unit) {
    Box(
        Modifier
            .align(align)
            .fillMaxHeight()
            .width(18.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onHorizontalDrag = { change, dragAmount -> change.consume(); onDrag(dragAmount) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight(0.4f).background(Color.White))
    }
}

@Composable
private fun OverlayLayer(
    state: VideoStudioState,
    onMove: (Long, Float, Float) -> Unit,
    onSelect: (Long) -> Unit,
) {
    if (state.overlays.isEmpty() || state.videoWidth <= 0 || state.videoHeight <= 0) return
    val density = LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val bw = constraints.maxWidth.toFloat(); val bh = constraints.maxHeight.toFloat()
        val va = state.videoWidth.toFloat() / state.videoHeight
        val ba = bw / bh
        val vwPx = if (va > ba) bw else bh * va
        val vhPx = if (va > ba) bw / va else bh
        val left = (bw - vwPx) / 2f; val top = (bh - vhPx) / 2f
        state.overlays.forEach { ov ->
            val selected = ov.id == state.selectedOverlayId
            if (!ov.activeAt(state.positionMs) && !selected) return@forEach
            val cx = left + ov.xNorm * vwPx; val cy = top + ov.yNorm * vhPx
            var sz by remember(ov.id) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            val currentOverlay by androidx.compose.runtime.rememberUpdatedState(ov)
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((cx - sz.width / 2f).toInt(), (cy - sz.height / 2f).toInt()) }
                    .onGloballyPositioned { sz = it.size }
                    .then(if (selected) Modifier.border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp)).padding(2.dp) else Modifier)
                    .pointerInput(ov.id, vwPx, vhPx) {
                        // Accumulate from the position at drag start; adding each delta to the live xNorm
                        // (a stale composition capture) made the overlay jump back on every event.
                        var x = 0f; var y = 0f
                        detectDragGestures(
                            onDragStart = { onSelect(ov.id); x = currentOverlay.xNorm; y = currentOverlay.yNorm },
                            onDrag = { ch, drag ->
                                ch.consume()
                                x = (x + drag.x / vwPx).coerceIn(0f, 1f)
                                y = (y + drag.y / vhPx).coerceIn(0f, 1f)
                                onMove(currentOverlay.id, x, y)
                            },
                        )
                    },
            ) {
                when (ov.kind) {
                    OverlayKind.TEXT -> {
                        val fontSizeSp = with(density) { (ov.sizeFraction * vhPx).toSp() }
                        val bg = if (ov.background) Modifier.background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier
                        Text(ov.text, color = Color(ov.color), fontWeight = FontWeight.Bold, fontSize = fontSizeSp, maxLines = 1, modifier = bg)
                    }
                    OverlayKind.IMAGE -> {
                        val bmp = ov.bitmap
                        if (bmp != null) {
                            val wPx = ov.sizeFraction * vwPx
                            val hPx = wPx * (bmp.height.toFloat() / bmp.width)
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.width(with(density) { wPx.toDp() }).height(with(density) { hPx.toDp() }),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayBar(state: VideoStudioState, viewModel: VideoStudioViewModel) {
    val swatches = listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFFFF5252, 0xFF69F0AE, 0xFF40C4FF, 0xFF000000)
    val sel = state.overlays.find { it.id == state.selectedOverlayId } ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        run {
            // Editor header: title + Remove + Done (so you can dismiss/cancel the overlay editor).
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (sel.kind == OverlayKind.TEXT) stringResource(R.string.video_edit_text) else stringResource(R.string.video_edit_sticker),
                    color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = { viewModel.deleteOverlay(sel.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.video_remove), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp))
                }
                androidx.compose.material3.Button(onClick = { viewModel.selectOverlay(-1) }) { Text(stringResource(R.string.video_done)) }
            }
            if (sel.kind == OverlayKind.TEXT) {
                androidx.compose.material3.OutlinedTextField(
                    value = sel.text,
                    onValueChange = { viewModel.setOverlayText(sel.id, it) },
                    label = { Text(stringResource(R.string.video_text_label), color = Color.White.copy(alpha = 0.7f)) },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    swatches.forEach { c ->
                        Box(
                            Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(Color(c))
                                .border(if (sel.color == c.toInt()) 3.dp else 1.dp, if (sel.color == c.toInt()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f), RoundedCornerShape(50))
                                .clickable { viewModel.setOverlayColor(sel.id, c.toInt()) },
                        )
                    }
                    StudioToggle(stringResource(R.string.video_tag), sel.background) { viewModel.toggleOverlayBackground(sel.id) }
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.video_size), color = Color.White, style = MaterialTheme.typography.labelMedium)
                androidx.compose.material3.Slider(
                    value = sel.sizeFraction,
                    onValueChange = { viewModel.setOverlaySize(sel.id, it) },
                    valueRange = 0.03f..0.35f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.TextButton(onClick = { viewModel.setOverlayStartHere(sel.id) }) { Text(stringResource(R.string.video_start_here)) }
                androidx.compose.material3.TextButton(onClick = { viewModel.setOverlayEndHere(sel.id) }) { Text(stringResource(R.string.video_end_here)) }
                Text(
                    "${fmt(sel.startMs)} - ${if (sel.endMs >= state.totalMs) fmt(state.totalMs) else fmt(sel.endMs)}",
                    color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A high-contrast pill toggle for the dark studio (M3 chips read too faint on black). */
@Composable
private fun StudioToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (selected) Color.Black else Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AudioBar(state: VideoStudioState, viewModel: VideoStudioViewModel, onHide: () -> Unit, onRequestDelete: () -> Unit) {
    // Only show the editor for the actively-selected track; tap a track on the timeline to open it.
    val sel = state.audioTracks.find { it.id == state.selectedAudioId }?.takeIf { state.activeAudio } ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(androidx.compose.material.icons.Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(sel.name, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(onClick = onRequestDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
            IconButton(onClick = onHide) { Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = stringResource(R.string.video_hide), tint = Color.White) }
        }
        VolumeSlider(stringResource(R.string.video_music_volume), sel.volume) { viewModel.setAudioVolume(sel.id, it) }
        VolumeSlider(stringResource(R.string.video_video_volume), state.videoVolume) { viewModel.setVideoVolume(it) }
    }
}

/** A destructive-action confirm sheet (deletes are gated so a mis-tap can't remove a track or clip). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmDeleteSheet(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1B1B1B)) {
        Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                androidx.compose.material3.Button(
                    onClick = onConfirm,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun VolumeSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp))
        androidx.compose.material3.Slider(value = value, onValueChange = onChange, valueRange = 0f..1f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Text("${(value * 100).toInt()}%", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
    }
}

private fun fmt(ms: Long): String {
    val totalS = ms / 1000
    return "%d:%02d.%d".format(totalS / 60, totalS % 60, (ms % 1000) / 100)
}

private const val FRAME_GRID_MS = 250L // snap sample times to a fixed absolute grid, so any split/trim reuses frames
// Decoded thumbnails, keyed by "uri|gridMs" on absolute source time (not clip bounds), so a clip and
// its split halves hit the same entries; these also back the instant scrub preview. Budgeted by bytes.
private val frameCache = object : android.util.LruCache<String, Bitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun snapMs(t: Long): Long = t / FRAME_GRID_MS * FRAME_GRID_MS

/** The grid-snapped absolute source times sampled for a clip's filmstrip cells. */
private fun frameTimes(startMs: Long, endMs: Long, count: Int): List<Long> {
    val span = (endMs - startMs).coerceAtLeast(1)
    return (0 until count).map { i -> snapMs(startMs + span * (2 * i + 1) / (2 * count)) }
}

/** Frames already in the cache for these cells (no decoding), used as the initial value so a re-split
 * shows the shared frames immediately instead of flashing dark cells. */
private fun cachedFrames(uri: Uri, startMs: Long, endMs: Long, count: Int): List<Bitmap?> =
    frameTimes(startMs, endMs, count).map { t -> frameCache.get("$uri|$t") }

/** The cached low-res frame nearest a global timeline position, for the instant scrub preview. */
private fun scrubFrame(state: VideoStudioState, globalMs: Long): Bitmap? {
    val cs = state.clips
    if (cs.isEmpty()) return null
    var remaining = globalMs.coerceIn(0, state.totalMs)
    var i = 0
    while (i < cs.lastIndex && remaining > cs[i].durationMs) { remaining -= cs[i].durationMs; i++ }
    val clip = cs[i]
    val sourceMs = clip.startMs + (remaining * clip.speed).toLong()
    val target = snapMs(sourceMs)
    frameCache.get("${clip.uri}|$target")?.let { return it }
    // Not decoded yet at this exact grid point: show the nearest cached frame so the preview tracks
    // the finger instead of freezing on the pre-scrub frame while the background warm catches up.
    for (step in 1..24) {
        frameCache.get("${clip.uri}|${target + step * FRAME_GRID_MS}")?.let { return it }
        frameCache.get("${clip.uri}|${(target - step * FRAME_GRID_MS).coerceAtLeast(0)}")?.let { return it }
    }
    return null
}

/** Decode the true frame at a global timeline position (OPTION_CLOSEST, higher-res) for the resting preview. */
private fun decodeExactFrame(context: android.content.Context, state: VideoStudioState, globalMs: Long): Bitmap? {
    val cs = state.clips
    if (cs.isEmpty()) return null
    var remaining = globalMs.coerceIn(0, state.totalMs)
    var i = 0
    while (i < cs.lastIndex && remaining > cs[i].durationMs) { remaining -= cs[i].durationMs; i++ }
    val clip = cs[i]
    val sourceMs = clip.startMs + (remaining * clip.speed).toLong()
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(context, clip.uri)
        // OPTION_CLOSEST returns the actual frame nearest the time, not the nearest keyframe.
        r.getScaledFrameAtTime(sourceMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 640, 640)
    } catch (e: Exception) {
        null
    } finally {
        r.release()
    }
}

/**
 * Warm the whole source range on the fixed grid (runs after the cells, in the background). Because
 * entries are keyed on absolute source time, this makes later splits/trims and the scrub proxy instant.
 */
private fun warmRange(context: android.content.Context, uri: Uri, startMs: Long, endMs: Long) {
    val first = snapMs(startMs)
    val last = snapMs((endMs - 1).coerceAtLeast(startMs))
    val missing = generateSequence(first) { it + FRAME_GRID_MS }.takeWhile { it <= last }
        .filter { frameCache.get("$uri|$it") == null }.toList()
    if (missing.isNotEmpty()) decodeInto(context, uri, missing)
}

private fun decodeInto(context: android.content.Context, uri: Uri, times: List<Long>) {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(context, uri)
        times.forEach { t ->
            runCatching { r.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 320) }
                .getOrNull()?.let { frameCache.put("$uri|$t", it) }
        }
    } catch (e: Exception) {
        // leave misses null; those cells stay neutral
    } finally {
        r.release()
    }
}
