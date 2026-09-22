package com.snatik.storage.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * A reusable folder-navigation bottom sheet: breadcrumb, a scrollable list of subfolders, a
 * create-folder inline field, and a confirm button that returns the folder the user settled on.
 * Used to pick a move target, a receive destination, an export folder, and so on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    title: String,
    confirmLabel: (folderName: String) -> String,
    onDismiss: () -> Unit,
    onPick: (File) -> Unit,
    start: File = android.os.Environment.getExternalStorageDirectory(),
) {
    val root = remember { android.os.Environment.getExternalStorageDirectory() }
    var dir by remember { mutableStateOf(start) }
    var refresh by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf("") }
    val breadcrumbScroll = rememberScrollState()
    val subdirs by produceState(initialValue = emptyList<File>(), dir, refresh) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList()
            }.getOrDefault(emptyList())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
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
                        modifier = Modifier.clickable { dir = f; creating = false }.padding(vertical = 2.dp, horizontal = 2.dp),
                    )
                }
            }

            LazyColumn(Modifier.heightIn(max = 300.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                if (dir != root) {
                    item(key = "up") {
                        FolderRow(stringResource(R.string.parent_folder), Icons.Default.SubdirectoryArrowLeft, muted = true) { dir.parentFile?.let { dir = it } }
                    }
                }
                items(subdirs, key = { it.absolutePath }) { f ->
                    FolderRow(f.name, Icons.Default.Folder) { dir = f; creating = false }
                }
                if (subdirs.isEmpty() && dir != root) {
                    item { Text(stringResource(R.string.no_subfolders), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
                }
            }

            if (creating) {
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    label = { Text(stringResource(R.string.new_folder)) },
                    trailingIcon = {
                        TextButton(onClick = {
                            val name = newFolder.trim()
                            if (name.isNotEmpty()) {
                                val created = File(dir, name)
                                if (created.mkdirs() || created.isDirectory) { dir = created; refresh++ }
                                creating = false; newFolder = ""
                            }
                        }, enabled = newFolder.isNotBlank()) { Text(stringResource(R.string.create)) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            } else {
                TextButton(onClick = { creating = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.new_folder), modifier = Modifier.padding(start = 8.dp))
                }
            }

            Button(
                onClick = { onPick(dir) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(confirmLabel(if (dir == root) stringResource(R.string.internal_storage) else dir.name)) }
        }
    }
}

@Composable
private fun FolderRow(name: String, icon: ImageVector, muted: Boolean = false, onClick: () -> Unit) {
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
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
