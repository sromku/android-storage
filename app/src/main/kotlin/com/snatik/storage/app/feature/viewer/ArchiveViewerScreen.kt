package com.snatik.storage.app.feature.viewer

import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.Intents
import com.snatik.storage.app.util.readableSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.util.zip.ZipFile

data class ArchiveEntry(val name: String, val size: Long, val compressed: Long, val isDirectory: Boolean)
data class ArchiveState(val entries: List<ArchiveEntry> = emptyList(), val total: Long = 0, val loading: Boolean = true, val error: String? = null)

class ArchiveViewModel(private val path: String, private val context: Context) : ViewModel() {
    val name = path.substringAfterLast('/')
    private val _state = MutableStateFlow(ArchiveState())
    val state: StateFlow<ArchiveState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    ZipFile(path).use { zip -> zip.entries().toList().map { ArchiveEntry(it.name, it.size, it.compressedSize, it.isDirectory) }.sortedBy { it.name } }
                }
                _state.update { it.copy(entries = entries, total = entries.sumOf { e -> e.size.coerceAtLeast(0) }, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun extractAndOpen(entry: ArchiveEntry) {
        if (entry.isDirectory) return
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) {
                runCatching {
                    val out = File(context.cacheDir, "archive/${entry.name.substringAfterLast('/')}")
                    out.parentFile?.mkdirs()
                    ZipFile(path).use { zip ->
                        val zipEntry = zip.getEntry(entry.name) ?: return@use null
                        zip.getInputStream(zipEntry).use { input -> out.outputStream().use { input.copyTo(it) } }
                        out
                    }
                }.getOrNull()
            }
            if (target != null) {
                Intents.openWith(context, target.absolutePath)
            } else {
                android.widget.Toast.makeText(context, R.string.cannot_open_entry, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(path: String, onBack: () -> Unit, viewModel: ArchiveViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(viewModel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!state.loading && state.error == null) Text(stringResource(R.string.archive_summary, state.entries.count { !it.isDirectory }, state.total.readableSize()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.archive_invalid), state.error)
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.entries, key = { it.name }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !entry.isDirectory) { viewModel.extractAndOpen(entry) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entry.name, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                            if (!entry.isDirectory) Text(entry.size.readableSize(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
