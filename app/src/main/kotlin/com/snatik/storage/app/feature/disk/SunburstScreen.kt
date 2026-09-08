package com.snatik.storage.app.feature.disk

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.core.fs.DirNode
import com.snatik.storage.core.fs.DiskScanner
import com.snatik.storage.core.fs.ScanEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class SunburstViewModel(private val path: String, private val scanner: DiskScanner) : ViewModel() {
    private val _root = MutableStateFlow<DirNode?>(null)
    val root: StateFlow<DirNode?> = _root.asStateFlow()
    init {
        viewModelScope.launch {
            scanner.scan(path, largestCount = 1).collect { if (it is ScanEvent.Done) _root.value = it.root }
        }
    }
}

/** One drawn ring segment. */
private class Arc(val node: DirNode, val depth: Int, val start: Float, val sweep: Float)

private const val MAX_DEPTH = 4
private const val MIN_SWEEP = 1.5f

private fun layout(focus: DirNode, into: MutableList<Arc>) {
    fun walk(node: DirNode, depth: Int, start: Float, sweep: Float) {
        if (depth > MAX_DEPTH || sweep < MIN_SWEEP) return
        if (depth > 0) into += Arc(node, depth, start, sweep)
        val total = node.size.coerceAtLeast(1)
        var cursor = start
        for (child in node.children) {
            val childSweep = sweep * (child.size.toFloat() / total)
            if (childSweep >= MIN_SWEEP) walk(child, depth + 1, cursor, childSweep)
            cursor += childSweep
        }
    }
    walk(focus, 0, 0f, 360f)
}

private val SUNBURST_PALETTE = listOf(
    Color(0xFF4F86C6), Color(0xFF57A773), Color(0xFFE0A458), Color(0xFFB56576),
    Color(0xFF8367C7), Color(0xFF4CB5AE), Color(0xFFD16BA5), Color(0xFF86A873),
    Color(0xFFC96567), Color(0xFF5B8FB0),
)

private fun sliceColor(hueIndex: Int, depth: Int): Color {
    val base = SUNBURST_PALETTE[hueIndex % SUNBURST_PALETTE.size]
    val f = (1f - (depth - 1) * 0.16f).coerceIn(0.5f, 1f)
    return base.copy(alpha = f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SunburstScreen(route: Route.Sunburst, onBack: () -> Unit, viewModel: SunburstViewModel = koinViewModel { parametersOf(route.path) }) {
    val root by viewModel.root.collectAsStateWithLifecycle()
    // Focus path: nodes from root to the currently centered node.
    var focusPath by remember(root) { mutableStateOf(listOfNotNull(root)) }
    val focus = focusPath.lastOrNull()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sunburst_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (root == null || focus == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // breadcrumb
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        focusPath.forEachIndexed { i, node ->
                            if (i > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { focusPath = focusPath.take(i + 1) }) {
                                Text(if (i == 0) route.label else node.name.ifEmpty { "/" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    val arcs = remember(focus) { ArrayList<Arc>().also { layout(focus, it) } }
                    val scheme = MaterialTheme.colorScheme
                    // top-level hue index per subtree root: map each depth-1 node to an index
                    val hueOf = remember(focus) {
                        val map = HashMap<String, Int>()
                        focus.children.forEachIndexed { idx, c -> map[c.path] = idx }
                        map
                    }
                    fun hueIndex(arc: Arc): Int {
                        // Walk arcs is not hierarchical here; approximate by the depth-1 ancestor via path prefix.
                        val top = focus.children.firstOrNull { arc.node.path == it.path || arc.node.path.startsWith(it.path + "/") }
                        return hueOf[top?.path] ?: 0
                    }

                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        var canvasSize by remember { mutableStateOf(Size.Zero) }
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp)
                                .pointerInput(focus) {
                                    detectTapGestures { tap ->
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val dx = tap.x - center.x
                                        val dy = tap.y - center.y
                                        val dist = hypot(dx, dy)
                                        val radius = min(size.width, size.height) / 2f
                                        val ringWidth = radius / (MAX_DEPTH + 1)
                                        if (dist < ringWidth) {
                                            // center tap = go up one
                                            if (focusPath.size > 1) focusPath = focusPath.dropLast(1)
                                            return@detectTapGestures
                                        }
                                        val depth = (dist / ringWidth).toInt()
                                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                        angle = ((angle - (-90f)) % 360f + 360f) % 360f
                                        val hit = arcs.firstOrNull { it.depth == depth && angle >= it.start && angle < it.start + it.sweep }
                                        if (hit != null && hit.node.children.isNotEmpty()) {
                                            focusPath = focusPath + hit.node
                                        }
                                    }
                                },
                        ) {
                            canvasSize = size
                            val radius = min(size.width, size.height) / 2f
                            val ringWidth = radius / (MAX_DEPTH + 1)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            // center disc = focus
                            drawCircle(scheme.surfaceVariant, radius = ringWidth * 0.92f, center = center)
                            // Draw each segment as a thick stroked arc (an annular band) so rings never overlap.
                            arcs.forEach { arc ->
                                val midR = ringWidth * arc.depth + ringWidth / 2f
                                val color = sliceColor(hueIndex(arc), arc.depth)
                                // small angular gap between neighbours for legibility
                                val gap = if (arc.sweep > 4f) 0.5f else 0f
                                drawArc(
                                    color = color,
                                    startAngle = arc.start - 90f + gap,
                                    sweepAngle = (arc.sweep - gap * 2).coerceAtLeast(0.2f),
                                    useCenter = false,
                                    topLeft = Offset(center.x - midR, center.y - midR),
                                    size = Size(midR * 2, midR * 2),
                                    style = Stroke(width = ringWidth * 0.9f),
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(humanSize(focus.size), style = MaterialTheme.typography.titleMedium)
                            Text(if (focusPath.size == 1) route.label else focus.name.ifEmpty { "/" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                    Text(stringResource(R.string.sunburst_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
    }
}

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return "${(v * 10).roundToInt() / 10.0} ${units[i]}"
}
