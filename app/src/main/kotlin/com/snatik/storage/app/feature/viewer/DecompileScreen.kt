package com.snatik.storage.app.feature.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
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

data class DecompileState(
    val loading: Boolean = true,
    val phase: String = "",
    val error: String? = null,
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
                val list = withContext(Dispatchers.IO) {
                    _state.value = _state.value.copy(phase = "Preparing dex")
                    val inputs = prepareInputs()
                    _state.value = _state.value.copy(phase = "Loading classes")
                    val args = JadxArgs().apply {
                        inputFiles.addAll(inputs)
                        security = AndroidJadxSecurity()          // Android-safe XML parsing
                        setSkipResources(true)                    // dex only — smaller footprint
                        isShowInconsistentCode = true
                        threadsCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                    }
                    val jadx = JadxDecompiler(args)
                    jadx.load()
                    decompiler = jadx
                    jadx.classes.sortedBy { it.fullName }
                }
                classes = list
                _state.value = DecompileState(loading = false, classNames = list.map { it.fullName })
            } catch (e: Throwable) {
                android.util.Log.e("Decompile", "jadx load failed", e)
                val cause = generateSequence(e as Throwable?) { it.cause }.joinToString(" <- ") { it.javaClass.simpleName + ": " + (it.message ?: "") }
                _state.value = DecompileState(loading = false, error = cause.take(400))
            }
        }
    }

    /** Extract just the classes*.dex from the APK (or use the .dex directly), after checking space. */
    private fun prepareInputs(): List<File> {
        sweepJadxCache()  // clear any leftovers from a previous, killed run
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

    fun back() { _state.value = _state.value.copy(selected = null, code = null) }

    override fun onCleared() {
        runCatching { decompiler?.close() }   // release jadx file handles first
        runCatching { tempDir?.deleteRecursively() }
        sweepJadxCache()
    }

    /** Remove our extracted dex plus jadx's own jadx-instance-* / jadx-temp-* working dirs. */
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selected?.substringAfterLast('.') ?: viewModel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (state.selected != null) viewModel.back() else onBack() }) {
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
                    if (state.decompiling || state.code == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        val lines = remember(state.code) { state.code!!.lines() }
                        CodeView(lines = lines, wrap = false, language = HlLanguage.JAVA, modifier = Modifier.fillMaxSize())
                    }
                }
                else -> ClassList(state.classNames, query, onQuery = { query = it }, onOpen = viewModel::open)
            }
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
