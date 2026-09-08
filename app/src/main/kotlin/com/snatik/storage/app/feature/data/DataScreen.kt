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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.data_title)) }, scrollBehavior = scrollBehavior) },
        bottomBar = { TopLevelBar(current = TopLevel.DATA, onSelect = onSwitchTab) },
    ) { padding ->
        val visible = state.visible
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "shortcuts") {
                Text(
                    stringResource(R.string.section_shortcuts),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
                )
                state.shortcuts.groupBy { it.group }.forEach { (group, items) ->
                    Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
                    FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { s -> AssistChip(onClick = { onOpenProvider(s.title, s.uri) }, label = { Text(s.title) }) }
                    }
                }
            }
            item(key = "search") {
                Text(
                    stringResource(R.string.section_all_providers) + " · " + stringResource(R.string.providers_count, visible.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.providers_search)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (!state.loading && visible.isEmpty()) {
                item { Text(stringResource(R.string.no_providers), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(visible, key = { it.packageName + "/" + it.className + "/" + it.authority }) { provider ->
                ProviderRow(provider, onClick = { onOpenProvider(provider.authority, provider.uri) })
            }
        }
    }
}

@Composable
private fun ProviderRow(provider: ProviderEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(provider.packageName, size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(provider.authority, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f, fill = false))
                if (provider.exported) Tag(stringResource(R.string.chip_exported), MaterialTheme.colorScheme.primary)
            }
            val detail = buildList {
                add(provider.appLabel)
                provider.readPermission?.let { add("r: ${it.substringAfterLast('.')}") }
                provider.writePermission?.let { add("w: ${it.substringAfterLast('.')}") }
            }.joinToString("  ·  ")
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
