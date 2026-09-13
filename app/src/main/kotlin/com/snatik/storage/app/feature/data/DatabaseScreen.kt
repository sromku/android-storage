package com.snatik.storage.app.feature.data

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.DataGrid
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.RowSheet
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.data.TableInfo
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(path: String, onBack: () -> Unit, onOpenTable: (String) -> Unit, viewModel: DatabaseViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, resources) {
        viewModel.messages.collect { m ->
            snackbar.showSnackbar(
                when {
                    m == "saved" -> resources.getString(R.string.db_saved)
                    m == "ran" -> resources.getString(R.string.db_sql_done)
                    m.startsWith("vacuum:") -> resources.getString(R.string.db_vacuum_done, formatBytes(m.removePrefix("vacuum:").toLongOrNull() ?: 0))
                    m.startsWith("integrity:") -> {
                        val res = m.removePrefix("integrity:")
                        if (res == "ok") resources.getString(R.string.db_integrity_ok) else resources.getString(R.string.db_integrity_bad, res)
                    }
                    else -> m
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(path.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(path, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    if (state.isCopy && state.dirty) TextButton(onClick = viewModel::saveBack) { Text(stringResource(R.string.db_save_back)) }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.db_open_failed), state.error)
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    if (state.isCopy) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.db_copied_banner), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                    PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                        Tab(selected = state.tab == DbTab.TABLES, onClick = { viewModel.selectTab(DbTab.TABLES) }, text = { Text(stringResource(R.string.db_tables)) })
                        Tab(selected = state.tab == DbTab.SCHEMA, onClick = { viewModel.selectTab(DbTab.SCHEMA) }, text = { Text(stringResource(R.string.db_schema)) })
                        Tab(selected = state.tab == DbTab.SQL, onClick = { viewModel.selectTab(DbTab.SQL) }, text = { Text(stringResource(R.string.db_sql)) })
                    }
                    when (state.tab) {
                        DbTab.TABLES -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(state.tables, key = { it.name }) { table -> TableRow(table, onClick = { onOpenTable(table.name) }) }
                        }
                        DbTab.SCHEMA -> SchemaTab(state, viewModel)
                        DbTab.SQL -> SqlConsole(state, viewModel)
                    }
                }
            }
        }
    }

    val selected = state.selectedRow
    val result = state.sqlResult
    if (selected != null && result != null && selected in result.rows.indices) {
        RowSheet(columns = result.columns, row = result.rows[selected], onDismiss = { viewModel.selectRow(null) })
    }
}

@Composable
private fun SchemaTab(state: DatabaseUiState, viewModel: DatabaseViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The tab's namesake first: structure and relationships.
        item { Text(stringResource(R.string.db_schema_tables, state.schema.size), style = MaterialTheme.typography.titleSmall) }
        items(state.schema, key = { it.name }) { st ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (st.type == "view") Icons.Default.ViewList else Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(st.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.db_schema_meta, st.rowCount, st.columnCount), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.relationships.isNotEmpty()) {
            item { Text(stringResource(R.string.db_relationships, state.relationships.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
            items(state.relationships) { fk ->
                Text(
                    "${fk.fromTable}.${fk.fromColumn}  →  ${fk.toTable}.${fk.toColumn}",
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Maintenance: whole-file operations, each explained, kept below the schema they act on.
        item {
            Text(stringResource(R.string.db_maintenance), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp))
            Text(stringResource(R.string.db_maintenance_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column {
                    MaintenanceRow(
                        icon = Icons.Default.CleaningServices,
                        title = stringResource(R.string.db_vacuum),
                        description = stringResource(R.string.db_vacuum_desc),
                        enabled = !state.maintenanceRunning,
                        onRun = viewModel::vacuum,
                    )
                    HorizontalDivider()
                    MaintenanceRow(
                        icon = Icons.Default.HealthAndSafety,
                        title = stringResource(R.string.db_integrity),
                        description = stringResource(R.string.db_integrity_desc),
                        enabled = !state.maintenanceRunning,
                        onRun = viewModel::integrityCheck,
                    )
                    if (state.maintenanceRunning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun MaintenanceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, enabled: Boolean, onRun: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onRun, enabled = enabled) { Text(stringResource(R.string.db_run)) }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return "${(v * 10).toLong() / 10.0} ${units[i]}"
}

@Composable
private fun TableRow(table: TableInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (table.type == "view") Icons.Default.ViewList else Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(table.name, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (table.type == "view") Tag(stringResource(R.string.db_view))
        table.rowCount?.let { Text(stringResource(R.string.db_rows, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SqlConsole(state: DatabaseUiState, viewModel: DatabaseViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.sql,
            onValueChange = viewModel::setSql,
            placeholder = { Text(stringResource(R.string.db_sql_hint)) },
            textStyle = MonoStyle,
            minLines = 2,
            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            state.sqlError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f)) }
                ?: state.sqlResult?.let { Text(stringResource(R.string.db_rows, it.rows.size), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
                ?: Box(modifier = Modifier.weight(1f))
            Button(onClick = viewModel::runSql, enabled = !state.sqlRunning) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.query_run), modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (state.sqlRunning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.sqlResult?.let { result ->
            if (result.columns.isNotEmpty()) DataGrid(data = result, modifier = Modifier.fillMaxSize().padding(top = 8.dp), onRowClick = { viewModel.selectRow(it) })
        }
    }
}
