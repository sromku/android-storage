package com.snatik.storage.app.feature.intents

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.AppSummary
import com.snatik.storage.core.apps.Component
import com.snatik.storage.core.intents.Extra
import com.snatik.storage.core.intents.ExtraType
import com.snatik.storage.core.intents.FlagOption
import com.snatik.storage.core.intents.IntentOption
import com.snatik.storage.core.intents.extraTypeInfo

/** A read-only field styled like an outlined text field that opens a picker when tapped. */
@Composable
fun PickerField(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val blank = value.isBlank()
            Text(
                if (blank) placeholder else value,
                style = if (mono) MonoStyle else MaterialTheme.typography.bodyLarge,
                color = if (blank) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f).padding(top = 2.dp),
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Compact sheet header: just the title and a search toggle plus an overflow menu that reveals the
 * custom-value field or clears the current selection — keeps the two big inputs off the top.
 */
@Composable
private fun SheetTopBar(
    title: String,
    searchable: Boolean,
    showSearch: Boolean,
    onToggleSearch: () -> Unit,
    hasCustom: Boolean,
    onEnterCustom: () -> Unit,
    clearable: Boolean,
    clearLabel: String,
    onClear: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (searchable) {
            IconButton(onClick = onToggleSearch) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search), tint = if (showSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hasCustom || clearable) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.builder_pick_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (hasCustom) DropdownMenuItem(text = { Text(stringResource(R.string.builder_pick_enter_custom)) }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }, onClick = { onEnterCustom(); menu = false })
                    if (clearable) DropdownMenuItem(text = { Text(clearLabel) }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }, onClick = { onClear(); menu = false })
                }
            }
        }
    }
}

