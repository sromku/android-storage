package com.snatik.storage.app.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import com.snatik.storage.app.R
import com.snatik.storage.app.util.Intents
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var chromeVisible by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chromeVisible = !chromeVisible },
                        onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        },
                    )
                },
        ) {
            AsyncImage(
                model = File(path),
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
        AnimatedVisibility(visible = chromeVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                TopAppBar(
                    title = { Text(File(path).name, maxLines = 1, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up), tint = Color.White) }
                    },
                    actions = {
                        IconButton(onClick = { Intents.share(context, listOf(path)) }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share), tint = Color.White) }
                        IconButton(onClick = { Intents.openWith(context, path) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.open_with), tint = Color.White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                )
        }
    }
}
