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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.snatik.storage.app.R

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
    onTiled: (String) -> Unit,
    repo: MediaRepository = koinInject(),
    favorites: FavoritesStore = koinInject(),
) {
    val favIds by favorites.ids.collectAsStateWithLifecycle()
    val items = remember { repo.pagerItems.ifEmpty { repo.cached } }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val startIndex = remember(startId) { items.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var showHistogram by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var histogram by remember { mutableStateOf<Histogram?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val current = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) onBack() // item gone; leave the viewer
    }
    fun deleteCurrent() {
        val uris = listOf(current.uri)
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = android.provider.MediaStore.createDeleteRequest(context.contentResolver, uris)
            deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            runCatching { uris.forEach { context.contentResolver.delete(it, null, null) } }
            onBack()
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.photos_delete_title)) },
            text = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.photos_delete_body, 1, 1)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false; deleteCurrent() }) {
                    Text(stringResource(R.string.delete), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    LaunchedEffect(current.id, showHistogram) {
        histogram = if (showHistogram && !current.isVideo) computeHistogram(context, mediaModel(current)) else null
    }

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
                    model = mediaModel(item),
                    onTap = { chromeVisible = !chromeVisible },
                    onZoomChange = { z -> if (page == pagerState.currentPage) zoomed = z },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (showInfo) MediaInfoSheet(current) { showInfo = false }
        if (showPrivacy) PrivacyReportSheet(current) { showPrivacy = false }

        histogram?.let { h ->
            if (showHistogram) {
                HistogramOverlay(
                    histogram = h,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                )
            }
        }

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
                    val isFav = current.id in favIds
                    IconButton(onClick = { favorites.toggle(current.id) }) {
                        Icon(
                            if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.favorite),
                            tint = if (isFav) Color(0xFFFF4081) else Color.White,
                        )
                    }
                    Box {
                        IconButton(onClick = { overflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions), tint = Color.White)
                        }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.details)) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = { overflow = false; showInfo = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_original)) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = { overflow = false; Intents.share(context, listOf(current.path)) },
                            )
                            if (supportsTiling(current)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.full_resolution)) },
                                    leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null) },
                                    onClick = {
                                        overflow = false
                                        val target = current
                                        if (isRawMedia(target.name, target.mime)) {
                                            // RAW: view its full-resolution embedded JPEG tiled.
                                            Toast.makeText(context, R.string.full_resolution_preparing, Toast.LENGTH_SHORT).show()
                                            scope.launch {
                                                val path = extractEmbeddedJpegToCache(context, target)
                                                if (path != null) onTiled(path)
                                                else Toast.makeText(context, R.string.full_resolution_failed, Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            onTiled(target.path)
                                        }
                                    },
                                )
                            }
                            if (!current.isVideo) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.histogram)) },
                                    leadingIcon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                                    trailingIcon = { if (showHistogram) Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = { overflow = false; showHistogram = !showHistogram },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.privacy_report)) },
                                    leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                                    onClick = { overflow = false; showPrivacy = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_stripped)) },
                                    leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                                    onClick = {
                                        overflow = false
                                        val target = current
                                        Toast.makeText(context, R.string.share_stripping, Toast.LENGTH_SHORT).show()
                                        scope.launch {
                                            val clean = MediaShare.stripToCache(context, target)
                                            if (clean != null) Intents.share(context, listOf(clean.absolutePath))
                                            else Toast.makeText(context, R.string.share_strip_failed, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.open_with)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                onClick = { overflow = false; Intents.openWith(context, current.path) },
                            )
                            if (!current.isVideo) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.use_as)) },
                                    leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = null) },
                                    onClick = { overflow = false; Intents.useAs(context, current.path) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { overflow = false; confirmDelete = true },
                            )
                        }
                    }
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
