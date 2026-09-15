package com.snatik.storage.app.feature.data

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.DataGrid
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.RowSheet
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.data.Tabular
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderQueryScreen(route: Route.ProviderQuery, onBack: () -> Unit, onOpenFolder: (String) -> Unit = {}, onWatch: (uri: String, label: String) -> Unit = { _, _ -> }, viewModel: ProviderQueryViewModel = koinViewModel(parameters = { parametersOf(route) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, resources) {
        viewModel.messages.collect { m ->
            snackbar.showSnackbar(if (m.startsWith("saved:")) resources.getString(R.string.exported_to, m.removePrefix("saved:")) else m)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(viewModel.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.form.uri, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { viewModel.setEditing(!state.editing) }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.query_edit)) }
                    var more by remember { mutableStateOf(false) }
                    IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                    val saved = state.form.uri.trim() in state.savedUris
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (saved) R.string.uri_action_unsave else R.string.uri_action_save)) },
                            leadingIcon = { Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null) },
                            onClick = { more = false; viewModel.toggleSave() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.uri_details)) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = { more = false; viewModel.openDetails() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.prov_watch_changes)) },
                            leadingIcon = { Icon(Icons.Default.Sensors, contentDescription = null) },
                            onClick = { more = false; onWatch(state.form.uri.trim(), viewModel.title) },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_csv)) }, leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) }, enabled = state.result != null, onClick = { more = false; viewModel.export(asJson = false) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_json)) }, leadingIcon = { Icon(Icons.Default.DataObject, contentDescription = null) }, enabled = state.result != null, onClick = { more = false; viewModel.export(asJson = true) })
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(visible = state.editing) {
                QueryForm(form = state.form, columns = state.columns, onChange = viewModel::updateForm, onRun = { viewModel.run() })
            }
            val result = state.result
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null -> EmptyState(
                        icon = if (state.permissionDenied) Icons.Default.Lock else Icons.Default.Block,
                        title = stringResource(if (state.permissionDenied) R.string.query_permission_denied else R.string.query_failed),
                        body = listOfNotNull(
                            if (state.permissionDenied) stringResource(if (state.shellDenied) R.string.query_permission_shell_hint else R.string.query_permission_hint) else null,
                            if (state.error?.contains("no cursor", true) == true) stringResource(R.string.query_no_cursor_hint) else null,
                            state.error,
                        ).joinToString("\n\n"),
                    )
                    result == null -> Unit
                    result.isEmpty -> EmptyState(Icons.Default.TableRows, stringResource(R.string.query_no_rows), state.mime?.let { stringResource(R.string.query_mime, it) })
                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        ResultBar(result, state.page, state.mime, onPrev = viewModel::previousPage, onNext = viewModel::nextPage)
                        DataGrid(data = result, modifier = Modifier.fillMaxSize(), onRowClick = { viewModel.selectRow(it) })
                    }
                }
            }
        }
    }

    val selected = state.selectedRow
    val result = state.result
    if (selected != null && result != null && selected in result.rows.indices) {
        RowSheet(columns = result.columns, row = result.rows[selected], onDismiss = { viewModel.selectRow(null) })
    }

    state.details?.let { details ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::closeDetails, sheetState = sheetState) {
            UriDetailsSheet(details)
        }
    }

    state.exportResult?.let { export ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::dismissExport, sheetState = sheetState) {
            ExportSheet(
                export = export,
                onOpenFolder = { viewModel.dismissExport(); onOpenFolder(export.folder) },
                onShare = { com.snatik.storage.app.util.Intents.share(context, listOf(export.path)) },
            )
        }
    }
}

@Composable
private fun ExportSheet(export: ExportResult, onOpenFolder: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.export_done_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (export.isJson) Icons.Default.DataObject else Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(export.name, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatCell(export.rows.toString(), stringResource(R.string.export_rows))
            StatCell(android.text.format.Formatter.formatShortFileSize(context, export.sizeBytes), stringResource(R.string.export_size))
        }
        DetailRow(stringResource(R.string.export_location), export.folder)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.OutlinedButton(onClick = onOpenFolder, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.export_open_folder), modifier = Modifier.padding(start = 8.dp))
            }
            Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.share), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UriDetailsSheet(details: UriDetails) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.uri_details), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        // Full URI, with a copy affordance.
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(details.uri, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(details.uri)) }) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.uri_action_copy), modifier = Modifier.size(20.dp)) }
            }
        }

        DetailRow(stringResource(R.string.prov_detail_authority), details.authority.ifEmpty { "—" })
        details.mime?.let { DetailRow(stringResource(R.string.uri_detail_mime), it) }
        if (details.pathSegments.isNotEmpty()) DetailRow(stringResource(R.string.uri_detail_path), details.pathSegments.joinToString("  /  "))

        val p = details.provider
        if (p == null) {
            Text(stringResource(R.string.uri_detail_no_provider), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        } else {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(p.packageName, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.appLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
                Tag(stringResource(if (p.isSystem) R.string.prov_tag_system else R.string.prov_source_apps), MaterialTheme.colorScheme.secondary)
                if (p.exported) Tag(stringResource(R.string.chip_exported), MaterialTheme.colorScheme.primary)
            }
            DetailRow(stringResource(R.string.prov_detail_class), p.className)
            DetailRow(stringResource(R.string.prov_detail_read), p.readPermission ?: stringResource(R.string.prov_detail_none))
            DetailRow(stringResource(R.string.prov_detail_write), p.writePermission ?: stringResource(R.string.prov_detail_none))
            DetailRow(stringResource(R.string.prov_detail_grant), stringResource(if (p.grantUriPermissions) R.string.prov_detail_yes else R.string.prov_detail_no))
            if (p.pathPermissions.isNotEmpty()) DetailRow(stringResource(R.string.prov_detail_paths), p.pathPermissions.joinToString("\n"))
            DetailRow(stringResource(R.string.uri_detail_direct), stringResource(if (p.queryableNow) R.string.uri_detail_direct_yes else R.string.uri_detail_direct_no))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface))
    }
}

