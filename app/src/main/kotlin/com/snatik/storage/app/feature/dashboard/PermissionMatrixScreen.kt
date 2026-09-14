package com.snatik.storage.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.PermApp
import com.snatik.storage.core.apps.PermCategory
import com.snatik.storage.core.apps.PermInfo
import com.snatik.storage.core.apps.PermissionData
import com.snatik.storage.core.apps.PermissionMatrixRepository
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class PermView { OVERVIEW, MATRIX, PERMS, APPS, GROUPS }
enum class PermFilter { DANGEROUS, SPECIAL, SIGNATURE, NORMAL, CUSTOM }

data class PermUiState(
    val loading: Boolean = true,
    val data: PermissionData? = null,
    val view: PermView = PermView.OVERVIEW,
    val query: String = "",
    val includeSystem: Boolean = false,
    val filters: Set<PermFilter> = setOf(PermFilter.DANGEROUS, PermFilter.SPECIAL),
    val privileged: Boolean = false,   // Shizuku or root available -> can grant/revoke
    val busy: Set<String> = emptySet(), // "pkg|perm" currently being toggled
)

/** Outcome of a grant/revoke, surfaced so the UI can build a localized message. */
sealed interface GrantResult {
    data class Ok(val granted: Boolean) : GrantResult
    data class Failed(val error: String?) : GrantResult
    object NeedsShell : GrantResult
}

class PermissionMatrixViewModel(
    private val repo: PermissionMatrixRepository,
    private val privilege: PrivilegeManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(PermUiState())
    val ui: StateFlow<PermUiState> = _ui.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            privilege.state.collect { st -> _ui.update { it.copy(privileged = st.isPrivileged) } }
        }
    }

    private fun reload() {
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            val data = repo.load(_ui.value.includeSystem)
            _ui.update { it.copy(loading = false, data = data) }
        }
    }

    fun setView(v: PermView) = _ui.update { it.copy(view = v) }
    fun setQuery(q: String) = _ui.update { it.copy(query = q) }
    fun toggleSystem() { _ui.update { it.copy(includeSystem = !it.includeSystem) }; reload() }
    fun toggleFilter(f: PermFilter) = _ui.update {
        it.copy(filters = if (f in it.filters) it.filters - f else it.filters + f)
    }

    /** Grant or revoke a runtime permission for one app via the privileged shell. Updates state in place. */
    fun setGranted(pkg: String, perm: String, grant: Boolean, onResult: (GrantResult) -> Unit) {
        val exec = privilege.executor.value
        if (exec == null) { onResult(GrantResult.NeedsShell); return }
        val key = "$pkg|$perm"
        if (key in _ui.value.busy) return
        _ui.update { it.copy(busy = it.busy + key) }
        viewModelScope.launch {
            val verb = if (grant) "grant" else "revoke"
            val res = runCatching { exec.run("pm $verb $pkg $perm", timeoutMs = 15_000) }.getOrNull()
            val ok = res?.ok == true
            if (ok) {
                _ui.update { st ->
                    val data = st.data ?: return@update st.copy(busy = st.busy - key)
                    val apps = data.apps.map { app ->
                        if (app.packageName != pkg) app
                        else app.copy(granted = if (grant) app.granted + perm else app.granted - perm)
                    }
                    val perms = data.perms.mapValues { (n, info) ->
                        if (n != perm) info else info.copy(grantedBy = (info.grantedBy + if (grant) 1 else -1).coerceAtLeast(0))
                    }
                    st.copy(data = data.copy(apps = apps, perms = perms), busy = st.busy - key)
                }
                onResult(GrantResult.Ok(grant))
            } else {
                _ui.update { it.copy(busy = it.busy - key) }
                onResult(GrantResult.Failed(res?.err?.ifBlank { res.out }?.trim()?.take(140)))
            }
        }
    }
}

private fun PermFilter.matches(p: PermInfo): Boolean = when (this) {
    PermFilter.DANGEROUS -> p.category == PermCategory.DANGEROUS
    PermFilter.SPECIAL -> p.category == PermCategory.SPECIAL
    PermFilter.SIGNATURE -> p.category == PermCategory.SIGNATURE
    PermFilter.NORMAL -> p.category == PermCategory.NORMAL
    PermFilter.CUSTOM -> p.custom
}

