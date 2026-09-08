package com.snatik.storage.app.feature.data

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.app.ui.components.DataGrid
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.RowSheet
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DbTableScreen(route: Route.DbTable, onBack: () -> Unit, viewModel: DbTableViewModel = koinViewModel(parameters = { parametersOf(route) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(viewModel.table, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(route.path.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconToggleButton(checked = state.showSchema, onCheckedChange = { viewModel.toggleSchema() }) {
                        Icon(Icons.Default.Schema, contentDescription = stringResource(R.string.db_schema))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val rows = state.rows
            when {
                state.loading && rows == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.db_open_failed), state.error)
                rows == null -> Unit
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = state.showSchema) {
                        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.columns.forEach { c ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(c.name, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                                        Text(c.type, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (c.primaryKey) Tag(stringResource(R.string.db_pk), MaterialTheme.colorScheme.primary)
                                        if (c.notNull) Tag(stringResource(R.string.db_not_null))
                                        c.defaultValue?.let { Text(stringResource(R.string.db_default, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                                state.createSql?.let {
                                    Text(stringResource(R.string.db_create_sql), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                                    SelectionContainer { Text(it, style = MonoStyle) }
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val total = state.total
                        Text(
                            if (total != null) stringResource(R.string.db_rows_paged, total, state.page + 1)
                            else stringResource(R.string.query_results, rows.rows.size, state.page + 1),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::previousPage, enabled = state.page > 0) { Text(stringResource(R.string.page_prev)) }
                        TextButton(onClick = viewModel::nextPage, enabled = rows.truncated) { Text(stringResource(R.string.page_next)) }
                    }
                    if (rows.isEmpty) {
                        EmptyState(Icons.Default.Block, stringResource(R.string.query_no_rows), null)
                    } else {
                        DataGrid(
                            data = rows,
                            modifier = Modifier.fillMaxSize(),
                            onRowClick = { viewModel.selectRow(it) },
                            onHeaderClick = viewModel::sortBy,
                            sortedBy = state.orderBy,
                            sortDescending = state.descending,
                        )
                    }
                }
            }
        }
    }

    val selected = state.selectedRow
    val rows = state.rows
    if (selected != null && rows != null && selected in rows.rows.indices) {
        RowSheet(columns = rows.columns, row = rows.rows[selected], onDismiss = { viewModel.selectRow(null) })
    }
}
