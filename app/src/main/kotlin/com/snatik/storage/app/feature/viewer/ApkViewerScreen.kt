package com.snatik.storage.app.feature.viewer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.highlight.HlLanguage
import com.snatik.storage.app.ui.components.CodeView
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.apps.ManifestDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ApkEntry(val name: String, val size: Long, val compressed: Long, val stored: Boolean) {
    val ratio: Int get() = if (size > 0) (100 - compressed * 100 / size).toInt() else 0
}

data class ApkDetails(
    val label: String, val packageName: String, val versionName: String, val versionCode: Long,
    val minSdk: Int, val targetSdk: Int, val compileSdk: Int, val size: Long,
    val permissions: List<String>, val features: List<String>,
    val activities: Int, val services: Int, val receivers: Int, val providers: Int,
    val signatures: List<String>, val schemes: List<String>,
    val entries: List<ApkEntry>, val totalUncompressed: Long,
    val dexFiles: List<ApkEntry>, val abis: List<String>, val arscSize: Long,
    val resCount: Int, val assetCount: Int, val manifestXml: String?,
)

enum class ApkTab { OVERVIEW, MANIFEST, RESOURCES, CONTENTS, SIGNING }

class ApkViewModel(private val path: String, private val context: Context, private val manifests: ManifestDecoder) : ViewModel() {
    val name = path.substringAfterLast('/')
    private val _state = MutableStateFlow<ApkDetails?>(null)
    val state: StateFlow<ApkDetails?> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _state.value = withContext(Dispatchers.IO) { parse() }
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun parse(): ApkDetails {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_SIGNING_CERTIFICATES or
            PackageManager.GET_CONFIGURATIONS
        val info: PackageInfo = pm.getPackageArchiveInfo(path, flags) ?: error("Not a valid APK")
        val app = info.applicationInfo!!.apply { sourceDir = path; publicSourceDir = path }
        val signatures = info.signingInfo?.let { s -> (if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory).orEmpty() }
            ?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString(":") { b -> "%02X".format(b) } } ?: emptyList()

        // Read the zip's own table of contents.
        val entries = ArrayList<ApkEntry>()
        val abis = sortedSetOf<String>()
        var resCount = 0; var assetCount = 0; var arscSize = 0L
        var v1 = false
        runCatching {
            ZipFile(path).use { zip ->
                val en = zip.entries()
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    if (e.isDirectory) continue
                    entries += ApkEntry(e.name, e.size.coerceAtLeast(0), e.compressedSize.coerceAtLeast(0), e.method == ZipEntry.STORED)
                    val n = e.name
                    when {
                        n.startsWith("lib/") -> n.substringAfter("lib/").substringBefore('/').takeIf { it.isNotEmpty() }?.let { abis += it }
                        n.startsWith("res/") -> resCount++
                        n.startsWith("assets/") -> assetCount++
                        n == "resources.arsc" -> arscSize = e.size
                    }
                    if (n.startsWith("META-INF/") && (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"))) v1 = true
                }
            }
        }
        entries.sortByDescending { it.size }
        val dexFiles = entries.filter { it.name.matches(Regex("classes\\d*\\.dex")) }.sortedBy { it.name }

        val schemes = ArrayList<String>()
        if (v1) schemes += "v1 (JAR)"
        schemes += detectBlockSchemes(path)

        val manifestXml = runCatching { manifests.decode(info.packageName, path) }.getOrNull()

        return ApkDetails(
            label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(info.packageName),
            packageName = info.packageName,
            versionName = info.versionName ?: "",
            versionCode = info.longVersionCode,
            minSdk = app.minSdkVersion,
            targetSdk = app.targetSdkVersion,
            compileSdk = if (android.os.Build.VERSION.SDK_INT >= 31) runCatching { app.compileSdkVersion }.getOrDefault(0) else 0,
            size = File(path).length(),
            permissions = info.requestedPermissions?.toList().orEmpty(),
            features = info.reqFeatures?.mapNotNull { it.name }.orEmpty(),
            activities = info.activities?.size ?: 0,
            services = info.services?.size ?: 0,
            receivers = info.receivers?.size ?: 0,
            providers = info.providers?.size ?: 0,
            signatures = signatures,
            schemes = schemes,
            entries = entries,
            totalUncompressed = entries.sumOf { it.size },
            dexFiles = dexFiles,
            abis = abis.toList(),
            arscSize = arscSize,
            resCount = resCount,
            assetCount = assetCount,
            manifestXml = manifestXml,
        )
    }

