package com.snatik.storage.app.feature.viewer

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.core.fs.FileKind
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import com.snatik.storage.core.shell.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

sealed interface MediaState {
    data object Loading : MediaState
    data class Ready(val playPath: String, val staged: Boolean) : MediaState
    data object Unplayable : MediaState
}

class MediaViewModel(private val path: String, private val context: Context, private val privilege: PrivilegeManager) : ViewModel() {
    private val _state = MutableStateFlow<MediaState>(MediaState.Loading)
    val state: StateFlow<MediaState> = _state.asStateFlow()
    private var staged: String? = null

    init { viewModelScope.launch { _state.value = resolve() } }

    private suspend fun resolve(): MediaState = withContext(Dispatchers.IO) {
        val f = File(path)
        if (f.canRead()) return@withContext MediaState.Ready(path, staged = false)
        // Not readable in-process (e.g. under Android/data): stage a copy through the shell into our
        // own external cache, which both the shell and this app can access, then play that.
        val exec = privilege.executor.value ?: return@withContext MediaState.Unplayable
        val dir = File(context.externalCacheDir, "mediaplay").apply { deleteRecursively(); mkdirs() }
        val dest = File(dir, f.name.ifEmpty { "media" })
        val r = runCatching {
            exec.run("cp -f ${path.shellQuote()} ${dest.absolutePath.shellQuote()} && chmod 0664 ${dest.absolutePath.shellQuote()}", timeoutMs = 10 * 60_000)
        }.getOrNull()
        if (r?.ok == true && dest.exists() && dest.canRead()) {
            staged = dest.absolutePath
            MediaState.Ready(dest.absolutePath, staged = true)
        } else MediaState.Unplayable
    }

    override fun onCleared() {
        staged?.let { runCatching { File(it).delete() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MediaViewerScreen(path: String, onBack: () -> Unit, viewModel: MediaViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val context = LocalContext.current
    val activity = remember(context) { context as? android.app.Activity }
    val name = remember(path) { path.substringAfterLast('/') }
    val isAudio = remember(path) { FileKind.of(name, false) == FileKind.AUDIO }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fullscreen by remember { mutableStateOf(false) }

    // Drive orientation + immersive system bars from the fullscreen toggle; always restore on exit.
    DisposableEffect(fullscreen) {
        val window = activity?.window
        val controller = window?.let { androidx.core.view.WindowInsetsControllerCompat(it, it.decorView) }
        if (fullscreen) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window?.let { androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, false) } // draw under the bars
            controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()) // hide status + navigation bars
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, true) }
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, true) }
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up), tint = Color.White) } },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                )
            }
        },
    ) { padding ->
        // In fullscreen the top bar is gone, so ignore its inset and let the video fill the screen.
        val contentModifier = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(padding)
        Box(modifier = contentModifier.background(Color.Black), contentAlignment = Alignment.Center) {
            when (val s = state) {
                is MediaState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.media_preparing), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                is MediaState.Unplayable -> EmptyState(Icons.Default.Block, stringResource(R.string.media_unreadable), stringResource(R.string.media_unreadable_body))
                is MediaState.Ready -> Player(s.playPath, isAudio, name, fullscreenEnabled = !isAudio, onFullscreenToggle = { fullscreen = it })
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun Player(playPath: String, isAudio: Boolean, name: String, fullscreenEnabled: Boolean, onFullscreenToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val player = remember(playPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(playPath))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(playPath) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                setBackgroundColor(android.graphics.Color.BLACK)
                // The built-in fullscreen button rotates the player to landscape and goes immersive.
                if (fullscreenEnabled) setFullscreenButtonClickListener { isFull -> onFullscreenToggle(isFull) }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
    if (isAudio) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(96.dp))
            Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}
