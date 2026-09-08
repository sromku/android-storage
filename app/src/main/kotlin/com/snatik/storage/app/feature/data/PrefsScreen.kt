package com.snatik.storage.app.feature.data

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.data.PrefEntry
import com.snatik.storage.core.data.PrefType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrefsScreen(path: String, onBack: () -> Unit, viewModel: PrefsViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, resources) {
        viewModel.messages.collect { m -> snackbar.showSnackbar(if (m == "saved") resources.getString(R.string.prefs_saved) else m) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(path.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (state.dirty) stringResource(R.string.prefs_unsaved) else stringResource(R.string.prefs_title),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.dirty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { TextButton(onClick = viewModel::save, enabled = state.dirty) { Text(stringResource(R.string.prefs_save)) } },
            )
        },
        floatingActionButton = {
            if (state.error == null) FloatingActionButton(onClick = viewModel::startAdding) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.prefs_add)) }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.prefs_parse_failed), state.error)
                state.entries.isEmpty() -> EmptyState(Icons.Default.DataObject, stringResource(R.string.prefs_empty), null)
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(state.entries, key = { it.key }) { entry -> PrefRow(entry, onClick = { viewModel.edit(entry) }) }
                }
            }
        }
    }

    state.editing?.let { entry ->
        PrefDialog(initial = entry, isNew = false, validate = viewModel::validate, onSave = viewModel::put, onDelete = { viewModel.remove(entry.key) }, onDismiss = viewModel::cancelDialog)
    }
    if (state.adding) {
        PrefDialog(initial = PrefEntry("", PrefType.STRING, ""), isNew = true, validate = viewModel::validate, onSave = viewModel::put, onDelete = null, onDismiss = viewModel::cancelDialog)
    }
}

@Composable
private fun PrefRow(entry: PrefEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.key, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.type == PrefType.SET) entry.setValues.joinToString(", ") else entry.value,
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Tag(entry.type.name.lowercase())
    }
}

@Composable
private fun PrefDialog(
    initial: PrefEntry,
    isNew: Boolean,
    validate: (PrefType, String) -> String?,
    onSave: (PrefEntry) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf(initial.key) }
    var type by remember { mutableStateOf(initial.type) }
    var value by remember { mutableStateOf(if (initial.type == PrefType.SET) initial.setValues.joinToString("\n") else initial.value) }
    val error = if (type == PrefType.SET) null else validate(type, value)
    val canSave = key.isNotBlank() && error == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) stringResource(R.string.prefs_add) else stringResource(R.string.prefs_edit, initial.key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isNew) {
                    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.prefs_key)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { menu = true }) { Text(stringResource(R.string.prefs_type) + ": " + type.name.lowercase()) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            PrefType.entries.forEach { t -> DropdownMenuItem(text = { Text(t.name.lowercase()) }, onClick = { type = t; menu = false; if (t == PrefType.BOOLEAN) value = "false" }) }
                        }
                    }
                }
                when (type) {
                    PrefType.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = value == "true", onCheckedChange = { value = it.toString() })
                        Text(value)
                    }
                    PrefType.SET -> OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(stringResource(R.string.prefs_set_hint)) }, minLines = 3, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
                    else -> OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(stringResource(R.string.prefs_value)) },
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        textStyle = MonoStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val entry = if (type == PrefType.SET) PrefEntry(key.trim(), type, "", value.lines().filter { it.isNotEmpty() }) else PrefEntry(key.trim(), type, value)
                    onSave(entry)
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
