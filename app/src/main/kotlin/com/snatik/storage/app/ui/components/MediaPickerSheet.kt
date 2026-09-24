package com.snatik.storage.app.ui.components

import android.content.ContentUris
import android.media.MediaPlayer
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.snatik.storage.app.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MediaPickKind { VIDEO, AUDIO }

data class PickableMedia(val uri: Uri, val name: String, val durationMs: Long)

/**
 * A bottom sheet listing the device's media of one [kind] - videos as a thumbnail grid, audio as a
 * list - so adding a clip or a song is a quick tap on a matching file, not a walk through folders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerSheet(
    kind: MediaPickKind,
    title: String,
    onDismiss: () -> Unit,
    onPick: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val items by produceState(initialValue = emptyList<PickableMedia>(), kind) {
        value = withContext(Dispatchers.IO) { queryMedia(context, kind) }
    }
    // A single shared player to hear a song before picking it; stops when another plays or on close.
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    val preview = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { runCatching { preview.release() } } }
    val togglePreview: (PickableMedia) -> Unit = { m ->
        if (previewUri == m.uri) {
            runCatching { preview.reset() }; previewUri = null
        } else {
            runCatching {
                preview.reset()
                preview.setOnCompletionListener { previewUri = null }
                preview.setDataSource(context, m.uri)
                preview.setOnPreparedListener { it.start() }
                preview.prepareAsync()
                previewUri = m.uri
            }.onFailure { previewUri = null }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            when {
                items.isEmpty() -> Text(
                    stringResource(R.string.no_files_here),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                kind == MediaPickKind.VIDEO -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 460.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(items, key = { it.uri.toString() }) { m -> VideoCell(m) { onPick(m.uri) } }
                }
                else -> LazyColumn(Modifier.heightIn(max = 460.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(items, key = { it.uri.toString() }) { m ->
                        AudioRow(
                            media = m,
                            playing = previewUri == m.uri,
                            onPlayToggle = { togglePreview(m) },
                            onSelect = { runCatching { preview.reset() }; previewUri = null; onPick(m.uri) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCell(media: PickableMedia, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
    ) {
        AsyncImage(model = media.uri, contentDescription = media.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (media.durationMs > 0) {
            Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(50), modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)) {
                Text(fmtDuration(media.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun AudioRow(media: PickableMedia, playing: Boolean, onPlayToggle: () -> Unit, onSelect: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onSelect).padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Preview play/stop, separate from picking so you can hear a song before choosing it.
        IconButton(onClick = onPlayToggle) {
            Icon(
                if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            media.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (media.durationMs > 0) Text(fmtDuration(media.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun queryMedia(context: Context, kind: MediaPickKind): List<PickableMedia> {
    val base = if (kind == MediaPickKind.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DURATION)
    val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
    val out = ArrayList<PickableMedia>()
    runCatching {
        context.contentResolver.query(base, proj, null, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val durCol = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out.add(
                    PickableMedia(
                        uri = ContentUris.withAppendedId(base, id),
                        name = c.getString(nameCol) ?: "",
                        durationMs = if (durCol >= 0 && !c.isNull(durCol)) c.getLong(durCol) else 0L,
                    ),
                )
            }
        }
    }
    return out
}