@Composable
private fun QueryForm(form: QueryForm, columns: List<String>, onChange: (QueryForm) -> Unit, onRun: () -> Unit) {
    Surface(tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = form.uri, onValueChange = { onChange(form.copy(uri = it)) }, label = { Text(stringResource(R.string.query_uri)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())

            if (columns.isNotEmpty()) {
                BuilderLabel(stringResource(R.string.query_columns))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(form.projection.isEmpty(), onClick = { onChange(form.copy(projection = emptySet())) }, label = { Text(stringResource(R.string.query_all_columns)) })
                    columns.forEach { col ->
                        FilterChip(col in form.projection, onClick = {
                            onChange(form.copy(projection = if (col in form.projection) form.projection - col else form.projection + col))
                        }, label = { Text(col) })
                    }
                }
            }

            BuilderLabel(stringResource(R.string.query_filter))
            form.conditions.forEachIndexed { i, cond ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Picker(cond.column, stringResource(R.string.query_column), columns, Modifier.weight(1.2f)) { onChange(form.copy(conditions = form.conditions.replaceAt(i, cond.copy(column = it)))) }
                    Picker(cond.op.sql, "=", QueryOp.entries.map { it.sql }, Modifier.weight(1f)) { sql -> onChange(form.copy(conditions = form.conditions.replaceAt(i, cond.copy(op = QueryOp.entries.first { o -> o.sql == sql })))) }
                    IconButton(onClick = { onChange(form.copy(conditions = form.conditions.filterIndexed { j, _ -> j != i })) }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) }
                }
                if (cond.op.hasValue) {
                    OutlinedTextField(value = cond.value, onValueChange = { onChange(form.copy(conditions = form.conditions.replaceAt(i, cond.copy(value = it)))) }, label = { Text(stringResource(R.string.query_value)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
                }
            }
            TextButton(onClick = { onChange(form.copy(conditions = form.conditions + Condition())) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.query_add_condition), modifier = Modifier.padding(start = 4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Picker(form.sortColumn, stringResource(R.string.query_sort_by), columns, Modifier.weight(1f)) { onChange(form.copy(sortColumn = it)) }
                if (form.sortColumn.isNotEmpty()) {
                    IconButton(onClick = { onChange(form.copy(sortDesc = !form.sortDesc)) }) {
                        Icon(if (form.sortDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, contentDescription = null)
                    }
                }
                OutlinedTextField(value = form.limit, onValueChange = { onChange(form.copy(limit = it.filter { c -> c.isDigit() })) }, label = { Text(stringResource(R.string.query_limit)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.width(96.dp))
            }

            Button(onClick = onRun, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.query_run), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

@Composable
private fun BuilderLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Picker(value: String, placeholder: String, options: List<String>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        androidx.compose.material3.OutlinedButton(onClick = { open = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(value.ifEmpty { placeholder }, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt -> DropdownMenuItem(text = { Text(opt, style = MonoStyle) }, onClick = { onSelect(opt); open = false }) }
        }
    }
}

@Composable
private fun ResultBar(result: Tabular, page: Int, mime: String?, onPrev: () -> Unit, onNext: () -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.query_results, result.rows.size, page + 1), style = MaterialTheme.typography.bodySmall)
                mime?.let { Text(stringResource(R.string.query_mime, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            TextButton(onClick = onPrev, enabled = page > 0) { Text(stringResource(R.string.page_prev)) }
            TextButton(onClick = onNext, enabled = result.truncated) { Text(stringResource(R.string.page_next)) }
        }
        if (result.source == Tabular.Source.SHELL) {
            Text(stringResource(R.string.query_via_shell), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}
