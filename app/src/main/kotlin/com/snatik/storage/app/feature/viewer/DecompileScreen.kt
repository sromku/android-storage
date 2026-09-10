package com.snatik.storage.app.feature.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.CodeView
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.highlight.HlLanguage
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.JavaClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

/** One dex file inside the APK the user can choose to decompile. */
data class DexInfo(val file: File, val name: String, val size: Long)

data class DecompileState(
    val loading: Boolean = true,
    val phase: String = "",
    val error: String? = null,
    val dexes: List<DexInfo>? = null,   // shown when the APK has more than one dex
    val selectedDex: String? = null,
    val classNames: List<String> = emptyList(),
    val selected: String? = null,
    val code: String? = null,
    val decompiling: Boolean = false,
)

class DecompileViewModel(private val path: String, private val context: android.content.Context) : ViewModel() {
    val name = path.substringAfterLast('/')
    private val _state = MutableStateFlow(DecompileState())
    val state: StateFlow<DecompileState> = _state.asStateFlow()

    private var decompiler: JadxDecompiler? = null
    private var classes: List<JavaClass> = emptyList()
    private var tempDir: File? = null

    init {
        viewModelScope.launch {
            try {
                val inputs = withContext(Dispatchers.IO) {
                    _state.value = _state.value.copy(phase = "Preparing dex")
                    prepareInputs()
                }
                // One dex → load straight away; several → let the user pick one to bound memory.
                if (inputs.size == 1) {
                    loadDex(inputs[0])
                } else {
                    _state.value = DecompileState(loading = false, dexes = inputs.map { DexInfo(it, it.name, it.length()) }.sortedByDescending { it.size })
                }
            } catch (e: Throwable) {
                _state.value = DecompileState(loading = false, error = friendly(e))
            }
        }
    }

    fun chooseDex(info: DexInfo) {
        viewModelScope.launch { loadDex(info.file) }
    }

    private suspend fun loadDex(dex: File) {
        _state.value = _state.value.copy(loading = true, phase = "Loading ${dex.name}", selectedDex = dex.name, classNames = emptyList(), selected = null, code = null, error = null)
        try {
            val list = withContext(Dispatchers.IO) {
                runCatching { decompiler?.close() }
                // Point jadx at a temp root we control and (re)create, so its process-wide cache is
                // never left pointing at a directory our cleanup deleted.
                val tmp = File(context.cacheDir, "jadxtmp").apply { mkdirs() }
                runCatching { jadx.core.utils.files.FileUtils.updateTempRootDir(tmp.toPath()) }
                val args = JadxArgs().apply {
                    inputFiles.add(dex)
                    security = AndroidJadxSecurity()
                    setSkipResources(true)
                    isShowInconsistentCode = true
                    threadsCount = 1               // one dex at a time; keep the footprint small
                }
                val jadx = JadxDecompiler(args)
                jadx.load()
                decompiler = jadx
                jadx.classes.sortedBy { it.fullName }
            }
            classes = list
            _state.value = _state.value.copy(loading = false, classNames = list.map { it.fullName })
        } catch (e: Throwable) {
            android.util.Log.e("Decompile", "jadx load failed", e)
            _state.value = _state.value.copy(loading = false, error = friendly(e))
        }
    }

    /** Extract just the classes*.dex from the APK (or use the .dex directly), after checking space. */
    private fun prepareInputs(): List<File> {
        sweepJadxCache()
        val f = File(path)
        if (path.endsWith(".dex", ignoreCase = true)) return listOf(f)
        val dir = File(context.cacheDir, "jadx").apply { deleteRecursively(); mkdirs() }
        tempDir = dir
        val out = ArrayList<File>()
        java.util.zip.ZipFile(f).use { zip ->
            val dexes = zip.entries().asSequence().filter { it.name.matches(Regex("classes\\d*\\.dex")) }.toList()
            val needed = dexes.sumOf { it.size.coerceAtLeast(0) }
            if (dir.usableSpace < needed + 16L * 1024 * 1024) {
                throw java.io.IOException("Not enough free space to decompile (need ~${needed / (1024 * 1024)} MB)")
            }
            dexes.forEach { e ->
                val dest = File(dir, e.name)
                zip.getInputStream(e).use { input -> dest.outputStream().use { input.copyTo(it) } }
                out += dest
            }
        }
        if (out.isEmpty()) throw java.io.IOException("No dex code found in this file")
        return out
    }

