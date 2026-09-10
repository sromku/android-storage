package com.snatik.storage.app.feature.viewer

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** A node in the APK composition tree: category -> subcategory -> file, aggregated by size. */
class AnalyzeNode(val name: String) {
    var size: Long = 0L
    var hue: Int = 0
    var entryName: String? = null   // full zip path when this node is a single file (a leaf)
    val childMap = LinkedHashMap<String, AnalyzeNode>()
    val children: List<AnalyzeNode> get() = childMap.values.sortedByDescending { it.size }
    fun child(key: String, display: String = key) = childMap.getOrPut(key) { AnalyzeNode(display) }
    val leafCount: Int get() = if (childMap.isEmpty()) 1 else childMap.values.sumOf { it.leafCount }
}

data class CategorySlice(val name: String, val size: Long, val count: Int, val colorIndex: Int)

data class ApkAnalysis(
    val root: AnalyzeNode,
    val categories: List<CategorySlice>,
    val totalSize: Long,
    val totalCompressed: Long,
    val entryCount: Int,
)

private val APK_PALETTE = listOf(
    Color(0xFF4F86C6), Color(0xFF57A773), Color(0xFFE0A458), Color(0xFFB56576),
    Color(0xFF8367C7), Color(0xFF4CB5AE), Color(0xFFD16BA5), Color(0xFF86A873),
    Color(0xFFC96567), Color(0xFF5B8FB0),
)

class ApkAnalyzeViewModel(private val path: String, private val context: Context, private val manifests: com.snatik.storage.core.apps.ManifestDecoder) : ViewModel() {
    val name = path.substringAfterLast('/')
    private val _state = MutableStateFlow<ApkAnalysis?>(null)
    val state: StateFlow<ApkAnalysis?> = _state.asStateFlow()

    init { viewModelScope.launch { _state.value = withContext(Dispatchers.IO) { analyze() } } }

    /** Extract (decoding compiled XML) one entry to the cache and return its path, for opening. */
    suspend fun openEntry(entryName: String): String? = withContext(Dispatchers.IO) {
        if (entryName.endsWith(".xml", ignoreCase = true)) {
            val pkg = runCatching { context.packageManager.getPackageArchiveInfo(path, 0)?.packageName }.getOrNull().orEmpty()
            val decoded = runCatching { manifests.decodeEntry(pkg, path, entryName) }.getOrNull()
            if (!decoded.isNullOrBlank()) return@withContext writeCache(entryName, decoded.toByteArray())
        }
        runCatching {
            ZipFile(File(path)).use { zip ->
                val e = zip.getEntry(entryName) ?: return@use null
                zip.getInputStream(e).use { writeCache(entryName, it.readBytes()) }
            }
        }.getOrNull()
    }

    private fun writeCache(entryName: String, bytes: ByteArray): String {
        val dir = File(context.cacheDir, "apk-extract").apply { mkdirs() }
        val out = File(dir, entryName.replace('/', '_').takeLast(120))
        out.writeBytes(bytes)
        return out.absolutePath
    }

    private fun analyze(): ApkAnalysis {
        val root = AnalyzeNode(name)
        var totalCompressed = 0L
        var count = 0
        ZipFile(File(path)).use { zip ->
            val en = zip.entries()
            while (en.hasMoreElements()) {
                val e = en.nextElement()
                if (e.isDirectory) continue
                val size = e.size.coerceAtLeast(0)
                totalCompressed += e.compressedSize.coerceAtLeast(0)
                count++
                val (cat, sub) = categorize(e.name)
                val short = e.name.substringAfterLast('/')
                root.size += size
                val c = root.child(cat); c.size += size
                // Leaves are keyed by the full entry path (so same-named files in different folders
                // stay distinct) and carry it for extraction; they display the short name.
                val leaf = if (sub != null) {
                    val sc = c.child(sub); sc.size += size; sc.child(e.name, short)
                } else {
                    c.child(e.name, short)
                }
                leaf.size += size
                leaf.entryName = e.name
            }
        }
        // Assign a colour per top-level category (largest first), propagate to descendants.
        val cats = root.children
        cats.forEachIndexed { i, cat -> paint(cat, i) }
        val categories = cats.map { CategorySlice(it.name, it.size, it.leafCount, it.hue) }
        return ApkAnalysis(root, categories, root.size, totalCompressed, count)
    }

    private fun paint(node: AnalyzeNode, hue: Int) { node.hue = hue; node.childMap.values.forEach { paint(it, hue) } }

