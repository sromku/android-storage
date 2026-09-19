package com.snatik.storage.app.feature.media

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.TopLevel
import com.snatik.storage.app.ui.components.TopLevelBar
import com.snatik.storage.app.util.Intents
import com.snatik.storage.app.util.readableSize
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGridScreen(
    onOpenMedia: (Long) -> Unit,
    onSwitchTab: (TopLevel) -> Unit,
    onFindDuplicates: () -> Unit,
    onBrowseCloud: () -> Unit,
    onSyncCamera: () -> Unit,
    onInsights: () -> Unit,
    viewModel: MediaGridViewModel = koinViewModel(),
) {
    var appMenu by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showConvert by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()
    val allItems = remember(state.sections) { state.sections.flatMap { it.items } }
    val selectedItems = remember(selection, allItems) { allItems.filter { it.id in selection } }
    fun toggle(id: Long) { selection = if (id in selection) selection - id else selection + id }
    fun clearSelection() { selection = emptySet() }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.load() }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.load()
        clearSelection()
    }

    fun shareSelected(strip: Boolean) {
        val targets = selectedItems
        if (targets.isEmpty()) return
        if (strip) {
            Toast.makeText(context, R.string.share_stripping, Toast.LENGTH_SHORT).show()
            scope.launch { Intents.share(context, MediaShare.stripManyToCache(context, targets)) }
        } else {
            Intents.share(context, targets.map { it.path })
        }
        clearSelection()
    }

    fun convertSelected(options: ConvertOptions) {
        val targets = selectedItems.filter { !it.isVideo }
        if (targets.isEmpty()) return
        Toast.makeText(context, R.string.converting, Toast.LENGTH_SHORT).show()
        scope.launch {
            val done = MediaConvert.toJpeg(context, targets, options)
            Toast.makeText(context, context.resources.getQuantityString(R.plurals.converted, done, done), Toast.LENGTH_SHORT).show()
            if (done > 0) viewModel.load()
        }
        clearSelection()
    }

    fun deleteSelected() {
        val uris = selectedItems.map { it.uri }
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            runCatching { uris.forEach { context.contentResolver.delete(it, null, null) } }
            viewModel.load()
            clearSelection()
        }
    }

    BackHandler(enabled = selecting) { clearSelection() }

    if (appMenu) {
        LibraryToolsSheet(
            onDismiss = { appMenu = false },
            onFindDuplicates = { appMenu = false; onFindDuplicates() },
            onBrowseCloud = { appMenu = false; onBrowseCloud() },
            onSyncCamera = { appMenu = false; onSyncCamera() },
            onInsights = { appMenu = false; onInsights() },
        )
    }
    if (showConvert) {
        ConvertSheet(count = selectedItems.count { !it.isVideo }, onDismiss = { showConvert = false }) { options ->
            showConvert = false
            convertSelected(options)
        }
    }
    if (showMove) {
        MoveToFolderSheet(onDismiss = { showMove = false }) { folder ->
            showMove = false
            val targets = selectedItems.map { it.path }.filter { it.isNotBlank() }
            scope.launch {
                val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MediaEdit.move(context, targets, folder) }
                Toast.makeText(context, if (n > 0) context.getString(R.string.moved_to, folder.name) else context.getString(R.string.move_failed), Toast.LENGTH_SHORT).show()
                clearSelection()
            }
        }
    }
    BackHandler(enabled = !selecting && state.searchActive) { viewModel.setSearchActive(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.photos_delete_title)) },
            text = { Text(pluralStringResource(R.plurals.photos_delete_body, selection.size, selection.size)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; deleteSelected() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(
        modifier = if (selecting) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selecting) {
                SelectionBar(
                    count = selection.size,
                    onClose = { clearSelection() },
                    onShare = { shareSelected(strip = false) },
                    onShareStripped = { shareSelected(strip = true) },
                    onConvert = { showConvert = true },
                    onMove = { showMove = true },
                    onDelete = { confirmDelete = true },
                )
            } else if (state.searchActive) {
                SearchBar(
                    query = state.query,
                    onQuery = viewModel::setQuery,
                    onClose = { viewModel.setSearchActive(false) },
                )
            } else {
                LargeTopAppBar(
                    scrollBehavior = scrollBehavior,
                    title = {
                        Column {
                            Text(stringResource(R.string.tab_photos))
                            if (state.total > 0) {
                                Text(
                                    stringResource(R.string.photos_summary, state.total, state.totalBytes.readableSize()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.setSearchActive(true) }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                        IconButton(onClick = { appMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
                        }
                    },
                )
            }
        },
        bottomBar = { if (!selecting) TopLevelBar(current = TopLevel.PHOTOS, onSelect = onSwitchTab) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.total == 0 -> EmptyMedia(
                onGrant = { permLauncher.launch(mediaPermissions()) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                if (!selecting) {
                    MediaFilterBar(
                        filter = state.filter,
                        hasCamera = state.hasCamera,
                        hasFavorites = state.hasFavorites,
                        onFilter = viewModel::setFilter,
                    )
                }
                if (state.sections.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            stringResource(R.string.photos_no_matches),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 106.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 2.dp,
                            bottom = padding.calculateBottomPadding() + 8.dp,
                            start = 2.dp, end = 2.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        state.sections.forEach { section ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "${section.label}   ·   ${section.items.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp),
                                )
                            }
                            items(section.items, key = { it.id }) { item ->
                                MediaCell(
                                    item = item,
                                    selected = item.id in selection,
                                    selecting = selecting,
                                    onClick = { if (selecting) toggle(item.id) else onOpenMedia(item.id) },
                                    onLongClick = { toggle(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close)) }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.photos_search_hint)) },
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
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaFilterBar(
    filter: MediaFilter,
    hasCamera: Boolean,
    hasFavorites: Boolean,
    onFilter: (MediaFilter) -> Unit,
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaFilter.entries.filter { (it != MediaFilter.CAMERA || hasCamera) && (it != MediaFilter.FAVORITES || hasFavorites) }.forEach { f ->
            FilterChip(
                selected = filter == f,
                onClick = { onFilter(f) },
                label = { Text(stringResource(filterLabel(f))) },
            )
        }
    }
}

private fun filterLabel(f: MediaFilter): Int = when (f) {
    MediaFilter.ALL -> R.string.filter_all
    MediaFilter.PHOTOS -> R.string.filter_photos
    MediaFilter.VIDEOS -> R.string.filter_videos
    MediaFilter.RAW -> R.string.filter_raw
    MediaFilter.SCREENSHOTS -> R.string.filter_screenshots
    MediaFilter.CAMERA -> R.string.filter_camera
    MediaFilter.FAVORITES -> R.string.filter_favorites
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onShareStripped: () -> Unit,
    onConvert: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.photos_selected, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
        },
        actions = {
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_stripped)) },
                        leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                        onClick = { menu = false; onShareStripped() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.convert_to_jpg)) },
                        leadingIcon = { Icon(Icons.Default.Transform, contentDescription = null) },
                        onClick = { menu = false; onConvert() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_to_folder)) },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                        onClick = { menu = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(
    item: MediaItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (selected) Modifier.padding(9.dp) else Modifier)
                .clip(RoundedCornerShape(if (selected) 8.dp else 4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = mediaModel(item),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (item.isVideo) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text(formatDuration(item.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (selecting) {
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(Color.White, CircleShape),
                )
            } else {
                Icon(
                    Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyMedia(onGrant: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val granted = mediaPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(if (granted) R.string.photos_empty else R.string.photos_needs_access),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            stringResource(if (granted) R.string.photos_empty_body else R.string.photos_needs_access_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!granted) {
            FilledTonalButton(onClick = onGrant, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.photos_grant))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
