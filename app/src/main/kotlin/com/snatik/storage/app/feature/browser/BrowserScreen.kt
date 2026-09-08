package com.snatik.storage.app.feature.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.StorageException
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.app.ui.components.Breadcrumbs
import com.snatik.storage.app.ui.components.ConfirmDialog
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.FileKindIcon
import com.snatik.storage.app.ui.components.NameDialog
import com.snatik.storage.app.ui.components.OperationDialog
import com.snatik.storage.app.util.Intents
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.fs.FileKind
import com.snatik.storage.core.fs.FsEntry
import com.snatik.storage.core.fs.OperationRunner
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    route: Route.Browser,
    onBack: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onOpenFile: (FsEntry) -> Unit,
    onViewAsText: (FsEntry) -> Unit,
    onViewAsHex: (FsEntry) -> Unit,
    onOpenAsDatabase: (FsEntry) -> Unit,
    onOpenAsPrefs: (FsEntry) -> Unit,
    onDiskUsage: () -> Unit,
    viewModel: BrowserViewModel = koinViewModel(parameters = { parametersOf(route) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(viewModel, resources) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is BrowserMessage.Text -> message.text
                is BrowserMessage.Finished -> when (message.kind) {
                    OperationRunner.Kind.COPY -> resources.getQuantityString(R.plurals.copied_items, message.count, message.count)
                    OperationRunner.Kind.MOVE -> resources.getQuantityString(R.plurals.moved_items, message.count, message.count)
                    OperationRunner.Kind.DELETE -> resources.getQuantityString(R.plurals.deleted_items, message.count, message.count)
                }
                is BrowserMessage.Failed -> message.error.message ?: resources.getString(R.string.operation_failed)
                is BrowserMessage.Cancelled -> resources.getString(R.string.operation_cancelled)
            }
            snackbar.showSnackbar(text)
        }
    }

    BackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }
    BackHandler(enabled = state.searchActive && !state.selectionMode) { viewModel.setSearchActive(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AnimatedContent(
                targetState = state.selectionMode,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "topbar",
            ) { selecting ->
                if (selecting) {
                    SelectionTopBar(state = state, viewModel = viewModel, onShare = { Intents.share(context, state.selected.toList()) })
                } else {
                    BrowserTopBar(route = route, state = state, viewModel = viewModel, onBack = onBack, onDiskUsage = onDiskUsage)
                }
            }
        },
        floatingActionButton = {
            val scrollingDown = listState.isScrollingDown()
            AnimatedVisibility(visible = !scrollingDown && !state.selectionMode && state.error == null, enter = fadeIn(), exit = fadeOut()) {
                NewItemFab(viewModel)
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = state.clipboard != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                state.clipboard?.let { clip -> PasteBar(clip, onPaste = viewModel::paste, onCancel = viewModel::cancelClipboard) }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (route.path != route.rootPath) {
                Breadcrumbs(rootLabel = route.rootLabel, rootPath = route.rootPath, path = route.path, onNavigate = onOpenDirectory)
            }
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            } else {
                Spacer(Modifier.height(2.dp))
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.error != null -> ErrorState(state.error!!)
                    !state.loading && state.entries.isEmpty() -> {
                        when {
                            state.query.isNotBlank() -> EmptyState(Icons.Default.SearchOff, stringResource(R.string.no_matches), null)
                            state.totalCount > 0 -> EmptyState(
                                Icons.Default.VisibilityOff,
                                pluralStringResource(R.plurals.only_hidden_items, state.hiddenCount, state.hiddenCount),
                                null,
                                action = { TextButton(onClick = { viewModel.setShowHidden(true) }) { Text(stringResource(R.string.show_hidden)) } },
                            )
                            else -> EmptyState(Icons.Default.FolderOff, stringResource(R.string.empty_directory), null)
                        }
                    }
                    else -> EntryList(
                        state = state,
                        listState = listState,
                        onOpen = { entry -> if (entry.isDirectory) onOpenDirectory(entry.path) else onOpenFile(entry) },
                        onToggle = viewModel::toggleSelected,
                        onOpenWith = { Intents.openWith(context, it.path) },
                        onShare = { Intents.share(context, listOf(it.path)) },
                        onRename = { viewModel.showDialog(BrowserDialog.Rename(it)) },
                        onDetails = viewModel::showDetails,
                        onCopy = { viewModel.copyToClipboard(listOf(it.path)) },
                        onCut = { viewModel.cutToClipboard(listOf(it.path)) },
                        onDelete = { viewModel.requestDelete(listOf(it.path)) },
                        onViewAsText = onViewAsText,
                        onViewAsHex = onViewAsHex,
                        onOpenAsDatabase = onOpenAsDatabase,
                        onOpenAsPrefs = onOpenAsPrefs,
                    )
                }
            }
        }
    }

    BrowserDialogs(state, viewModel)
    state.details?.let { details ->
        DetailsSheet(details, onComputeHash = viewModel::computeHash, onDismiss = viewModel::dismissDetails)
    }
    state.running?.let { running -> OperationDialog(running, onCancel = viewModel::cancelOperation) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopBar(route: Route.Browser, state: BrowserUiState, viewModel: BrowserViewModel, onBack: () -> Unit, onDiskUsage: () -> Unit) {
    var sortMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    TopAppBar(
        title = {
            if (state.searchActive) {
                TextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.search_in, route.path.substringAfterLast('/').ifEmpty { route.rootLabel })) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            } else {
                Text(if (route.path == route.rootPath) route.rootLabel else route.path.substringAfterLast('/'), maxLines = 1)
            }
        },
        navigationIcon = {
            IconButton(onClick = { if (state.searchActive) viewModel.setSearchActive(false) else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
            }
        },
        actions = {
            if (state.searchActive) {
                IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) }
            } else {
                IconButton(onClick = { viewModel.setSearchActive(true) }) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) }
                IconButton(onClick = onDiskUsage) { Icon(Icons.Default.DonutLarge, contentDescription = stringResource(R.string.analyze)) }
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort)) }
                SortMenu(
                    expanded = sortMenu,
                    preferences = state.preferences,
                    onDismiss = { sortMenu = false },
                    onSort = viewModel::setSort,
                    onShowHidden = viewModel::setShowHidden,
                )
            }
        },
    )
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    preferences: ListingPreferences,
    onDismiss: () -> Unit,
    onSort: (SortField, Boolean) -> Unit,
    onShowHidden: (Boolean) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.sort_by),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SortField.entries.forEach { field ->
            val label = when (field) {
                SortField.NAME -> R.string.sort_name
                SortField.MODIFIED -> R.string.sort_modified
                SortField.SIZE -> R.string.sort_size
                SortField.TYPE -> R.string.sort_type
            }
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                leadingIcon = { RadioButton(selected = preferences.sort.field == field, onClick = null) },
                onClick = { onSort(field, preferences.sort.ascending) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_descending)) },
            leadingIcon = { Checkbox(checked = !preferences.sort.ascending, onCheckedChange = null) },
            onClick = { onSort(preferences.sort.field, !preferences.sort.ascending) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.show_hidden)) },
            leadingIcon = { Checkbox(checked = preferences.showHidden, onCheckedChange = null) },
            onClick = { onShowHidden(!preferences.showHidden) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(state: BrowserUiState, viewModel: BrowserViewModel, onShare: () -> Unit) {
    val single = state.selected.singleOrNull()?.let { path -> state.entries.firstOrNull { it.path == path } }
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.selected_count, state.selected.size, state.selected.size)) },
        navigationIcon = {
            IconButton(onClick = viewModel::clearSelection) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection)) }
        },
        actions = {
            IconButton(onClick = { if (state.allSelected) viewModel.clearSelection() else viewModel.selectAll() }) {
                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
            }
            IconButton(onClick = { viewModel.copyToClipboard(state.selected) }) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy)) }
            IconButton(onClick = { viewModel.cutToClipboard(state.selected) }) { Icon(Icons.Default.ContentCut, contentDescription = stringResource(R.string.cut)) }
            IconButton(onClick = { viewModel.requestDelete(state.selected) }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
            var more by remember { mutableStateOf(false) }
            IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share)) },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                    onClick = { more = false; onShare() },
                )
                if (single != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                        onClick = { more = false; viewModel.clearSelection(); viewModel.showDialog(BrowserDialog.Rename(single)) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.details)) },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = { more = false; viewModel.showDetails(single) },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