    /** Map a zip entry to (category, subcategory-or-null). */
    private fun categorize(n: String): Pair<String, String?> = when {
        n.matches(Regex("classes\\d*\\.dex")) -> "Code (dex)" to null
        n.startsWith("lib/") -> "Native libs" to n.removePrefix("lib/").substringBefore('/')
        n == "resources.arsc" -> "Resource table" to null
        n.startsWith("res/") -> "Resources" to n.removePrefix("res/").substringBefore('/').substringBefore('-')
        n.startsWith("assets/") -> "Assets" to n.removePrefix("assets/").substringBefore('/').ifEmpty { "(root)" }
        n.startsWith("META-INF/") -> "Signing / META-INF" to null
        n.startsWith("kotlin/") -> "Kotlin metadata" to null
        else -> "Other" to null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkAnalyzeScreen(path: String, onBack: () -> Unit, onOpenPath: (String) -> Unit = {}, viewModel: ApkAnalyzeViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val analysis by viewModel.state.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val openLeaf: (AnalyzeNode) -> Unit = { node -> node.entryName?.let { en -> scope.launch { viewModel.openEntry(en)?.let(onOpenPath) } } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apk_analyze_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val a = analysis
            if (a == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                var focus by remember(a) { mutableStateOf(listOf(a.root)) }
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { HeaderCard(a) }
                    item { SunburstCard(a, focus, onFocus = { focus = it }, onOpenLeaf = openLeaf) }
                    item { CompositionBar(a) }
                    item { Text(stringResource(R.string.apk_analyze_categories), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 4.dp)) }
                    items(a.categories.size) { i ->
                        val c = a.categories[i]
                        CategoryRow(c, a.totalSize) {
                            val node = a.root.children.firstOrNull { it.name == c.name }
                            if (node != null) focus = listOf(a.root, node)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(a: ApkAnalysis) {
    val saved = if (a.totalSize > 0) (100 - a.totalCompressed * 100 / a.totalSize).toInt() else 0
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat(a.totalSize.readableSize(), stringResource(R.string.apk_analyze_unpacked))
            Stat("${a.entryCount}", stringResource(R.string.apk_analyze_entries))
            Stat("$saved%", stringResource(R.string.apk_analyze_compressed))
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val MAX_DEPTH = 3
private const val MIN_SWEEP = 2f
private class Arc(val node: AnalyzeNode, val depth: Int, val start: Float, val sweep: Float)

private fun layout(focus: AnalyzeNode, into: MutableList<Arc>) {
    fun walk(node: AnalyzeNode, depth: Int, start: Float, sweep: Float) {
        if (depth > MAX_DEPTH || sweep < MIN_SWEEP) return
        if (depth > 0) into += Arc(node, depth, start, sweep)
        val total = node.size.coerceAtLeast(1)
        var cursor = start
        for (child in node.children) {
            val cs = sweep * (child.size.toFloat() / total)
            if (cs >= MIN_SWEEP) walk(child, depth + 1, cursor, cs)
            cursor += cs
        }
    }
    walk(focus, 0, 0f, 360f)
}

private fun sliceColor(hue: Int, depth: Int): Color {
    val base = APK_PALETTE[hue % APK_PALETTE.size]
    return base.copy(alpha = (1f - (depth - 1) * 0.18f).coerceIn(0.45f, 1f))
}

@Composable
private fun SunburstCard(a: ApkAnalysis, focusPath: List<AnalyzeNode>, onFocus: (List<AnalyzeNode>) -> Unit, onOpenLeaf: (AnalyzeNode) -> Unit) {
    val focus = focusPath.last()
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                focusPath.forEachIndexed { i, node ->
                    if (i > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onFocus(focusPath.take(i + 1)) }) {
                        Text(if (i == 0) stringResource(R.string.apk_analyze_all) else node.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val arcs = remember(focus) { ArrayList<Arc>().also { layout(focus, it) } }
            val surface = MaterialTheme.colorScheme.surface
            val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp), contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier.fillMaxSize().pointerInput(focus) {
                        detectTapGestures { tap ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dist = hypot(tap.x - center.x, tap.y - center.y)
                            val radius = min(size.width, size.height) / 2f
                            val ring = radius / (MAX_DEPTH + 1)
                            if (dist < ring) { if (focusPath.size > 1) onFocus(focusPath.dropLast(1)); return@detectTapGestures }
                            val depth = (dist / ring).toInt()
                            var ang = Math.toDegrees(atan2((tap.y - center.y).toDouble(), (tap.x - center.x).toDouble())).toFloat()
                            ang = ((ang + 90f) % 360f + 360f) % 360f
                            val hit = arcs.firstOrNull { it.depth == depth && ang >= it.start && ang < it.start + it.sweep }
                            if (hit != null) {
                                if (hit.node.childMap.isNotEmpty()) onFocus(focusPath + hit.node)
                                else if (hit.node.entryName != null) onOpenLeaf(hit.node)
                            }
                        }
                    },
                ) {
                    val radius = min(size.width, size.height) / 2f
                    val ring = radius / (MAX_DEPTH + 1)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(surfaceVariant, radius = ring * 0.9f, center = center)
                    arcs.forEach { arc ->
                        val midR = ring * arc.depth + ring / 2f
                        val gap = if (arc.sweep > 6f) 0.6f else 0f
                        drawArc(
                            color = sliceColor(arc.node.hue, arc.depth),
                            startAngle = arc.start - 90f + gap,
                            sweepAngle = (arc.sweep - gap * 2).coerceAtLeast(0.3f),
                            useCenter = false,
                            topLeft = Offset(center.x - midR, center.y - midR),
                            size = Size(midR * 2, midR * 2),
                            style = Stroke(width = ring * 0.92f),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(focus.size.readableSize(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (focusPath.size == 1) stringResource(R.string.apk_analyze_all) else focus.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            Text(stringResource(R.string.sunburst_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun CompositionBar(a: ApkAnalysis) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.apk_analyze_composition), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val total = a.totalSize.coerceAtLeast(1)
            Row(modifier = Modifier.fillMaxWidth().height(24.dp).clip(MaterialTheme.shapes.small)) {
                a.categories.forEach { c ->
                    Spacer(modifier = Modifier.weight((c.size.toFloat() / total).coerceAtLeast(0.001f)).fillMaxHeight().background(APK_PALETTE[c.colorIndex % APK_PALETTE.size]))
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(c: CategorySlice, total: Long, onClick: () -> Unit) {
    val pct = if (total > 0) (c.size * 100.0 / total).roundToInt() else 0
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(APK_PALETTE[c.colorIndex % APK_PALETTE.size]))
        Column(modifier = Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.apk_analyze_cat_meta, c.count, pct), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(c.size.readableSize(), style = MonoStyle, fontWeight = FontWeight.SemiBold)
    }
}
