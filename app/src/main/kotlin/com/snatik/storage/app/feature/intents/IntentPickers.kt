package com.snatik.storage.app.feature.intents

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import androidx.compose.ui.res.stringResource
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.AppSummary
import com.snatik.storage.core.apps.Component
import com.snatik.storage.core.intents.ExtraTypeInfo
import com.snatik.storage.core.intents.FlagOption
import com.snatik.storage.core.intents.IntentOption

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

/** Header row shown at the top of every picker sheet. */
@Composable
private fun SheetHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
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
private fun OptionRow(option: IntentOption, selected: Boolean, count: Int?, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(option.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else null)
                count?.let { Tag(androidx.compose.ui.res.pluralStringResource(R.plurals.builder_pick_apps, it, it), MaterialTheme.colorScheme.tertiary) }
            }
            Text(option.value, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Single-select picker over a grouped catalog, with search and a custom-value field. */
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
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else options.filter { it.title.contains(query, true) || it.value.contains(query, true) || it.description.contains(query, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetHeader(title)
        CustomEntry(current, customLabel, onConfirm = onPick)
        if (searchable) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            val groups = filtered.groupBy { it.group }
            groups.forEach { (group, items) ->
                item(key = "g:$group") { GroupLabel(group) }
                items(items, key = { it.value }) { option ->
                    OptionRow(option, selected = option.value == current, count = counts[option.value], onClick = { onPick(option.value) })
                }
            }
        }
    }
}

/** Multi-select picker (categories), with custom add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiOptionSheet(
    title: String,
    options: List<IntentOption>,
    selected: Set<String>,
    customLabel: String,
    onToggle: (String) -> Unit,
    onAddCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetHeader(title)
        CustomEntry("", customLabel, onConfirm = onAddCustom)
        HorizontalDivider()
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Any selected custom values not present in the catalog.
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
            val groups = options.groupBy { it.group }
            groups.forEach { (group, groupItems) ->
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

/** Package picker over installed apps, searchable, with a custom-package field. */
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
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetHeader(stringResource(R.string.builder_pick_package_title))
        CustomEntry(current, stringResource(R.string.builder_pick_custom_package), onConfirm = onPick)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        )
        HorizontalDivider()
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(app.packageName) }.padding(horizontal = 24.dp, vertical = 8.dp),
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

/** Component picker for the selected package, filtered to the current send-as kind. */
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetHeader(title)
        CustomEntry(current, stringResource(R.string.builder_pick_custom_class), onConfirm = onPick)
        HorizontalDivider()
        if (components.isEmpty()) {
            Text(emptyHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(components, key = { it.name }) { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(c.name) }.padding(horizontal = 24.dp, vertical = 8.dp),
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

/** Flags picker: a described, switch-per-row list grouped by purpose. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlagsSheet(
    flags: List<FlagOption>,
    value: Int,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetHeader(stringResource(R.string.builder_flags))
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

/** Extra-type picker: each type with what it holds and an example. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraTypeSheet(
    types: List<ExtraTypeInfo>,
    current: com.snatik.storage.core.intents.ExtraType,
    onPick: (com.snatik.storage.core.intents.ExtraType) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetHeader(stringResource(R.string.builder_pick_type_title))
        HorizontalDivider()
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(types, key = { it.type.name }) { info ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(info.type) }.padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(info.label, style = MaterialTheme.typography.bodyLarge)
                        Text(info.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("e.g. ${info.example}", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (info.type == current) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
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
