package com.snatik.storage.app.feature.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.util.Intents
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(path: String, onBack: () -> Unit, viewModel: PdfViewerViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        if (state.pageCount > 0) Text(stringResource(R.string.pdf_pages, state.pageCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { IconButton(onClick = { Intents.openWith(context, path) }) { Icon(Icons.Default.OpenInNew, contentDescription = stringResource(R.string.open_with)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> EmptyState(Icons.Default.PictureAsPdf, stringResource(R.string.pdf_failed), state.error)
                else -> Pages(state, viewModel)
            }
        }
    }
}

@Composable
private fun Pages(state: PdfViewerState, viewModel: PdfViewerViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Render pages a little wider than the container so text stays crisp, but bounded.
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtMost(2200)
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            items(state.pageCount) { index ->
                PdfPage(index, widthPx, state.aspect, viewModel)
            }
        }
    }
}

@Composable
private fun PdfPage(index: Int, widthPx: Int, aspect: Float, viewModel: PdfViewerViewModel) {
    var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index, widthPx) { bitmap = viewModel.renderPage(index, widthPx) }
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f / aspect).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = stringResource(R.string.pdf_page, index + 1), modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        } else {
            CircularProgressIndicator(Modifier.size(28.dp))
        }
    }
}
