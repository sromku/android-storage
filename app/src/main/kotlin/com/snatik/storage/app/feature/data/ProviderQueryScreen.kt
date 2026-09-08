package com.snatik.storage.app.feature.data

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.app.ui.components.DataGrid
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.RowSheet
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.data.Tabular
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderQueryScreen(route: Route.ProviderQuery, onBack: () -> Unit, viewModel: ProviderQueryViewModel = koinViewModel(parameters = { parametersOf(route) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
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
                    IconButton(onClick = { more = true }, enabled = state.result != null) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_csv)) }, onClick = { more = false; viewModel.export(asJson = false) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_json)) }, onClick = { more = false; viewModel.export(asJson = true) })
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(visible = state.editing) {
                QueryForm(form = state.form, onChange = viewModel::updateForm, onRun = { viewModel.run() })
            }
            val result = state.result
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null -> EmptyState(
                        icon = if (state.permissionDenied) Icons.Default.Lock else Icons.Default.Block,
                        title = stringResource(if (state.permissionDenied) R.string.query_permission_denied else R.string.query_failed),
                        body = listOfNotNull(if (state.permissionDenied) stringResource(R.string.query_permission_hint) else null, state.error).joinToString("\n\n"),
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
}

@Composable
private fun QueryForm(form: QueryForm, onChange: (QueryForm) -> Unit, onRun: () -> Unit) {
    Surface(tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = form.uri, onValueChange = { onChange(form.copy(uri = it)) }, label = { Text(stringResource(R.string.query_uri)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = form.projection, onValueChange = { onChange(form.copy(projection = it)) }, label = { Text(stringResource(R.string.query_projection)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = form.selection, onValueChange = { onChange(form.copy(selection = it)) }, label = { Text(stringResource(R.string.query_selection)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = form.args, onValueChange = { onChange(form.copy(args = it)) }, label = { Text(stringResource(R.string.query_args)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = form.sort, onValueChange = { onChange(form.copy(sort = it)) }, label = { Text(stringResource(R.string.query_sort)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.weight(2f))
                OutlinedTextField(value = form.limit, onValueChange = { onChange(form.copy(limit = it)) }, label = { Text(stringResource(R.string.query_limit)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.weight(1f))
            }
            Button(onClick = onRun, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.query_run), modifier = Modifier.padding(start = 6.dp))
            }
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
