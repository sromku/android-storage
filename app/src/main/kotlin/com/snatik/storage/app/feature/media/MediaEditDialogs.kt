package com.snatik.storage.app.feature.media

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.snatik.storage.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameSheet(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(stringResource(R.string.rename), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.rename_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onRename(text.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
            Button(
                onClick = { if (text.isNotBlank()) onRename(text.trim()) },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) { Text(stringResource(R.string.rename)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToFolderSheet(onDismiss: () -> Unit, onMove: (java.io.File) -> Unit) {
    val root = remember { android.os.Environment.getExternalStorageDirectory() }
    var dir by remember { mutableStateOf(root) }
    var refresh by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf("") }
    val breadcrumbScroll = androidx.compose.foundation.rememberScrollState()
    val subdirs by produceState(initialValue = emptyList<java.io.File>(), dir, refresh) {
        value = withContext(Dispatchers.IO) { MediaEdit.subdirs(dir) }
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
            Text(stringResource(R.string.move_to_folder), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))

            // Breadcrumb: Internal storage / seg / seg ... (each clickable to jump up).
            val segments = remember(dir) {
                val rel = dir.absolutePath.removePrefix(root.absolutePath).trim('/')
                buildList {
                    add(root)
                    if (rel.isNotEmpty()) {
                        var acc = root
                        for (s in rel.split('/')) { acc = java.io.File(acc, s); add(acc) }
                    }
                }
            }
            androidx.compose.foundation.layout.Row(
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
                        FolderRow(stringResource(R.string.parent_folder), Icons.Default.DriveFileMove, muted = true) { dir.parentFile?.let { dir = it } }
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
                        androidx.compose.material3.TextButton(onClick = {
                            val name = newFolder.trim()
                            if (name.isNotEmpty()) {
                                val created = java.io.File(dir, name)
                                if (created.mkdirs() || created.isDirectory) { dir = created; refresh++ }
                                creating = false; newFolder = ""
                            }
                        }, enabled = newFolder.isNotBlank()) { Text(stringResource(R.string.create)) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            } else {
                androidx.compose.material3.TextButton(onClick = { creating = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.new_folder), modifier = Modifier.padding(start = 8.dp))
                }
            }

            Button(
                onClick = { onMove(dir) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.move_here, if (dir == root) stringResource(R.string.internal_storage) else dir.name)) }
        }
    }
}

@Composable
private fun FolderRow(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, muted: Boolean = false, onClick: () -> Unit) {
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
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

