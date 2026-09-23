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
import kotlinx.coroutines.withContext

private const val PX_PER_SECOND = 90 // timeline scale (dp per second of source)

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
    val pickAudio = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { viewModel.addAudio(it) } }
    val pickVideo = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { viewModel.insertClipAtPlayhead(it) } }

    LaunchedEffect(state.message) {
        state.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessage() }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.video_studio_title), color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up), tint = Color.White) } },
                actions = {
                    TextButton(onClick = { viewModel.export() }, enabled = !state.exporting) {
                        Text(stringResource(R.string.video_export), color = if (state.exporting) Color.Gray else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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

            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
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
                    IconButton(onClick = { pickVideo.launch("video/*") }) {
                        Icon(androidx.compose.material.icons.Icons.Default.LibraryAdd, contentDescription = stringResource(R.string.video_insert), tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.splitAtPlayhead() }) {
                        Icon(Icons.Default.ContentCut, contentDescription = stringResource(R.string.video_split), tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.deleteSelected() }, enabled = state.clips.size > 1) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = if (state.clips.size > 1) Color.White else Color.DarkGray)
                    }
                }

                SpeedRow(state, onSetSpeed = { s -> viewModel.setClipSpeed(state.selectedId, s) })
                Timeline(
                    state,
                    onSeek = viewModel::seekToGlobal, onSelect = viewModel::select, onTrim = viewModel::trimSelected,
                    onSelectOverlay = viewModel::selectOverlay, onShiftOverlay = viewModel::shiftOverlay,
                    onSelectAudio = viewModel::selectAudio, onShiftAudio = viewModel::setAudioStartDelta,
                )
                OverlayBar(state, viewModel, onPickImage = { pickImage.launch("image/*") })
                AudioBar(state, viewModel, onPickAudio = { pickAudio.launch("audio/*") })
                Spacer(Modifier.height(8.dp))
            }
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
    onSeek: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onTrim: (Long, Long) -> Unit,
    onSelectOverlay: (Long) -> Unit,
    onShiftOverlay: (Long, Long) -> Unit,
    onSelectAudio: (Long) -> Unit,
    onShiftAudio: (Long, Long) -> Unit,
) {
    val density = LocalDensity.current
    val pxPerMs = with(density) { PX_PER_SECOND.dp.toPx() } / 1000f
    val videoLane = 68.dp
    val laneH = 30.dp
    val overlayLane = if (state.overlays.isNotEmpty()) laneH + 4.dp else 0.dp
    val audioLanes = laneH * state.audioTracks.size + (if (state.audioTracks.isNotEmpty()) 4.dp else 0.dp)
    val totalHeight = videoLane + overlayLane + audioLanes
    val scroll = rememberScrollState()
    val sidePad = with(density) { (LocalConfiguration.current.screenWidthDp.dp.toPx() / 2f).toDp() }
    val totalDp = with(density) { (state.totalMs * pxPerMs).toDp() }

    LaunchedEffect(state.positionMs, state.playing) {
        if (state.playing) scroll.scrollTo((state.positionMs * pxPerMs).toInt())
    }
    LaunchedEffect(pxPerMs) {
        snapshotFlow { scroll.value to scroll.isScrollInProgress }.collect { (v, dragging) ->
            if (dragging && !state.playing) onSeek((v / pxPerMs).toLong())
        }
    }

    Box(Modifier.fillMaxWidth().height(totalHeight).padding(vertical = 2.dp)) {
        Column(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = sidePad)) {
            // Video lane.
            Row(Modifier.height(videoLane), verticalAlignment = Alignment.CenterVertically) {
                state.clips.forEach { clip ->
                    ClipView(clip, clip.durationMs * pxPerMs, clip.id == state.selectedId, pxPerMs, onSelect = { onSelect(clip.id) }, onTrim = onTrim)
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
                        )
                    }
                }
            }
            // Audio lanes.
            state.audioTracks.forEachIndexed { i, track ->
                Box(Modifier.width(totalDp.coerceAtLeast(1.dp)).height(laneH).padding(top = if (i == 0) 4.dp else 2.dp)) {
                    TrackPill(
                        label = track.name,
                        startMs = track.startMs, lengthMs = (if (track.durationMs > 0) track.durationMs else state.totalMs).coerceAtLeast(200),
                        pxPerMs = pxPerMs, selected = track.id == state.selectedAudioId,
                        color = MaterialTheme.colorScheme.primary,
                        onSelect = { onSelectAudio(track.id) }, onShift = { d -> onShiftAudio(track.id, d) },
                    )
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
) {
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset((startMs * pxPerMs).toInt(), 0) }
            .width(with(density) { (lengthMs * pxPerMs).toDp() }.coerceAtLeast(28.dp))
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) color else color.copy(alpha = 0.4f))
            .border(if (selected) 2.dp else 0.dp, Color.White, RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, drag -> change.consume(); onSelect(); onShift((drag / pxPerMs).toLong()) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

@Composable
private fun ClipView(
    clip: VideoClip,
    widthPx: Float,
    selected: Boolean,
    pxPerMs: Float,
    onSelect: () -> Unit,
    onTrim: (Long, Long) -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val widthDp = with(density) { widthPx.toDp() }.coerceAtLeast(24.dp)
    // A real filmstrip: one thumbnail cell per ~54dp of clip width, each sampled across the source range,
    // so you can read the motion as you scrub instead of one frame smeared over the whole clip.
    val cells = (widthDp.value / 54f).toInt().coerceIn(1, 24)
    val cellWidth = widthDp / cells
    val frames by produceState(initialValue = emptyList<Bitmap?>(), clip.uri, clip.startMs, clip.endMs, cells) {
        value = withContext(Dispatchers.IO) { loadFrames(context, clip.uri, clip.startMs, clip.endMs, cells) }
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
            TrimHandle(Alignment.CenterStart) { dxPx -> onTrim(clip.startMs + (dxPx / pxPerMs).toLong(), clip.endMs) }
            TrimHandle(Alignment.CenterEnd) { dxPx -> onTrim(clip.startMs, clip.endMs + (dxPx / pxPerMs).toLong()) }
        }
    }
}