/** A custom-value text field with a confirm button, for the "set any string" escape hatch. */
@Composable
private fun CustomEntry(initial: String, label: String, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        textStyle = MonoStyle,
        trailingIcon = {
            IconButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.builder_pick_custom_set))
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        label = { Text(stringResource(R.string.search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun OptionRow(option: IntentOption, selected: Boolean, count: Int?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(option.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else null)
                count?.let { Tag(pluralStringResource(R.plurals.builder_pick_apps, it, it), MaterialTheme.colorScheme.tertiary) }
            }
            Text(option.value, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Single-select picker over a grouped catalog. Tap the selected row again to clear it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(
    title: String,
    options: List<IntentOption>,
    current: String,
    customLabel: String,
    counts: Map<String, Int> = emptyMap(),
    searchable: Boolean = false,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else options.filter { it.title.contains(query, true) || it.value.contains(query, true) || it.description.contains(query, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetTopBar(
            title = title, searchable = searchable, showSearch = showSearch, onToggleSearch = { showSearch = !showSearch },
            hasCustom = true, onEnterCustom = { showCustom = true },
            clearable = current.isNotBlank(), clearLabel = stringResource(R.string.builder_pick_clear), onClear = { onPick("") },
        )
        if (showCustom) CustomEntry(current, customLabel, onConfirm = onPick)
        if (searchable && showSearch) SearchField(query) { query = it }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            filtered.groupBy { it.group }.forEach { (group, items) ->
                item(key = "g:$group") { GroupLabel(group) }
                items(items, key = { it.value }) { option ->
                    OptionRow(option, selected = option.value == current, count = counts[option.value], onClick = { onPick(if (option.value == current) "" else option.value) })
                }
            }
        }
    }
}

/** Multi-select picker (categories). Toggle a row to add/remove; menu clears all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiOptionSheet(
    title: String,
    options: List<IntentOption>,
    selected: Set<String>,
    customLabel: String,
    onToggle: (String) -> Unit,
    onAddCustom: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCustom by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetTopBar(
            title = title, searchable = false, showSearch = false, onToggleSearch = {},
            hasCustom = true, onEnterCustom = { showCustom = true },
            clearable = selected.isNotEmpty(), clearLabel = stringResource(R.string.builder_pick_clear_all), onClear = onClearAll,
        )
        if (showCustom) CustomEntry("", customLabel, onConfirm = onAddCustom)
        HorizontalDivider()
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            val extras = selected.filter { sel -> options.none { it.value == sel } }
            if (extras.isNotEmpty()) {
                item(key = "g:custom") { GroupLabel(stringResource(R.string.builder_pick_custom_group)) }
                items(extras, key = { "c:$it" }) { value ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(value) }.padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(value, style = MonoStyle, modifier = Modifier.weight(1f))
                        Checkbox(checked = true, onCheckedChange = { onToggle(value) })
                    }
                }
            }
            options.groupBy { it.group }.forEach { (group, groupItems) ->
                item(key = "g:$group") { GroupLabel(group) }
                items(groupItems, key = { it.value }) { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(option.value) }.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.title, style = MaterialTheme.typography.bodyLarge)
                            Text(option.value, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                            Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked = option.value in selected, onCheckedChange = { onToggle(option.value) })
                    }
                }
            }
        }
    }
}

/** Package picker over installed apps. Tap the current package again to clear it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    apps: List<AppSummary>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(true) }
    var showCustom by remember { mutableStateOf(false) }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetTopBar(
            title = stringResource(R.string.builder_pick_package_title), searchable = true, showSearch = showSearch, onToggleSearch = { showSearch = !showSearch },
            hasCustom = true, onEnterCustom = { showCustom = true },
            clearable = current.isNotBlank(), clearLabel = stringResource(R.string.builder_pick_clear), onClear = { onPick("") },
        )
        if (showCustom) CustomEntry(current, stringResource(R.string.builder_pick_custom_package), onConfirm = onPick)
        if (showSearch) SearchField(query) { query = it }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(if (app.packageName == current) "" else app.packageName) }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(app.packageName, size = 36.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                    if (app.packageName == current) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Component picker for the selected package. Tap the current class again to clear it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentPickerSheet(
    title: String,
    components: List<Component>,
    current: String,
    emptyHint: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    val filtered = remember(query, components) {
        if (query.isBlank()) components else components.filter { it.name.contains(query, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetTopBar(
            title = title, searchable = components.isNotEmpty(), showSearch = showSearch, onToggleSearch = { showSearch = !showSearch },
            hasCustom = true, onEnterCustom = { showCustom = true },
            clearable = current.isNotBlank(), clearLabel = stringResource(R.string.builder_pick_clear), onClear = { onPick("") },
        )
        if (showCustom) CustomEntry(current, stringResource(R.string.builder_pick_custom_class), onConfirm = onPick)
        if (showSearch) SearchField(query) { query = it }
        HorizontalDivider()
        if (components.isEmpty()) {
            Text(emptyHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered, key = { it.name }) { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(if (c.name == current) "" else c.name) }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(c.name.substringAfterLast('.'), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.name, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                    if (!c.exported) Tag(stringResource(R.string.builder_pick_not_exported))
                    if (c.name == current) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Flags picker: a described, switch-per-row list grouped by purpose; menu clears all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlagsSheet(
    flags: List<FlagOption>,
    value: Int,
    onToggle: (Int) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetTopBar(
            title = stringResource(R.string.builder_flags), searchable = false, showSearch = false, onToggleSearch = {},
            hasCustom = false, onEnterCustom = {},
            clearable = value != 0, clearLabel = stringResource(R.string.builder_pick_clear_all), onClear = onClearAll,
        )
        HorizontalDivider()
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            flags.groupBy { it.group }.forEach { (group, groupFlags) ->
                item(key = "g:$group") { GroupLabel(group) }
                items(groupFlags, key = { it.name }) { flag ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(flag.bit) }.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(flag.name, style = MaterialTheme.typography.bodyLarge)
                            Text(flag.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = value and flag.bit != 0, onCheckedChange = { onToggle(flag.bit) })
                    }
                }
            }
        }
    }
}

/** Editor for one extra — key, type (with description) and value. Added only when saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraEditorSheet(
    initial: Extra?,
    onSave: (Extra) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var key by remember { mutableStateOf(initial?.key.orEmpty()) }
    var type by remember { mutableStateOf(initial?.type ?: ExtraType.STRING) }
    var value by remember { mutableStateOf(initial?.value.orEmpty()) }
    val info = remember(type) { extraTypeInfo().first { it.type == type } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (initial == null) R.string.builder_extra_add_title else R.string.builder_extra_edit_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.builder_extra_key)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.builder_extra_type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                extraTypeInfo().forEach { t ->
                    FilterChip(selected = type == t.type, onClick = { type = t.type }, label = { Text(t.label, style = MaterialTheme.typography.labelMedium) })
                }
            }
            Text("${info.description}  e.g. ${info.example}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(stringResource(R.string.builder_extra_value)) }, singleLine = type != ExtraType.STRING_ARRAY, minLines = if (type == ExtraType.STRING_ARRAY) 3 else 1, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.delete)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onSave(Extra(key.trim(), type, value)) }, enabled = key.isNotBlank()) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 2.dp),
    )
}
