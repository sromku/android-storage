package com.snatik.storage.app.feature.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.FileKindIcon
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.fullDateTime
import com.snatik.storage.app.util.readableSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(details: EntryDetails, onComputeHash: () -> Unit, onDismiss: () -> Unit) {
    val entry = details.entry
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FileKindIcon(entry = entry, selected = false)
                Column {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(entry.mimeType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            DetailRow(stringResource(R.string.detail_path)) { SelectionContainer { Text(entry.path, style = MonoStyle) } }
            DetailRow(stringResource(R.string.detail_size)) {
                when {
                    !entry.isDirectory -> Text("${entry.size.readableSize()}  (${entry.size} B)")
                    details.computingSize -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.computing))
                    }
                    details.directorySize != null -> Text(details.directorySize.readableSize())
                    else -> Text(stringResource(R.string.unknown))
                }
            }
            if (entry.isDirectory && entry.childCount != null) {
                DetailRow(stringResource(R.string.detail_items)) { Text(entry.childCount.toString()) }
            }
            DetailRow(stringResource(R.string.detail_modified)) { Text(entry.lastModified.fullDateTime(context)) }
            DetailRow(stringResource(R.string.detail_permissions)) {
                Text(
                    buildString {
                        append(if (entry.canRead) "r" else "-")
                        append(if (entry.canWrite) "w" else "-")
                        if (entry.isHidden) append("  ·  ").append(stringResource(R.string.hidden))
                        if (entry.isSymlink) append("  ·  ").append(stringResource(R.string.symlink))
                    },
                    style = MonoStyle,
                )
            }
            if (!entry.isDirectory) {
                DetailRow("SHA-256") {
                    when {
                        details.sha256 != null -> SelectionContainer { Text(details.sha256, style = MonoStyle) }
                        details.computingHash -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.computing))
                        }
                        else -> TextButton(onClick = onComputeHash, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.compute))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp).width(96.dp),
        )
        Column(modifier = Modifier.weight(1f)) { value() }
    }
}
