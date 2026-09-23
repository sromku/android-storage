package com.snatik.storage.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A reusable file-navigation bottom sheet: breadcrumb, subfolders, and the files under a folder that
 * match [accept] (e.g. audio or video). Tapping a file returns it. The app's own explorer, so picking
 * music or a clip stays inside the app instead of handing off to the system picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerSheet(
    title: String,
    fileIcon: ImageVector,
    accept: (File) -> Boolean,
    onDismiss: () -> Unit,
    onPick: (File) -> Unit,
    start: File = android.os.Environment.getExternalStorageDirectory(),
) {
    val root = remember { android.os.Environment.getExternalStorageDirectory() }
    var dir by remember { mutableStateOf(if (start.isDirectory) start else root) }
    val breadcrumbScroll = rememberScrollState()
    val entries by produceState(initialValue = emptyList<File>(), dir) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val all = dir.listFiles()?.filterNot { it.name.startsWith(".") } ?: return@runCatching emptyList()
                val dirs = all.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
                val files = all.filter { it.isFile && accept(it) }.sortedBy { it.name.lowercase() }
                dirs + files
            }.getOrDefault(emptyList())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))

            val segments = remember(dir) {
                val rel = dir.absolutePath.removePrefix(root.absolutePath).trim('/')
                buildList {
                    add(root)
                    if (rel.isNotEmpty()) {
                        var acc = root
                        for (s in rel.split('/')) { acc = File(acc, s); add(acc) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(breadcrumbScroll).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                segments.forEachIndexed { i, f ->
                    if (i > 0) Text(" / ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (i == 0) stringResource(R.string.internal_storage) else f.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (f == dir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (f == dir) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.clickable { dir = f }.padding(vertical = 2.dp, horizontal = 2.dp),
                    )
                }
            }

            LazyColumn(Modifier.heightIn(max = 360.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                if (dir != root) {
                    item(key = "up") {
                        EntryRow(stringResource(R.string.parent_folder), Icons.Default.SubdirectoryArrowLeft, isFolder = true, muted = true) { dir.parentFile?.let { dir = it } }
                    }
                }
                items(entries, key = { it.absolutePath }) { f ->
                    if (f.isDirectory) EntryRow(f.name, Icons.Default.Folder, isFolder = true) { dir = f }
                    else EntryRow(f.name, fileIcon, isFolder = false) { onPick(f) }
                }
                if (entries.isEmpty()) {
                    item { Text(stringResource(R.string.no_files_here), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(name: String, icon: ImageVector, isFolder: Boolean, muted: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
        if (isFolder) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