private fun NewItemFab(viewModel: BrowserViewModel) {
    var menu by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = { menu = true }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_item))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_folder)) },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                onClick = { menu = false; viewModel.showDialog(BrowserDialog.NewFolder) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_file)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                onClick = { menu = false; viewModel.showDialog(BrowserDialog.NewFile) },
            )
        }
    }
}

@Composable
private fun PasteBar(clip: FileClipboard.Content, onPaste: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = if (clip.mode == FileClipboard.Mode.COPY) R.plurals.items_to_copy else R.plurals.items_to_move
            Text(
                pluralStringResource(label, clip.paths.size, clip.paths.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = onPaste) {
                Icon(Icons.Default.ContentPaste, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.paste_here))
            }
        }
    }
}

@Composable
private fun ErrorState(error: StorageException) {
    val (title, body) = when (error) {
        is StorageException.NotFound -> stringResource(R.string.directory_missing) to error.path
        is StorageException.Io -> stringResource(R.string.cannot_read_directory) to
            listOfNotNull(stringResource(R.string.cannot_read_hint), error.cause?.message).joinToString("\n\n")
        else -> stringResource(R.string.cannot_read_directory) to (error.message ?: "")
    }
    EmptyState(Icons.Default.Block, title, body)
}

@Composable
private fun EntryList(
    state: BrowserUiState,
    listState: LazyListState,
    onOpen: (FsEntry) -> Unit,
    onToggle: (String) -> Unit,
    onOpenWith: (FsEntry) -> Unit,
    onShare: (FsEntry) -> Unit,
    onRename: (FsEntry) -> Unit,
    onDetails: (FsEntry) -> Unit,
    onCopy: (FsEntry) -> Unit,
    onCut: (FsEntry) -> Unit,
    onDelete: (FsEntry) -> Unit,
    onViewAsText: (FsEntry) -> Unit,
    onViewAsHex: (FsEntry) -> Unit,
    onOpenAsDatabase: (FsEntry) -> Unit,
    onOpenAsPrefs: (FsEntry) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(state.entries, key = { it.path }) { entry ->
            val selected = entry.path in state.selected
            EntryRow(
                entry = entry,
                selected = selected,
                selectionMode = state.selectionMode,
                onClick = { if (state.selectionMode) onToggle(entry.path) else onOpen(entry) },
                onLongClick = { onToggle(entry.path) },
                menu = { dismiss ->
                    if (!entry.isDirectory) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.open_with)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) }, onClick = { dismiss(); onOpenWith(entry) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { dismiss(); onShare(entry) })
                        if (!entry.kind.isTextLike) DropdownMenuItem(text = { Text(stringResource(R.string.view_as_text)) }, onClick = { dismiss(); onViewAsText(entry) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.view_as_hex)) }, onClick = { dismiss(); onViewAsHex(entry) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.open_as_database)) }, onClick = { dismiss(); onOpenAsDatabase(entry) })
                        if (entry.kind == FileKind.XML) DropdownMenuItem(text = { Text(stringResource(R.string.open_as_prefs)) }, onClick = { dismiss(); onOpenAsPrefs(entry) })
                        HorizontalDivider()
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { dismiss(); onCopy(entry) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.cut)) }, leadingIcon = { Icon(Icons.Default.ContentCut, null) }, onClick = { dismiss(); onCut(entry) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }, onClick = { dismiss(); onRename(entry) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.details)) }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { dismiss(); onDetails(entry) })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { dismiss(); onDelete(entry) },
                    )
                },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: FsEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FileKindIcon(entry = entry, selected = selected)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                color = if (entry.isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val childCount = entry.childCount
            val primary = when {
                !entry.isDirectory -> entry.size.readableSize()
                childCount != null -> pluralStringResource(R.plurals.items_count, childCount, childCount)
                !entry.canRead -> stringResource(R.string.no_access)
                else -> null
            }
            val symlink = if (entry.isSymlink) "  ·  " + stringResource(R.string.symlink) else ""
            val detail = listOfNotNull(primary, entry.lastModified.relativeTime(context)).joinToString("  ·  ") + symlink
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (!selectionMode) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu { open = false }
                }
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun BrowserDialogs(state: BrowserUiState, viewModel: BrowserViewModel) {
    val invalidText = stringResource(R.string.name_invalid)
    val existsText = stringResource(R.string.name_exists)
    val nameError: (NameError?) -> String? = { e ->
        when (e) {
            NameError.INVALID -> invalidText
            NameError.EXISTS -> existsText
            null -> null
        }
    }
    when (val dialog = state.dialog) {
        null -> Unit
        BrowserDialog.NewFolder -> NameDialog(
            title = stringResource(R.string.new_folder),
            confirmLabel = stringResource(R.string.create),
            validate = { nameError(viewModel.validateName(it)) },
            onConfirm = viewModel::createFolder,
            onDismiss = viewModel::dismissDialog,
        )
        BrowserDialog.NewFile -> NameDialog(
            title = stringResource(R.string.new_file),
            confirmLabel = stringResource(R.string.create),
            initial = "untitled.txt",
            selectBaseName = true,
            validate = { nameError(viewModel.validateName(it)) },
            onConfirm = viewModel::createFile,
            onDismiss = viewModel::dismissDialog,
        )
        is BrowserDialog.Rename -> NameDialog(
            title = stringResource(R.string.rename),
            confirmLabel = stringResource(R.string.rename),
            initial = dialog.entry.name,
            selectBaseName = !dialog.entry.isDirectory,
            validate = { nameError(viewModel.validateName(it, current = dialog.entry.name)) },
            onConfirm = { viewModel.rename(dialog.entry, it) },
            onDismiss = viewModel::dismissDialog,
        )
        is BrowserDialog.ConfirmDelete -> ConfirmDialog(
            title = pluralStringResource(R.plurals.delete_items_title, dialog.paths.size, dialog.paths.size),
            body = stringResource(R.string.delete_items_body),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = { viewModel.confirmDelete(dialog.paths) },
            onDismiss = viewModel::dismissDialog,
        )
    }
}

/** True while the user is scrolling towards the end of the list, used to hide the FAB. */
@Composable
private fun LazyListState.isScrollingDown(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            val down = if (previousIndex != firstVisibleItemIndex) {
                previousIndex < firstVisibleItemIndex
            } else {
                previousOffset < firstVisibleItemScrollOffset
            }
            previousIndex = firstVisibleItemIndex
            previousOffset = firstVisibleItemScrollOffset
            down
        }
    }.value
}