    fun open(className: String) {
        _state.value = _state.value.copy(selected = className, decompiling = true, code = null)
        viewModelScope.launch {
            val code = withContext(Dispatchers.IO) {
                runCatching { classes.firstOrNull { it.fullName == className }?.code }.getOrNull()
            }
            _state.value = _state.value.copy(decompiling = false, code = code ?: "// could not decompile")
        }
    }

    /** Internal back: code → class list → dex picker. Returns false when nothing left to pop. */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.selected != null -> { _state.value = s.copy(selected = null, code = null); true }
            s.selectedDex != null && (s.dexes?.size ?: 0) > 1 -> {
                runCatching { decompiler?.close() }
                classes = emptyList()
                _state.value = s.copy(selectedDex = null, classNames = emptyList())
                true
            }
            else -> false
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is OutOfMemoryError -> "This dex is too large to decompile in the app's memory. Try a smaller dex, or use jadx on a computer for this one."
        else -> generateSequence(e as Throwable?) { it.cause }.joinToString(" <- ") { it.javaClass.simpleName + ": " + (it.message ?: "") }.take(400)
    }

    override fun onCleared() {
        runCatching { decompiler?.close() }
        runCatching { tempDir?.deleteRecursively() }
        sweepJadxCache()
    }

    private fun sweepJadxCache() {
        runCatching {
            context.cacheDir.listFiles { file -> file.name.startsWith("jadx") }?.forEach { it.deleteRecursively() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecompileScreen(path: String, onBack: () -> Unit, viewModel: DecompileViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    // Let system back step class -> class list -> dex picker before leaving the screen.
    val canGoBack = state.selected != null || (state.selectedDex != null && (state.dexes?.size ?: 0) > 1)
    androidx.activity.compose.BackHandler(enabled = canGoBack) { viewModel.back() }

    val title = when {
        state.selected != null -> state.selected!!.substringAfterLast('.')
        state.selectedDex != null && (state.dexes?.size ?: 0) > 1 -> state.selectedDex!!
        else -> viewModel.name
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.back()) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(state.phase.ifEmpty { stringResource(R.string.decompile_loading) }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                }
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.decompile_failed), state.error)
                state.selected != null -> {
                    if (state.decompiling || state.code == null) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    else {
                        val lines = remember(state.code) { state.code!!.lines() }
                        CodeView(lines = lines, wrap = false, language = HlLanguage.JAVA, modifier = Modifier.fillMaxSize())
                    }
                }
                state.dexes != null && state.selectedDex == null -> DexPicker(state.dexes!!, viewModel::chooseDex)
                else -> ClassList(state.classNames, query, onQuery = { query = it }, onOpen = viewModel::open)
            }
        }
    }
}

@Composable
private fun DexPicker(dexes: List<DexInfo>, onChoose: (DexInfo) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(stringResource(R.string.decompile_pick_dex, dexes.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        items(dexes.size) { i ->
            val d = dexes[i]
            Row(modifier = Modifier.fillMaxWidth().clickable { onChoose(d) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.DataObject, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(d.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(d.size.readableSize(), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ClassList(all: List<String>, query: String, onQuery: (String) -> Unit, onOpen: (String) -> Unit) {
    val filtered = remember(all, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all else all.filter { it.lowercase().contains(q) }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = onQuery,
            placeholder = { Text(stringResource(R.string.decompile_search, all.size)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered.size) { i ->
                val cls = filtered[i]
                Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(cls) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(cls.substringAfterLast('.'), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SelectionContainer { Text(cls.substringBeforeLast('.', ""), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                HorizontalDivider()
            }
        }
    }
}