@Composable
private fun BoxScope.TrimHandle(align: Alignment, onDrag: (Float) -> Unit) {
    Box(
        Modifier
            .align(align)
            .fillMaxHeight()
            .width(18.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount -> change.consume(); onDrag(dragAmount) }
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
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((cx - sz.width / 2f).toInt(), (cy - sz.height / 2f).toInt()) }
                    .onGloballyPositioned { sz = it.size }
                    .then(if (selected) Modifier.border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp)).padding(2.dp) else Modifier)
                    .pointerInput(ov.id, vwPx, vhPx) {
                        detectDragGestures(
                            onDragStart = { onSelect(ov.id) },
                            onDrag = { ch, drag -> ch.consume(); onMove(ov.id, ov.xNorm + drag.x / vwPx, ov.yNorm + drag.y / vhPx) },
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
private fun OverlayBar(state: VideoStudioState, viewModel: VideoStudioViewModel, onPickImage: () -> Unit) {
    val swatches = listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFFFF5252, 0xFF69F0AE, 0xFF40C4FF, 0xFF000000)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedButton(onClick = { viewModel.addTextOverlay() }) {
                Icon(androidx.compose.material.icons.Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.video_add_text), modifier = Modifier.padding(start = 6.dp))
            }
            androidx.compose.material3.OutlinedButton(onClick = onPickImage) {
                Icon(androidx.compose.material.icons.Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.video_add_sticker), modifier = Modifier.padding(start = 6.dp))
            }
        }
        val sel = state.overlays.find { it.id == state.selectedOverlayId }
        if (sel != null) {
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
private fun AudioBar(state: VideoStudioState, viewModel: VideoStudioViewModel, onPickAudio: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        androidx.compose.material3.OutlinedButton(
            onClick = onPickAudio,
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
        ) {
            Icon(androidx.compose.material.icons.Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.video_add_music), modifier = Modifier.padding(start = 6.dp))
        }
        if (state.audioTracks.isNotEmpty()) {
            val sel = state.audioTracks.find { it.id == state.selectedAudioId } ?: state.audioTracks.last()
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(sel.name, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = { viewModel.removeAudio(sel.id) }) { Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = null, tint = Color.White) }
            }
            VolumeSlider(stringResource(R.string.video_music_volume), sel.volume) { viewModel.setAudioVolume(sel.id, it) }
            VolumeSlider(stringResource(R.string.video_video_volume), state.videoVolume) { viewModel.setVideoVolume(it) }
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

/** Decode [count] evenly-spaced thumbnails across a clip's source range for the timeline filmstrip. */
private fun loadFrames(context: android.content.Context, uri: Uri, startMs: Long, endMs: Long, count: Int): List<Bitmap?> {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(context, uri)
        val span = (endMs - startMs).coerceAtLeast(1)
        (0 until count).map { i ->
            val t = startMs + span * (2 * i + 1) / (2 * count) // centre of the i-th cell
            runCatching { r.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 200, 200) }.getOrNull()
        }
    } catch (e: Exception) {
        List(count) { null }
    } finally {
        r.release()
    }
}