    /**
     * Make one zip entry openable in another viewer and return its file path. Compiled binary-XML
     * (res/ layouts, drawables, the manifest) is decoded to text first so the XML viewer can read
     * it; a raw-text .xml (res/raw, assets) falls back to a plain copy. Everything else is copied
     * as-is.
     */
    suspend fun openEntry(entryName: String): String? = withContext(Dispatchers.IO) {
        if (entryName.endsWith(".xml", ignoreCase = true)) {
            val pkg = _state.value?.packageName ?: ""
            val decoded = runCatching { manifests.decodeEntry(pkg, path, entryName) }.getOrNull()
            if (!decoded.isNullOrBlank()) return@withContext writeCache(entryName, decoded.toByteArray())
        }
        extract(entryName)
    }

    /** Extract one zip entry to the cache and return its file path. */
    suspend fun extract(entryName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            ZipFile(path).use { zip ->
                val e = zip.getEntry(entryName) ?: return@use null
                zip.getInputStream(e).use { writeCache(entryName, it.readBytes()) }
            }
        }.getOrNull()
    }

    private fun writeCache(entryName: String, bytes: ByteArray): String {
        val outDir = File(context.cacheDir, "apk-extract").apply { mkdirs() }
        val out = File(outDir, entryName.replace('/', '_').takeLast(120))
        out.writeBytes(bytes)
        return out.absolutePath
    }

    /** Detect APK Signing Block schemes (v2/v3/v3.1) by locating the block before the central directory. */
    private fun detectBlockSchemes(path: String): List<String> = runCatching {
        RandomAccessFile(path, "r").use { raf ->
            val len = raf.length()
            val tail = minOf(len, 65_536L + 22).toInt()
            val buf = ByteArray(tail)
            raf.seek(len - tail); raf.readFully(buf)
            // find End Of Central Directory signature 0x06054b50, scanning backward
            var eocd = -1
            for (i in buf.size - 22 downTo 0) {
                if (buf[i] == 0x50.toByte() && buf[i+1] == 0x4b.toByte() && buf[i+2] == 0x05.toByte() && buf[i+3] == 0x06.toByte()) { eocd = i; break }
            }
            if (eocd < 0) return@runCatching emptyList()
            val cdOffset = readLE32(buf, eocd + 16)
            // magic "APK Sig Block 42" sits at cdOffset-16; block size (u64) at cdOffset-24
            if (cdOffset < 24) return@runCatching emptyList()
            val magic = ByteArray(16); raf.seek(cdOffset - 16); raf.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != "APK Sig Block 42") return@runCatching emptyList()
            raf.seek(cdOffset - 24)
            val sizeBytes = ByteArray(8); raf.readFully(sizeBytes)
            val blockSize = readLE64(sizeBytes, 0)
            if (blockSize <= 0 || blockSize > 200_000_000) return@runCatching listOf("v2/v3 (APK Signing Block)")
            val blockStart = cdOffset - 8 - blockSize
            val block = ByteArray(blockSize.toInt())
            raf.seek(blockStart); raf.readFully(block)
            val out = ArrayList<String>()
            // walk id-value pairs: [u64 len][u32 id][value...]
            var p = 8
            while (p + 12 <= block.size) {
                val pairLen = readLE64(block, p); p += 8
                if (pairLen < 4 || p + 4 > block.size) break
                when (readLE32I(block, p)) {
                    0x7109871a -> out += "v2"
                    0xf05368c0.toInt() -> out += "v3"
                    0x1b93ad61 -> out += "v3.1"
                }
                p += pairLen.toInt()
            }
            if (out.isEmpty()) listOf("v2/v3 (APK Signing Block)") else out.map { "$it (APK Signing Block)" }
        }
    }.getOrDefault(emptyList())

    private fun readLE32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o+1].toLong() and 0xFF) shl 8) or ((b[o+2].toLong() and 0xFF) shl 16) or ((b[o+3].toLong() and 0xFF) shl 24)
    private fun readLE32I(b: ByteArray, o: Int): Int = readLE32(b, o).toInt()
    private fun readLE64(b: ByteArray, o: Int): Long {
        var v = 0L; for (i in 7 downTo 0) v = (v shl 8) or (b[o + i].toLong() and 0xFF); return v
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkViewerScreen(path: String, onBack: () -> Unit, onOpenPath: (String) -> Unit = {}, onDecompile: () -> Unit = {}, viewModel: ApkViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val apk by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ApkTab.OVERVIEW) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val open: (String) -> Unit = { name -> scope.launch { viewModel.openEntry(name)?.let(onOpenPath) } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { androidx.compose.material3.TextButton(onClick = onDecompile) { Text(stringResource(R.string.apk_decompile)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.apk_invalid), error)
                apk == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> {
                    val a = apk!!
                    Column(modifier = Modifier.fillMaxSize()) {
                        PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp) {
                            ApkTab.entries.forEach { t ->
                                val label = when (t) {
                                    ApkTab.OVERVIEW -> R.string.tab_overview
                                    ApkTab.MANIFEST -> R.string.tab_manifest
                                    ApkTab.RESOURCES -> R.string.apk_resources_tab
                                    ApkTab.CONTENTS -> R.string.apk_contents
                                    ApkTab.SIGNING -> R.string.apk_signing
                                }
                                Tab(selected = tab == t, onClick = { tab = t }, text = { Text(stringResource(label)) })
                            }
                        }
                        when (tab) {
                            ApkTab.OVERVIEW -> OverviewTab(a)
                            ApkTab.MANIFEST -> ManifestTab(a)
                            ApkTab.RESOURCES -> ResourcesTab(a, open)
                            ApkTab.CONTENTS -> ContentsTab(a, open)
                            ApkTab.SIGNING -> SigningTab(a)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(a: ApkDetails) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(a.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                SelectionContainer { Text(a.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${a.versionName} (${a.versionCode}) · ${a.size.readableSize()} on disk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
        item {
            val sdk = stringResource(R.string.info_sdk_value, a.targetSdk, a.minSdk) + if (a.compileSdk > 0) " · compile ${a.compileSdk}" else ""
            InfoLine(stringResource(R.string.info_sdk), sdk)
        }
        item { InfoLine(stringResource(R.string.apk_components), "${a.activities} act · ${a.services} svc · ${a.receivers} rcv · ${a.providers} prov") }
        item { InfoLine(stringResource(R.string.apk_uncompressed), "${a.totalUncompressed.readableSize()} · ${a.entries.size} entries") }
        item {
            Column(modifier = Modifier.padding(16.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.apk_content_summary), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                StatRow(stringResource(R.string.apk_dex), if (a.dexFiles.isEmpty()) "—" else a.dexFiles.joinToString(", ") { "${it.name} (${it.size.readableSize()})" })
                StatRow(stringResource(R.string.apk_abis), if (a.abis.isEmpty()) stringResource(R.string.apk_no_native) else a.abis.joinToString(", "))
                StatRow(stringResource(R.string.apk_resources), "${a.arscSize.readableSize()} arsc · ${a.resCount} res · ${a.assetCount} assets")
            }
        }
        if (a.features.isNotEmpty()) {
            item { Text(stringResource(R.string.apk_features, a.features.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
            items(a.features.size) { i -> Text(a.features[i], style = MonoStyle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
        }
        item { Text(stringResource(R.string.tab_permissions) + " · " + a.permissions.size, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
        items(a.permissions.size) { i -> Text(a.permissions[i], style = MonoStyle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
    }
}

@Composable
private fun ManifestTab(a: ApkDetails) {
    var query by remember { mutableStateOf("") }
    val xml = a.manifestXml
    Column(modifier = Modifier.fillMaxSize()) {
        if (xml == null) {
            EmptyState(Icons.Default.Block, stringResource(R.string.cannot_read_file), null)
            return
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.manifest_search)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val allLines = remember(xml) { xml.lines() }
        val q = query.trim()
        val (lines, numbers) = remember(allLines, q) {
            if (q.isEmpty()) allLines to null
            else allLines.withIndex().filter { it.value.contains(q, ignoreCase = true) }.let { hits -> hits.map { it.value } to hits.map { it.index + 1 } }
        }
        CodeView(lines = lines, wrap = false, lineNumbers = numbers, language = HlLanguage.XML, highlight = q.ifEmpty { null }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ContentsTab(a: ApkDetails, onOpen: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item { Text(stringResource(R.string.apk_entries_sorted, a.entries.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 4.dp)) }
        items(a.entries.size) { i -> EntryRow(a.entries[i], onOpen) }
    }
}

/** Whether tapping an entry can open it in another viewer. Compiled AXML/arsc are not openable as text. */
// Every entry can be opened: compiled XML is decoded to text, images/.so/text get their own
// viewer, and anything else (arsc, dex, unknown) falls back to the hex viewer.
private fun openable(name: String): Boolean = true

@Composable
private fun EntryRow(e: ApkEntry, onOpen: (String) -> Unit) {
    val canOpen = openable(e.name)
    val base = Modifier.fillMaxWidth()
    Row(
        modifier = (if (canOpen) base.then(Modifier.clickable { onOpen(e.name) }) else base).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(e.name, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (canOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(
                if (e.stored) stringResource(R.string.apk_stored) else stringResource(R.string.apk_deflated, e.ratio),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(e.size.readableSize(), style = MonoStyle, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider()
}

@Composable
private fun ResourcesTab(a: ApkDetails, onOpen: (String) -> Unit) {
    // Group res/ and assets/ entries by their type folder (density/qualifier stripped).
    val groups = remember(a.entries) {
        val res = a.entries.filter { it.name.startsWith("res/") }
            .groupBy { it.name.removePrefix("res/").substringBefore('/').substringBefore('-').ifEmpty { "root" } }
        val assets = a.entries.filter { it.name.startsWith("assets/") }
        val merged = LinkedHashMap<String, List<ApkEntry>>()
        (res.entries.sortedByDescending { it.value.size }).forEach { merged[it.key] = it.value }
        if (assets.isNotEmpty()) merged["assets"] = assets
        merged
    }
    if (groups.isEmpty()) { EmptyState(Icons.Default.Block, stringResource(R.string.apk_no_resources), stringResource(R.string.apk_res_note)); return }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(stringResource(R.string.apk_res_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 8.dp)) }
        groups.forEach { (type, entries) ->
            item(key = "h-$type") {
                val isOpen = expanded[type] == true
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expanded[type] = !isOpen }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(if (isOpen) "▾" else "▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(type, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${entries.size} · ${entries.sumOf { it.size }.readableSize()}", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
            if (expanded[type] == true) {
                items(entries.size, key = { "e-$type-$it" }) { i ->
                    val e = entries[i]
                    val short = e.name.substringAfterLast('/')
                    val canOpen = openable(e.name)
                    Row(
                        modifier = Modifier.fillMaxWidth().let { if (canOpen) it.clickable { onOpen(e.name) } else it }.padding(start = 34.dp, end = 16.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(short, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (canOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Text(e.size.readableSize(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SigningTab(a: ApkDetails) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (a.schemes.isEmpty()) Text(stringResource(R.string.apk_unsigned), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                else a.schemes.forEach { sch -> Tag(sch, MaterialTheme.colorScheme.primary) }
            }
        }
        if (a.schemes.isNotEmpty()) item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.apk_signed_by, a.signatures.size), style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(a.signatures.size) { i ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.apk_cert_n, i + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer { Text(a.signatures[i], style = MonoStyle) }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
        SelectionContainer(modifier = Modifier.weight(1f)) { Text(value, style = MonoStyle) }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
        Text(value, style = MonoStyle, modifier = Modifier.weight(1f))
    }
}