private fun Set<PermFilter>.match(p: PermInfo): Boolean = isEmpty() || any { it.matches(p) }

@Composable
private fun categoryColor(category: PermCategory): Color = when (category) {
    PermCategory.DANGEROUS -> MaterialTheme.colorScheme.error
    PermCategory.SPECIAL -> MaterialTheme.colorScheme.tertiary
    PermCategory.SIGNATURE -> Color(0xFF7C5CFF)
    PermCategory.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
    PermCategory.UNKNOWN -> MaterialTheme.colorScheme.outline
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionMatrixScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit, viewModel: PermissionMatrixViewModel = koinViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Saveable so the open sheet is restored when returning from an app screen.
    var detailPerm by rememberSaveable { mutableStateOf<String?>(null) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var cellApp by rememberSaveable { mutableStateOf<String?>(null) }
    var cellPerm by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Localized strings for grant/revoke feedback, resolved here so the VM stays free of resources.
    val fmtGranted = stringResource(R.string.perm_granted_toast)
    val fmtRevoked = stringResource(R.string.perm_revoked_toast)
    val fmtFailed = stringResource(R.string.perm_grant_failed)
    val needsShell = stringResource(R.string.perm_grant_needs_shell)
    fun onGrantResult(permShort: String, appLabel: String, result: GrantResult) {
        val msg = when (result) {
            is GrantResult.Ok -> (if (result.granted) fmtGranted else fmtRevoked).format(permShort, appLabel)
            is GrantResult.Failed -> fmtFailed.format(result.error ?: "")
            GrantResult.NeedsShell -> needsShell
        }
        scope.launch { snackbar.showSnackbar(msg) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.matrix_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.perm_help))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // View switcher
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewChip(stringResource(R.string.perm_view_overview), ui.view == PermView.OVERVIEW) { viewModel.setView(PermView.OVERVIEW) }
                ViewChip(stringResource(R.string.perm_view_matrix), ui.view == PermView.MATRIX) { viewModel.setView(PermView.MATRIX) }
                ViewChip(stringResource(R.string.perm_view_perms), ui.view == PermView.PERMS) { viewModel.setView(PermView.PERMS) }
                ViewChip(stringResource(R.string.perm_view_apps), ui.view == PermView.APPS) { viewModel.setView(PermView.APPS) }
                ViewChip(stringResource(R.string.perm_view_groups), ui.view == PermView.GROUPS) { viewModel.setView(PermView.GROUPS) }
            }
            HorizontalDivider()

            val data = ui.data
            if (ui.loading || data == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else when (ui.view) {
                PermView.OVERVIEW -> OverviewView(data, ui, viewModel, onOpenPerm = { detailPerm = it }, onOpenApp = onOpenApp)
                PermView.MATRIX -> MatrixView(data, ui, viewModel, onOpenApp = onOpenApp, onOpenPerm = { detailPerm = it }, onOpenCell = { pkg, perm -> cellApp = pkg; cellPerm = perm })
                PermView.PERMS -> PermsView(data, ui, viewModel, onOpenPerm = { detailPerm = it })
                PermView.APPS -> AppsView(data, ui, viewModel, onOpenApp = onOpenApp, onOpenPerm = { detailPerm = it })
                PermView.GROUPS -> GroupsView(data, onOpenApp = onOpenApp)
            }
        }
    }

    detailPerm?.let { name ->
        PermDetailSheet(
            name = name,
            ui = ui,
            onOpenApp = { onOpenApp(it) }, // keep the sheet so it's here on the way back
            onToggle = { app, perm, grant -> viewModel.setGranted(app.packageName, perm.name, grant) { onGrantResult(perm.short, app.label, it) } },
            onDismiss = { detailPerm = null },
        )
    }

    val cp = cellPerm
    val ca = cellApp
    if (cp != null && ca != null) {
        MatrixCellSheet(
            pkg = ca,
            permName = cp,
            ui = ui,
            onOpenApp = { onOpenApp(it) }, // keep the sheet for the way back
            onOpenPerm = { cellApp = null; cellPerm = null; detailPerm = it },
            onToggle = { app, perm, grant -> viewModel.setGranted(app.packageName, perm.name, grant) { onGrantResult(perm.short, app.label, it) } },
            onDismiss = { cellApp = null; cellPerm = null },
        )
    }

    if (showHelp) HelpSheet(ui, viewModel, onDismiss = { showHelp = false })
}

