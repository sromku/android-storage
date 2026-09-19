package com.snatik.storage.app.feature.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.snatik.storage.app.R
import com.snatik.storage.app.feature.viewer.ImageInfoSheet
import com.snatik.storage.app.util.Intents
import org.koin.compose.koinInject

/**
 * Full-screen, swipeable viewer over the whole photo gallery. Opens at [startId] and pages
 * through the repo's last-loaded ordered list. Images support pinch and double-tap zoom;
 * videos open in the dedicated player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPagerScreen(
    startId: Long,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    repo: MediaRepository = koinInject(),
) {
    val items = remember { repo.cached }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val startIndex = remember(startId) { items.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val current = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
            key = { items[it].id },
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPage(item, onPlay = { onOpenVideo(item.path) }, onTapChrome = { chromeVisible = !chromeVisible })
            } else {
                ZoomableImage(
                    model = item.uri,
                    onTap = { chromeVisible = !chromeVisible },
                    onZoomChange = { z -> if (page == pagerState.currentPage) zoomed = z },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (showInfo) ImageInfoSheet(current.path) { showInfo = false }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Text(
                        "${current.name}   ·   ${pagerState.currentPage + 1} / ${items.size}",
                        maxLines = 1,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up), tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, contentDescription = stringResource(R.string.details), tint = Color.White) }
                    IconButton(onClick = { Intents.share(context, listOf(current.path)) }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share), tint = Color.White) }
                    IconButton(onClick = { Intents.openWith(context, current.path) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.open_with), tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f)),
            )
        }
    }
}

@Composable
private fun VideoPage(item: MediaItem, onPlay: () -> Unit, onTapChrome: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(item.id) { detectTapGestures(onTap = { onTapChrome() }) },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayCircle, contentDescription = stringResource(R.string.play), tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(72.dp))
        }
    }
}

/** An image that pinches and double-taps to zoom, while leaving single-finger swipes to the pager. */
@Composable
private fun ZoomableImage(
    model: Any?,
    onTap: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale) { onZoomChange(scale > 1f) }

    Box(
        modifier
            .pointerInput(model) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    },
                )
            }
            .pointerInput(model) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        when {
                            pressed >= 2 -> {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 8f)
                                offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                                event.changes.forEach { it.consume() }
                            }
                            scale > 1f && pressed == 1 -> {
                                offset += event.calculatePan()
                                event.changes.forEach { it.consume() }
                            }
                            // single finger at scale 1: leave the drag for the pager
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
