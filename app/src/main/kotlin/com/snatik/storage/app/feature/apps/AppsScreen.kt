package com.snatik.storage.app.feature.apps

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.TopLevel
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.components.TopLevelBar
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.apps.AppSummary
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(onOpenApp: (String) -> Unit, onSwitchTab: (TopLevel) -> Unit, viewModel: AppsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    BackHandler(enabled = state.searchActive) { viewModel.setSearchActive(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            var sortMenu by remember { mutableStateOf(false) }
            val focus = remember { FocusRequester() }
            TopAppBar(
                title = {
                    if (state.searchActive) {
                        TextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text(stringResource(R.string.apps_search)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    } else {
                        Text(stringResource(R.string.apps_title))
                    }
                },
                actions = {
                    if (state.searchActive) {
                        IconButton(onClick = { viewModel.setSearchActive(false) }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) }
                    } else {
                        IconButton(onClick = { viewModel.setSearchActive(true) }) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) }
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.sort)) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            Text(stringResource(R.string.apps_filter_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            AppFilter.entries.forEach { filter ->
                                val flabel = when (filter) {
                                    AppFilter.ALL -> R.string.apps_filter_all
                                    AppFilter.USER -> R.string.apps_filter_user
                                    AppFilter.SYSTEM -> R.string.apps_filter_system
                                    AppFilter.DEBUGGABLE -> R.string.apps_filter_debuggable
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(flabel)) },
                                    leadingIcon = { RadioButton(selected = state.filter == filter, onClick = null) },
                                    onClick = { viewModel.setFilter(filter) },
                                )
                            }
                            HorizontalDivider()
                            Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            AppSort.entries.forEach { sort ->
                                val label = when (sort) {
                                    AppSort.NAME -> R.string.apps_sort_name
                                    AppSort.SIZE -> R.string.apps_sort_size
                                    AppSort.UPDATED -> R.string.apps_sort_updated
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    leadingIcon = { RadioButton(selected = state.sort == sort, onClick = null) },
                                    onClick = { viewModel.setSort(sort); sortMenu = false },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = { TopLevelBar(current = TopLevel.APPS, onSelect = onSwitchTab) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(R.string.apps_summary, state.totalCount, if (state.hasUsageAccess) state.totalBytes.readableSize() else stringResource(R.string.size_unknown)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading && state.apps.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.apps.isEmpty() -> EmptyState(Icons.Default.SearchOff, stringResource(R.string.apps_no_match), null)
                    else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (!state.hasUsageAccess) {
                            item(key = "usage") {
                                UsageAccessCard(onGrant = { context.startActivity(viewModel.usageAccessIntent()) })
                            }
                        }
                        items(state.apps, key = { it.packageName }) { app ->
                            AppRow(app, onClick = { onOpenApp(app.packageName) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageAccessCard(onGrant: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Insights, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.usage_access_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.usage_access_body), style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.usage_access_grant)) }
        }
    }
}

@Composable
private fun AppRow(app: AppSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(app.packageName, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (app.isDebuggable) Tag(stringResource(R.string.chip_debuggable), MaterialTheme.colorScheme.tertiary)
                if (!app.isEnabled) Tag(stringResource(R.string.chip_disabled), MaterialTheme.colorScheme.error)
            }
            Text(app.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        Text(
            app.storage?.totalBytes?.readableSize() ?: stringResource(R.string.size_unknown),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
