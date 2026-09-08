package com.snatik.storage.app.feature.disk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.app.ui.components.Breadcrumbs
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.fs.DirNode
import com.snatik.storage.core.fs.LargeFile
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.max

/** Stand-in node so the files sitting directly in a folder get a tile too. */
private class FilesHere(val size: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiskUsageScreen(
    route: Route.DiskUsage,
    onBack: () -> Unit,
    onBrowse: (String) -> Unit,
    onSunburst: () -> Unit,
    viewModel: DiskUsageViewModel = koinViewModel(parameters = { parametersOf(route.path) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.trail.size > 1) { viewModel.up() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.disk_usage_title), style = MaterialTheme.typography.titleMedium)
                        Text(route.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.up()) onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) }
                },
                actions = {
                    IconButton(onClick = onSunburst) { Icon(Icons.Default.DonutLarge, contentDescription = stringResource(R.string.sunburst_title)) }
                    IconButton(onClick = viewModel::scan, enabled = !state.scanning) { Icon(Icons.Default.Refresh, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.scanning -> Scanning(state)
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.scan_failed), state.error)
                else -> {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        DiskView.entries.forEachIndexed { i, view ->
                            SegmentedButton(
                                selected = state.view == view,
                                onClick = { viewModel.setView(view) },
                                shape = SegmentedButtonDefaults.itemShape(i, DiskView.entries.size),
                            ) { Text(stringResource(if (view == DiskView.TREE) R.string.tree else R.string.largest_files)) }
                        }
                    }
                    when (state.view) {
                        DiskView.TREE -> TreeView(route, state, viewModel, onBrowse)
                        DiskView.LARGEST -> LargestView(state.largest, onBrowse)
                    }
                }
            }
        }
    }
}

@Composable
private fun Scanning(state: DiskUsageUiState) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        CircularProgressIndicator()
        Text(stringResource(R.string.scanning), style = MaterialTheme.typography.titleMedium)
        state.progress?.let { p ->
            Text(stringResource(R.string.scan_progress, p.filesSeen, p.bytesSeen.readableSize()), style = MaterialTheme.typography.bodyMedium)
            Text(p.currentPath, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TreeView(route: Route.DiskUsage, state: DiskUsageUiState, viewModel: DiskUsageViewModel, onBrowse: (String) -> Unit) {
    val current = state.current ?: return
    val root = state.root ?: return
    Breadcrumbs(
        rootLabel = route.label,
        rootPath = root.path,
        path = current.path,
        onNavigate = { path -> viewModel.jumpTo(state.trail.indexOfFirst { it.path == path }.coerceAtLeast(0)) },
    )
    val children = remember(current) { current.children }
    val filesHere = remember(current) { current.size - children.sumOf { it.size } }
    Treemap(
        children = children,
        filesHere = filesHere,
        onEnter = viewModel::enter,
        modifier = Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 16.dp),
    )
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(current.size.readableSize(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp).weight(1f))
                Text(stringResource(R.string.browse), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onBrowse(current.path) }.padding(8.dp))
            }
        }
        items(children, key = { it.path }) { child -> NodeRow(child, current.size, onClick = { viewModel.enter(child) }) }
        if (filesHere > 0) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.other_files), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.files_in_dir, current.fileCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(filesHere.readableSize(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NodeRow(node: DirNode, parentSize: Long, onClick: () -> Unit) {
    val fraction = if (parentSize > 0) node.size.toFloat() / parentSize else 0f
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(node.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
            Text(node.size.readableSize(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(start = 36.dp, top = 6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun LargestView(largest: List<LargeFile>, onBrowse: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        items(largest, key = { it.path }) { file ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onBrowse(file.path.substringBeforeLast('/')) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.path.substringAfterLast('/'), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(file.path.substringBeforeLast('/'), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
                Text(file.size.readableSize(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun Treemap(children: List<DirNode>, filesHere: Long, onEnter: (DirNode) -> Unit, modifier: Modifier = Modifier) {
    val items: List<Any> = remember(children, filesHere) {
        (children + listOfNotNull(if (filesHere > 0) FilesHere(filesHere) else null)).sortedByDescending { weightOf(it) }
    }
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    val outline = MaterialTheme.colorScheme.surface
    val filesColor = MaterialTheme.colorScheme.surfaceVariant
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontSize = 11.sp)
    var tiles: List<TreemapTile<Any>> = emptyList()

    Canvas(
        modifier = modifier.pointerInput(items) {
            detectTapGestures { offset ->
                val hit = tiles.firstOrNull { it.rect.contains(offset) }?.item
                if (hit is DirNode) onEnter(hit)
            }
        },
    ) {
        tiles = squarify(items, ::weightOf, Rect(Offset.Zero, Size(size.width, size.height)))
        val gap = with(density) { 2.dp.toPx() }
        tiles.forEachIndexed { index, tile ->
            val rect = tile.rect.deflate(gap / 2)
            if (rect.width <= 0 || rect.height <= 0) return@forEachIndexed
            val color = if (tile.item is FilesHere) filesColor else lerp(primary, container, (index.toFloat() / max(1, tiles.size - 1)) * 0.85f)
            drawRoundRect(color = color, topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(gap * 2))
            drawRoundRect(color = outline, topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(gap * 2), style = Stroke(gap / 2))
            val name = (tile.item as? DirNode)?.name ?: "…"
            val padding = gap * 3
            if (rect.width > padding * 2 + 24 && rect.height > padding * 2 + 12) {
                val layout = measurer.measure(
                    text = "$name\n${weightOf(tile.item).readableSize()}",
                    style = labelStyle.copy(color = if (color.luminance() > 0.4f) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.9f)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = (rect.width - padding * 2).toInt().coerceAtLeast(1), maxHeight = (rect.height - padding * 2).toInt().coerceAtLeast(1)),
                )
                if (layout.size.height <= rect.height - padding * 2) {
                    drawText(layout, topLeft = Offset(rect.left + padding, rect.top + padding))
                }
            }
        }
    }
}

private fun weightOf(item: Any): Long = when (item) {
    is DirNode -> item.size
    is FilesHere -> item.size
    else -> 0
}