@Composable
private fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/* ---------- shared bits ---------- */

@Composable
private fun PermChip(perm: PermInfo, granted: Boolean? = null, onClick: (() -> Unit)? = null) {
    val color = categoryColor(perm.category)
    val base = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (granted == true) Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(perm.short, style = MonoStyle.copy(fontSize = 11.sp, color = color), maxLines = 1)
        if (perm.custom) Box(modifier = Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
    }
}

@Composable
private fun CountBadge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.background(color.copy(alpha = 0.13f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun SectionHeader(text: String, sub: String? = null) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SearchField(ui: PermUiState, viewModel: PermissionMatrixViewModel) {
    OutlinedTextField(
        value = ui.query,
        onValueChange = viewModel::setQuery,
        placeholder = { Text(stringResource(R.string.perm_search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun FilterRow(ui: PermUiState, viewModel: PermissionMatrixViewModel) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        catFilter(ui, viewModel, PermFilter.DANGEROUS, R.string.perm_cat_dangerous)
        catFilter(ui, viewModel, PermFilter.SPECIAL, R.string.perm_cat_special)
        catFilter(ui, viewModel, PermFilter.SIGNATURE, R.string.perm_cat_signature)
        catFilter(ui, viewModel, PermFilter.NORMAL, R.string.perm_cat_normal)
        catFilter(ui, viewModel, PermFilter.CUSTOM, R.string.perm_cat_custom)
    }
}

@Composable
private fun catFilter(ui: PermUiState, viewModel: PermissionMatrixViewModel, f: PermFilter, labelRes: Int) {
    FilterChip(selected = f in ui.filters, onClick = { viewModel.toggleFilter(f) }, label = { Text(stringResource(labelRes)) })
}

/* ---------- Overview ---------- */

@Composable
private fun OverviewView(data: PermissionData, ui: PermUiState, viewModel: PermissionMatrixViewModel, onOpenPerm: (String) -> Unit, onOpenApp: (String) -> Unit) {
    val perms = remember(data) { data.perms.values }
    val dangerous = remember(data) { perms.filter { it.category == PermCategory.DANGEROUS } }
    val special = remember(data) { perms.filter { it.category == PermCategory.SPECIAL } }
    val custom = remember(data) { perms.filter { it.custom } }
    val topDangerous = remember(data) { dangerous.sortedByDescending { it.requestedBy }.take(8) }
    val topApps = remember(data) { data.apps.sortedByDescending { data.categoryCount(it, PermCategory.DANGEROUS) }.take(8) }
    val topCustom = remember(data) { custom.sortedByDescending { it.requestedBy }.take(8) }
    val anomalies = remember(data) { data.apps.filter { !it.system && it.requested.any { p -> data.perms[p]?.category == PermCategory.SIGNATURE } } }
    val maxReq = remember(data) { (dangerous.maxOfOrNull { it.requestedBy } ?: 1).coerceAtLeast(1) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(data.apps.size.toString(), stringResource(R.string.perm_stat_apps), Modifier.weight(1f))
                StatTile(data.perms.size.toString(), stringResource(R.string.perm_stat_perms), Modifier.weight(1f))
                StatTile(dangerous.size.toString(), stringResource(R.string.perm_stat_dangerous), Modifier.weight(1f), MaterialTheme.colorScheme.error)
                StatTile(custom.size.toString(), stringResource(R.string.perm_stat_custom), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleSystem() }.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.perm_include_system), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.perm_scope_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = ui.includeSystem, onCheckedChange = { viewModel.toggleSystem() })
            }
        }
        item { SectionHeader(stringResource(R.string.perm_most_requested)) }
        items(topDangerous, key = { "d:" + it.name }) { p -> PermBarRow(p, maxReq) { onOpenPerm(p.name) } }
        item { SectionHeader(stringResource(R.string.perm_most_permissioned)) }
        items(topApps, key = { "a:" + it.packageName }) { app ->
            AppSummaryRow(app, data) { onOpenApp(app.packageName) }
        }
        if (topCustom.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.perm_custom_perms)) }
            items(topCustom, key = { "c:" + it.name }) { p -> PermBarRow(p, maxReq.coerceAtLeast(p.requestedBy)) { onOpenPerm(p.name) } }
        }
        if (anomalies.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.perm_anomalies), stringResource(R.string.perm_anomalies_hint)) }
            items(anomalies, key = { "x:" + it.packageName }) { app ->
                AppSummaryRow(app, data) { onOpenApp(app.packageName) }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PermBarRow(perm: PermInfo, max: Int, onClick: () -> Unit) {
    val color = categoryColor(perm.category)
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(perm.short, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            perm.definingLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            Text(perm.requestedBy.toString(), style = MaterialTheme.typography.labelMedium, color = color)
        }
        Box(modifier = Modifier.fillMaxWidth().height(5.dp).padding(top = 3.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(3.dp))) {
            Box(modifier = Modifier.fillMaxWidth(perm.requestedBy.toFloat() / max).height(5.dp).background(color, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun AppSummaryRow(app: PermApp, data: PermissionData, onClick: () -> Unit) {
    val d = data.categoryCount(app, PermCategory.DANGEROUS)
    val s = data.categoryCount(app, PermCategory.SPECIAL)
    val sig = data.categoryCount(app, PermCategory.SIGNATURE)
    val cust = data.customCount(app)
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppIcon(app.packageName, size = 34.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                if (d > 0) CountBadge("$d ${stringResource(R.string.perm_cat_dangerous).lowercase()}", MaterialTheme.colorScheme.error)
                if (s > 0) CountBadge("$s ${stringResource(R.string.perm_cat_special).lowercase()}", MaterialTheme.colorScheme.tertiary)
                if (sig > 0) CountBadge("$sig ${stringResource(R.string.perm_cat_signature).lowercase()}", Color(0xFF7C5CFF))
                if (cust > 0) CountBadge("$cust ${stringResource(R.string.perm_cat_custom).lowercase()}", MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/* ---------- Matrix ---------- */

@Composable
private fun MatrixView(data: PermissionData, ui: PermUiState, viewModel: PermissionMatrixViewModel, onOpenApp: (String) -> Unit, onOpenPerm: (String) -> Unit, onOpenCell: (String, String) -> Unit) {
    FilterRow(ui, viewModel)
    SearchField(ui, viewModel)
    val cols = remember(data, ui.filters) {
        data.perms.values.filter { ui.filters.match(it) }
            .sortedWith(compareBy<PermInfo> { it.category.ordinal }.thenByDescending { it.requestedBy })
    }
    val q = ui.query.trim()
    val rows = remember(data, cols, q) {
        val colNames = cols.map { it.name }.toSet()
        data.apps.filter { app -> app.requested.any { it in colNames } && (q.isBlank() || app.label.contains(q, true)) }
    }
    if (cols.isEmpty() || rows.isEmpty()) { EmptyNote(); return }
    val horizontal = rememberScrollState()
    val cell = 34.dp
    val nameW = 148.dp
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.horizontalScroll(horizontal).padding(start = nameW)) {
            cols.forEach { p ->
                Text(
                    p.short.take(5),
                    style = MonoStyle.copy(fontSize = 9.sp),
                    color = categoryColor(p.category),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(cell).clickable { onOpenPerm(p.name) }.padding(vertical = 6.dp),
                )
            }
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(rows, key = { it.packageName }) { app ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.width(nameW).clickable { onOpenApp(app.packageName) }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.packageName, size = 22.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(app.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(modifier = Modifier.horizontalScroll(horizontal)) {
                        cols.forEach { p ->
                            val c = categoryColor(p.category)
                            val requested = p.name in app.requested
                            val color = when { p.name in app.granted -> c; requested -> c.copy(alpha = 0.22f); else -> Color.Transparent }
                            Box(
                                modifier = Modifier.width(cell).height(28.dp).padding(1.dp)
                                    .background(color, RoundedCornerShape(4.dp))
                                    .let { if (requested) it.clickable { onOpenCell(app.packageName, p.name) } else it },
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------- By permission ---------- */

@Composable
private fun PermsView(data: PermissionData, ui: PermUiState, viewModel: PermissionMatrixViewModel, onOpenPerm: (String) -> Unit) {
    FilterRow(ui, viewModel)
    SearchField(ui, viewModel)
    val q = ui.query.trim()
    val list = remember(data, ui.filters, q) {
        data.perms.values
            .filter { ui.filters.match(it) && (q.isBlank() || it.short.contains(q, true) || it.name.contains(q, true) || it.group?.contains(q, true) == true) }
            .sortedWith(compareBy<PermInfo> { it.category.ordinal }.thenByDescending { it.requestedBy })
    }
    if (list.isEmpty()) { EmptyNote(); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(list, key = { it.name }) { p -> PermRow(p) { onOpenPerm(p.name) } }
    }
}

/** One aligned row in the By-permission list: color rail · name/label/badges · trailing count. */
@Composable
private fun PermRow(p: PermInfo, onClick: () -> Unit) {
    val color = categoryColor(p.category)
    val label = p.label?.takeIf { it != p.short }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.padding(top = 2.dp).width(3.dp).height(if (label != null) 34.dp else 18.dp).background(color, RoundedCornerShape(2.dp)))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.short, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.Ellipsis)
            label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CountBadge(categoryLabel(p.category), color)
                p.group?.let { CountBadge(GROUP_LABELS[it] ?: it.lowercase(), MaterialTheme.colorScheme.onSurfaceVariant) }
                if (p.custom) CountBadge(stringResource(R.string.perm_cat_custom), MaterialTheme.colorScheme.primary)
                p.definingLabel?.let { CountBadge(it, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(p.grantedBy.toString(), style = MaterialTheme.typography.titleMedium, color = color)
                Text("/${p.requestedBy}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
            }
            Text(stringResource(R.string.perm_granted_of), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/* ---------- By app ---------- */

@Composable
private fun AppsView(data: PermissionData, ui: PermUiState, viewModel: PermissionMatrixViewModel, onOpenApp: (String) -> Unit, onOpenPerm: (String) -> Unit) {
    SearchField(ui, viewModel)
    val q = ui.query.trim()
    val list = remember(data, q) { if (q.isBlank()) data.apps else data.apps.filter { it.label.contains(q, true) || it.packageName.contains(q, true) } }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    if (list.isEmpty()) { EmptyNote(); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(list, key = { it.packageName }) { app ->
            val open = expanded[app.packageName] == true
            Column(modifier = Modifier.fillMaxWidth()) {
                AppSummaryRow(app, data) { expanded[app.packageName] = !open }
                if (open) {
                    val perms = remember(app) { app.requested.mapNotNull { data.perms[it] }.sortedWith(compareBy<PermInfo> { it.category.ordinal }.thenBy { it.short }) }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        perms.forEach { p -> PermChip(p, granted = p.name in app.granted) { onOpenPerm(p.name) } }
                    }
                    Row(modifier = Modifier.padding(start = 60.dp, bottom = 10.dp)) {
                        Text(app.packageName, style = MonoStyle.copy(color = MaterialTheme.colorScheme.primary), modifier = Modifier.clickable { onOpenApp(app.packageName) }, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/* ---------- By group ---------- */

private val GROUP_LABELS = mapOf(
    "LOCATION" to "Location", "CAMERA" to "Camera", "MICROPHONE" to "Microphone", "CONTACTS" to "Contacts",
    "SMS" to "SMS", "PHONE" to "Phone", "CALL_LOG" to "Call log", "CALENDAR" to "Calendar",
    "STORAGE" to "Storage", "MEDIA" to "Photos & media", "SENSORS" to "Body sensors", "NEARBY_DEVICES" to "Nearby devices",
    "NOTIFICATIONS" to "Notifications", "ACTIVITY_RECOGNITION" to "Physical activity",
)

@Composable
private fun GroupsView(data: PermissionData, onOpenApp: (String) -> Unit) {
    val groups = remember(data) {
        data.perms.values.filter { it.group != null }
            .groupBy { it.group!! }
            .map { (g, ps) -> Triple(g, ps.map { it.name }.toSet(), data.apps.count { app -> ps.any { it.name in app.requested } }) }
            .sortedByDescending { it.third }
    }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    if (groups.isEmpty()) { EmptyNote(); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(groups, key = { it.first }) { (g, permNames, count) ->
            val open = expanded[g] == true
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().clickable { expanded[g] = !open }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(GROUP_LABELS[g] ?: g.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.perm_apps_count, count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (open) {
                    val apps = remember(g) { data.apps.filter { app -> permNames.any { it in app.requested } } }
                    apps.forEach { app ->
                        val granted = app.granted.any { it in permNames }
                        Row(modifier = Modifier.fillMaxWidth().clickable { onOpenApp(app.packageName) }.padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppIcon(app.packageName, size = 28.dp)
                            Text(app.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (granted) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/* ---------- Permission detail sheet ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermDetailSheet(
    name: String,
    ui: PermUiState,
    onOpenApp: (String) -> Unit,
    onToggle: (PermApp, PermInfo, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val data = ui.data ?: return
    val perm = data.perms[name] ?: return
    val apps = remember(name, data) { data.apps.filter { name in it.requested } }
    // pm grant/revoke only works for runtime (dangerous) permissions.
    val toggleable = perm.category == PermCategory.DANGEROUS
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(perm.label ?: perm.short, style = MaterialTheme.typography.titleLarge)
            SelectionContainer { Text(perm.name, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CountBadge(categoryLabel(perm.category), categoryColor(perm.category))
                if (perm.custom) CountBadge(stringResource(R.string.perm_cat_custom), MaterialTheme.colorScheme.primary)
                if (perm.privileged) CountBadge(stringResource(R.string.perm_flag_priv), Color(0xFF7C5CFF))
                if (perm.restricted) CountBadge(stringResource(R.string.perm_flag_restricted), MaterialTheme.colorScheme.error)
                perm.group?.let { CountBadge(GROUP_LABELS[it] ?: it.lowercase(), MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            perm.definingLabel?.let { Text(stringResource(R.string.perm_defined_by, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(stringResource(R.string.perm_req_grant, perm.requestedBy, perm.grantedBy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (toggleable) {
                val hint = if (ui.privileged) R.string.perm_toggle_hint else R.string.perm_grant_needs_shell
                Text(stringResource(hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            apps.forEach { app ->
                val granted = name in app.granted
                val busy = "${app.packageName}|$name" in ui.busy
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.weight(1f).clickable { onOpenApp(app.packageName) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(app.packageName, size = 30.dp)
                        Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Fixed-size slot (the Switch's M3 footprint) so swapping in the spinner
                    // never changes the row height.
                    Box(modifier = Modifier.size(width = 52.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                        when {
                            toggleable && ui.privileged ->
                                Switch(checked = granted, enabled = !busy, onCheckedChange = { onToggle(app, perm, it) })
                            busy -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            granted -> Icon(Icons.Default.Check, contentDescription = null, tint = categoryColor(perm.category), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Matrix cell sheet (one app × one permission) ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatrixCellSheet(
    pkg: String,
    permName: String,
    ui: PermUiState,
    onOpenApp: (String) -> Unit,
    onOpenPerm: (String) -> Unit,
    onToggle: (PermApp, PermInfo, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val data = ui.data ?: return
    val perm = data.perms[permName] ?: return
    val app = data.apps.find { it.packageName == pkg } ?: return
    val granted = permName in app.granted
    val busy = "$pkg|$permName" in ui.busy
    val toggleable = perm.category == PermCategory.DANGEROUS
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // App header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(app.packageName, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, style = MonoStyle.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
            }
            HorizontalDivider()

            // Permission + grant control
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.width(3.dp).height(34.dp).background(categoryColor(perm.category), RoundedCornerShape(2.dp)))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(perm.short, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    perm.label?.takeIf { it != perm.short }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Box(modifier = Modifier.size(width = 52.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                    when {
                        toggleable && ui.privileged -> Switch(checked = granted, enabled = !busy, onCheckedChange = { onToggle(app, perm, it) })
                        busy -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        granted -> Icon(Icons.Default.Check, contentDescription = null, tint = categoryColor(perm.category), modifier = Modifier.size(18.dp))
                    }
                }
            }
            SelectionContainer { Text(perm.name, style = MonoStyle.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CountBadge(categoryLabel(perm.category), categoryColor(perm.category))
                if (perm.custom) CountBadge(stringResource(R.string.perm_cat_custom), MaterialTheme.colorScheme.primary)
                perm.group?.let { CountBadge(GROUP_LABELS[it] ?: it.lowercase(), MaterialTheme.colorScheme.onSurfaceVariant) }
                CountBadge(if (granted) stringResource(R.string.perm_state_granted) else stringResource(R.string.perm_state_requested), if (granted) categoryColor(perm.category) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (toggleable && !ui.privileged) {
                Text(stringResource(R.string.perm_grant_needs_shell), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()

            // Actions
            CellAction(stringResource(R.string.perm_cell_open_app, app.label)) { onOpenApp(app.packageName) }
            CellAction(stringResource(R.string.perm_cell_all_apps, perm.requestedBy)) { onOpenPerm(perm.name) }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CellAction(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun categoryLabel(category: PermCategory): String = stringResource(
    when (category) {
        PermCategory.DANGEROUS -> R.string.perm_cat_dangerous
        PermCategory.SPECIAL -> R.string.perm_cat_special
        PermCategory.SIGNATURE -> R.string.perm_cat_signature
        PermCategory.NORMAL -> R.string.perm_cat_normal
        PermCategory.UNKNOWN -> R.string.perm_cat_unknown
    },
)

@Composable
private fun EmptyNote() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.perm_none_match), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ---------- Help / legend ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(ui: PermUiState, viewModel: PermissionMatrixViewModel, onDismiss: () -> Unit) {
    val data = ui.data
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.perm_help), style = MaterialTheme.typography.titleLarge)

            // Scope
            HelpSection(stringResource(R.string.perm_help_scope_title)) {
                if (data != null) {
                    Text(
                        stringResource(R.string.perm_help_scope_body, data.apps.size, data.perms.size),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleSystem() }.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.perm_include_system), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = ui.includeSystem, onCheckedChange = { viewModel.toggleSystem() })
                }
            }

            // Protection level
            HelpSection(stringResource(R.string.perm_help_cat_title)) {
                Text(stringResource(R.string.perm_help_cat_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HelpBadgeRow(stringResource(R.string.perm_cat_dangerous), categoryColor(PermCategory.DANGEROUS), stringResource(R.string.perm_help_dangerous))
                HelpBadgeRow(stringResource(R.string.perm_cat_special), categoryColor(PermCategory.SPECIAL), stringResource(R.string.perm_help_special))
                HelpBadgeRow(stringResource(R.string.perm_cat_signature), categoryColor(PermCategory.SIGNATURE), stringResource(R.string.perm_help_signature))
                HelpBadgeRow(stringResource(R.string.perm_cat_normal), categoryColor(PermCategory.NORMAL), stringResource(R.string.perm_help_normal))
            }

            // Custom
            HelpSection(stringResource(R.string.perm_help_custom_title)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
                    Text(stringResource(R.string.perm_help_custom_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Grant & revoke
            HelpSection(stringResource(R.string.perm_help_grant_title)) {
                Text(stringResource(R.string.perm_help_grant_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HelpSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun HelpBadgeRow(badge: String, color: Color, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.padding(top = 2.dp).width(84.dp)) { CountBadge(badge, color) }
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}
