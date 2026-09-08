package com.snatik.storage.app.feature.intents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.intents.COMMON_ACTIONS
import com.snatik.storage.core.intents.Extra
import com.snatik.storage.core.intents.ExtraType
import com.snatik.storage.core.intents.KNOWN_FLAGS
import com.snatik.storage.core.intents.ResolvedTarget
import com.snatik.storage.core.intents.SendAs
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentBuilderScreen(route: Route.IntentBuilder, onBack: () -> Unit, viewModel: IntentBuilderViewModel = koinViewModel(parameters = { parametersOf(route) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val chooserTitle = stringResource(R.string.builder_chooser)

    LaunchedEffect(viewModel, resources) {
        viewModel.messages.collect { m ->
            snackbar.showSnackbar(
                when (m) {
                    "sent:activity" -> resources.getString(R.string.builder_sent_activity)
                    "sent:broadcast" -> resources.getString(R.string.builder_sent_broadcast)
                    "sent:service" -> resources.getString(R.string.builder_sent_service)
                    "saved" -> resources.getString(R.string.builder_saved)
                    else -> m
                },
            )
        }
    }

    val form = state.form
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.presetName.ifBlank { stringResource(R.string.builder_title) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { viewModel.setSaving(true) }) { Icon(Icons.Default.Bookmark, contentDescription = stringResource(R.string.builder_save)) }
                    IconButton(onClick = viewModel::send) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.builder_send)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SendAs.entries.forEachIndexed { i, kind ->
                        SegmentedButton(selected = form.sendAs == kind, onClick = { viewModel.update(form.copy(sendAs = kind)) }, shape = SegmentedButtonDefaults.itemShape(i, SendAs.entries.size)) {
                            Text(stringResource(when (kind) { SendAs.ACTIVITY -> R.string.builder_activity; SendAs.BROADCAST -> R.string.builder_broadcast; SendAs.SERVICE -> R.string.builder_service }))
                        }
                    }
                }
            }
            item { ActionField(form.action, onChange = { viewModel.update(form.copy(action = it)) }) }
            item { OutlinedTextField(value = form.data, onValueChange = { viewModel.update(form.copy(data = it)) }, label = { Text(stringResource(R.string.builder_data)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = form.type, onValueChange = { viewModel.update(form.copy(type = it)) }, label = { Text(stringResource(R.string.builder_type)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = form.categories, onValueChange = { viewModel.update(form.copy(categories = it)) }, label = { Text(stringResource(R.string.builder_categories)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth()) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = form.packageName, onValueChange = { viewModel.update(form.copy(packageName = it)) }, label = { Text(stringResource(R.string.builder_package)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = form.className, onValueChange = { viewModel.update(form.copy(className = it)) }, label = { Text(stringResource(R.string.builder_class)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.weight(1f))
                }
            }
            item {
                Text(stringResource(R.string.builder_flags), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KNOWN_FLAGS.forEach { (name, bit) ->
                        FilterChip(selected = form.flags and bit != 0, onClick = { viewModel.toggleFlag(bit) }, label = { Text(name, style = MaterialTheme.typography.labelMedium) })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.builder_extras), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::addExtra) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(stringResource(R.string.builder_add_extra))
                    }
                }
            }
            itemsIndexed(form.extras) { index, extra -> ExtraRow(extra, onChange = { viewModel.updateExtra(index, it) }, onRemove = { viewModel.removeExtra(index) }) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = viewModel::resolve) { Text(stringResource(R.string.builder_resolve)) }
                    if (form.sendAs == SendAs.ACTIVITY) OutlinedButton(onClick = { context.startActivity(viewModel.chooserIntent(chooserTitle)) }) { Text(stringResource(R.string.builder_chooser)) }
                    Button(onClick = viewModel::send) { Text(stringResource(R.string.builder_send)) }
                }
            }
            state.targets?.let { targets ->
                if (targets.isEmpty()) {
                    item { Text(stringResource(R.string.builder_no_targets), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    itemsIndexed(targets, key = { _, t -> t.packageName + "/" + t.className }) { _, target -> TargetRow(target, onClick = { viewModel.sendTo(target) }) }
                }
            }
        }
    }

    if (state.saving) {
        AlertDialog(
            onDismissRequest = { viewModel.setSaving(false) },
            title = { Text(stringResource(R.string.builder_save)) },
            text = { OutlinedTextField(value = state.presetName, onValueChange = viewModel::setPresetName, label = { Text(stringResource(R.string.builder_preset_name)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = viewModel::savePreset, enabled = state.presetName.isNotBlank()) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { viewModel.setSaving(false) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionField(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(value) { COMMON_ACTIONS.filter { value.isBlank() || it.contains(value, ignoreCase = true) } }
    ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text(stringResource(R.string.builder_action)) },
            singleLine = true,
            textStyle = MonoStyle,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && suggestions.isNotEmpty(), onDismissRequest = { expanded = false }) {
            suggestions.take(12).forEach { s ->
                DropdownMenuItem(text = { Text(s, style = MonoStyle) }, onClick = { onChange(s); expanded = false })
            }
        }
    }
}

@Composable
private fun ExtraRow(extra: Extra, onChange: (Extra) -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(value = extra.key, onValueChange = { onChange(extra.copy(key = it)) }, label = { Text(stringResource(R.string.builder_extra_key)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.weight(1.1f))
        var menu by remember { mutableStateOf(false) }
        Column(modifier = Modifier.width(88.dp)) {
            AssistChip(onClick = { menu = true }, label = { Text(extra.type.name.lowercase(), maxLines = 1, style = MaterialTheme.typography.labelSmall) })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                ExtraType.entries.forEach { t -> DropdownMenuItem(text = { Text(t.name.lowercase()) }, onClick = { onChange(extra.copy(type = t)); menu = false }) }
            }
        }
        OutlinedTextField(value = extra.value, onValueChange = { onChange(extra.copy(value = it)) }, label = { Text(stringResource(R.string.builder_extra_value)) }, singleLine = extra.type != ExtraType.STRING_ARRAY, textStyle = MonoStyle, modifier = Modifier.weight(1.4f))
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete)) }
    }
}

@Composable
private fun TargetRow(target: ResolvedTarget, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(target.packageName, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(target.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (target.isDefault) Tag(stringResource(R.string.builder_default), MaterialTheme.colorScheme.primary)
                if (!target.exported) Tag(stringResource(R.string.chip_exported).let { "not $it" })
            }
            Text(target.className, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        Text(stringResource(R.string.builder_priority, target.priority), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
