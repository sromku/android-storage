package com.snatik.storage.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.snatik.storage.core.fs.FileKind
import com.snatik.storage.core.fs.FsEntry
import java.io.File

fun FileKind.icon(): ImageVector = when (this) {
    FileKind.DIRECTORY -> Icons.Default.Folder
    FileKind.IMAGE -> Icons.Default.Image
    FileKind.VIDEO -> Icons.Default.Movie
    FileKind.AUDIO -> Icons.Default.AudioFile
    FileKind.TEXT -> Icons.Default.Description
    FileKind.CODE -> Icons.Default.Code
    FileKind.JSON, FileKind.XML -> Icons.Default.DataObject
    FileKind.PDF -> Icons.Default.PictureAsPdf
    FileKind.ARCHIVE -> Icons.Default.Archive
    FileKind.APK -> Icons.Default.Android
    FileKind.DATABASE -> Icons.Default.Storage
    FileKind.FONT -> Icons.Default.FontDownload
    FileKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@Composable
fun FileKind.tint(): Color = when (this) {
    FileKind.DIRECTORY -> MaterialTheme.colorScheme.primary
    FileKind.IMAGE, FileKind.VIDEO -> MaterialTheme.colorScheme.tertiary
    FileKind.APK, FileKind.DATABASE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Leading visual for a directory entry: a tinted glyph, a thumbnail for media, or a check mark when selected.
 */
@Composable
fun FileKindIcon(entry: FsEntry, selected: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val kind = entry.kind
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier.size(44.dp).clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            kind == FileKind.IMAGE || kind == FileKind.VIDEO -> {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context).data(File(entry.path)).size(THUMBNAIL_PX).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp),
                )
            }
            else -> Icon(kind.icon(), contentDescription = null, tint = kind.tint())
        }
    }
}

private const val THUMBNAIL_PX = 128
