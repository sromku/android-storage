package com.snatik.storage.app.feature.data

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.TopLevel
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.components.TopLevelBar
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.data.ProviderEntry
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(onOpenProvider: (title: String, uri: String) -> Unit, onSwitchTab: (TopLevel) -> Unit, viewModel: DataViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var selected by remember { mutableStateOf<ProviderEntry?>(null) }
    var filtersOpen by remember { mutableStateOf(false) }
    var shortcutsExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.data_title)) }, scrollBehavior = scrollBehavior) },
        bottomBar = { TopLevelBar(current = TopLevel.DATA, onSelect = onSwitchTab) },
    ) { padding ->
        val visible = state.visible
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "shortcuts") {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { shortcutsExpanded = !shortcutsExpanded }.padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.section_shortcuts), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Icon(if (shortcutsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (shortcutsExpanded) {
                    state.shortcuts.groupBy { it.group }.forEach { (group, items) ->
                        Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
                        FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items.forEach { s -> AssistChip(onClick = { onOpenProvider(s.title, s.uri) }, label = { Text(s.title) }) }
                        }
                    }
                }
            }
            item(key = "controls") {
                Text(
                    stringResource(R.string.section_all_providers) + " · " + stringResource(R.string.providers_count, visible.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(R.string.providers_search)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) } },
                        modifier = Modifier.weight(1f),
                    )
                    BadgedBox(badge = { if (state.activeFilterCount > 0) Badge { Text("${state.activeFilterCount}") } }) {
                        FilledTonalIconButton(onClick = { filtersOpen = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.prov_filters_button))
                        }
                    }
                }
            }
            if (!state.loading && visible.isEmpty()) {
                item { Text(stringResource(R.string.no_providers), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (state.groupByApp) {
                state.grouped.forEach { (app, list) ->
                    item(key = "group/$app") {
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(list.first().packageName, size = 20.dp)
                            Text(app, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Text("${list.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(list, key = { it.packageName + "/" + it.className + "/" + it.authority }) { provider ->
                        ProviderRow(provider, grouped = true, onClick = { selected = provider })
                    }
                }
            } else {
                items(visible, key = { it.packageName + "/" + it.className + "/" + it.authority }) { provider ->
                    ProviderRow(provider, grouped = false, onClick = { selected = provider })
                }
            }
        }
    }

    selected?.let { provider ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = sheetState) {
            ProviderDetail(provider, onQuery = { onOpenProvider(provider.authority, provider.uri); selected = null })
        }
    }

    if (filtersOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { filtersOpen = false }, sheetState = sheetState) {
            FiltersSheet(state, viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSheet(state: DataUiState, viewModel: DataViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.prov_filters_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (state.activeFilterCount > 0) TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.prov_clear)) }
        }
        Text(stringResource(R.string.prov_source_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val sources = listOf(
                ProviderSource.ALL to stringResource(R.string.prov_source_all),
                ProviderSource.SYSTEM to stringResource(R.string.prov_source_system),
                ProviderSource.APPS to stringResource(R.string.prov_source_apps),
            )
            sources.forEachIndexed { i, (src, label) ->
                SegmentedButton(
                    selected = state.source == src,
                    onClick = { viewModel.setSource(src) },
                    shape = SegmentedButtonDefaults.itemShape(i, sources.size),
                ) { Text(label, maxLines = 1) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.prov_filter_group), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.groupByApp, onCheckedChange = { viewModel.toggleGroupByApp() })
        }
        Text(stringResource(R.string.prov_show_only), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.onlyExported, onClick = viewModel::toggleExported, label = { Text(stringResource(R.string.prov_filter_exported)) })
            FilterChip(state.onlyPermission, onClick = viewModel::togglePermission, label = { Text(stringResource(R.string.prov_filter_permission)) })
            FilterChip(state.onlyGrantsUri, onClick = viewModel::toggleGrantsUri, label = { Text(stringResource(R.string.prov_filter_grant)) })
            FilterChip(state.onlyQueryable, onClick = viewModel::toggleQueryable, label = { Text(stringResource(R.string.prov_filter_queryable)) })
        }
    }
}

@Composable
private fun ProviderRow(provider: ProviderEntry, grouped: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = if (grouped) 24.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!grouped) AppIcon(provider.packageName, size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(provider.authority, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f, fill = false))
                if (provider.exported) Tag(stringResource(R.string.chip_exported), MaterialTheme.colorScheme.primary)
            }
            val detail = buildList {
                if (!grouped) add(provider.appLabel)
                provider.readPermission?.let { add("r: ${it.substringAfterLast('.')}") }
                provider.writePermission?.let { add("w: ${it.substringAfterLast('.')}") }
            }.joinToString("  ·  ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProviderDetail(provider: ProviderEntry, onQuery: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(provider.packageName, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(provider.appLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(provider.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Tag(stringResource(if (provider.isSystem) R.string.prov_tag_system else R.string.prov_source_apps), MaterialTheme.colorScheme.secondary)
            if (provider.exported) Tag(stringResource(R.string.chip_exported), MaterialTheme.colorScheme.primary)
        }
        DetailLine(stringResource(R.string.prov_detail_authority), provider.authority)
        DetailLine(stringResource(R.string.prov_detail_class), provider.className)
        DetailLine(stringResource(R.string.prov_detail_read), provider.readPermission ?: stringResource(R.string.prov_detail_none))
        DetailLine(stringResource(R.string.prov_detail_write), provider.writePermission ?: stringResource(R.string.prov_detail_none))
        DetailLine(stringResource(R.string.prov_detail_grant), stringResource(if (provider.grantUriPermissions) R.string.prov_detail_yes else R.string.prov_detail_no))
        if (provider.pathPermissions.isNotEmpty()) DetailLine(stringResource(R.string.prov_detail_paths), provider.pathPermissions.joinToString("\n"))
        androidx.compose.material3.Button(onClick = onQuery, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.prov_query), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface))
    }
}
