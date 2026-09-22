package com.snatik.storage.app.feature.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
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
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setBackgroundColor(android.graphics.Color.BLACK)
                            player = viewModel.player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

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
                IconButton(onClick = { viewModel.splitAtPlayhead() }) {
                    Icon(Icons.Default.ContentCut, contentDescription = stringResource(R.string.video_split), tint = Color.White)
                }
                IconButton(onClick = { viewModel.deleteSelected() }, enabled = state.clips.size > 1) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = if (state.clips.size > 1) Color.White else Color.DarkGray)
                }
            }

            Timeline(state, onSeek = viewModel::seekToGlobal, onSelect = viewModel::select, onTrim = viewModel::trimSelected)
            Spacer(Modifier.height(8.dp))
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
private fun Timeline(
    state: VideoStudioState,
    onSeek: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onTrim: (Long, Long) -> Unit,
) {
    val density = LocalDensity.current
    val pxPerMs = with(density) { PX_PER_SECOND.dp.toPx() } / 1000f
    val trackHeight = 76.dp
    val scroll = rememberScrollState()
    val sidePad = with(density) { (LocalConfiguration.current.screenWidthDp.dp.toPx() / 2f).toDp() }

    // Playback drives the scroll so the playhead (fixed centre) sits over the current time.
    LaunchedEffect(state.positionMs, state.playing) {
        if (state.playing) scroll.scrollTo((state.positionMs * pxPerMs).toInt())
    }
    // User scrubbing drives the playhead: when the user drags the timeline, seek to the centre time.
    LaunchedEffect(pxPerMs) {
        snapshotFlow { scroll.value to scroll.isScrollInProgress }.collect { (v, dragging) ->
            if (dragging && !state.playing) onSeek((v / pxPerMs).toLong())
        }
    }

    Box(Modifier.fillMaxWidth().height(trackHeight)) {
        Row(
            Modifier.fillMaxWidth().height(trackHeight).horizontalScroll(scroll).padding(horizontal = sidePad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.clips.forEach { clip ->
                ClipView(
                    clip = clip,
                    widthPx = clip.durationMs * pxPerMs,
                    selected = clip.id == state.selectedId,
                    pxPerMs = pxPerMs,
                    onSelect = { onSelect(clip.id) },
                    onTrim = onTrim,
                )
                Spacer(Modifier.width(2.dp))
            }
        }
        Box(Modifier.align(Alignment.Center).width(2.dp).height(trackHeight).background(MaterialTheme.colorScheme.primary))
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
    val widthDp = with(density) { widthPx.toDp() }.coerceAtLeast(24.dp)
    val thumb by produceState<Bitmap?>(initialValue = null, clip.uri, clip.startMs, clip.endMs) {
        value = withContext(Dispatchers.IO) { frameAt(clip.uri, (clip.startMs + clip.endMs) / 2) }
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
        thumb?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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

private fun fmt(ms: Long): String {
    val totalS = ms / 1000
    return "%d:%02d.%d".format(totalS / 60, totalS % 60, (ms % 1000) / 100)
}

private fun frameAt(uri: Uri, atMs: Long): Bitmap? = runCatching {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(uri.path)
        r.getScaledFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 240)
    } finally {
        r.release()
    }
}.getOrNull()
