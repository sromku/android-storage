package com.snatik.storage.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.fs.OperationRunner

/** Single line name prompt used for new folders, new files and renames. */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    selectBaseName: Boolean = false,
    validate: (String) -> String? = { null },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) {
        val end = if (selectBaseName) initial.substringBeforeLast('.', initial).length else initial.length
        mutableStateOf(TextFieldValue(initial, TextRange(0, end)))
    }
    val error = remember(value.text) { validate(value.text.trim()) }
    val focus = remember { FocusRequester() }
    val confirm = { if (error == null && value.text.isNotBlank()) onConfirm(value.text.trim()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = confirm, enabled = error == null && value.text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Progress of the running copy, move or delete with a cancel button. */
@Composable
fun OperationDialog(running: OperationRunner.Running, onCancel: () -> Unit) {
    val title = when (running.kind) {
        OperationRunner.Kind.COPY -> pluralStringResource(R.plurals.op_copying, running.itemCount, running.itemCount)
        OperationRunner.Kind.MOVE -> pluralStringResource(R.plurals.op_moving, running.itemCount, running.itemCount)
        OperationRunner.Kind.DELETE -> pluralStringResource(R.plurals.op_deleting, running.itemCount, running.itemCount)
    }
    val progress = running.progress
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column {
                val fraction = progress?.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                val detail = when {
                    progress == null -> stringResource(R.string.op_preparing)
                    progress.totalBytes > 0 -> "${progress.doneBytes.readableSize()} / ${progress.totalBytes.readableSize()}"
                    else -> "${progress.doneItems} / ${progress.totalItems}"
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                if (!progress?.currentName.isNullOrEmpty()) {
                    Text(
                        progress.currentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
