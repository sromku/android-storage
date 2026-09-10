package com.snatik.storage.app.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.StorageException
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.fs.FsEntry
import com.snatik.storage.core.fs.OperationRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface BrowserDialog {
    data object NewFolder : BrowserDialog
    data object NewFile : BrowserDialog
    data class Rename(val entry: FsEntry) : BrowserDialog
    data class ConfirmDelete(val paths: List<String>) : BrowserDialog
}

data class EntryDetails(
    val entry: FsEntry,
    val directorySize: Long? = null,
    val sha256: String? = null,
    val computingSize: Boolean = false,
    val computingHash: Boolean = false,
)

sealed interface BrowserMessage {
    data class Text(val text: String) : BrowserMessage
    data class Finished(val kind: OperationRunner.Kind, val count: Int) : BrowserMessage
    data class Failed(val error: Throwable) : BrowserMessage
    data class Cancelled(val kind: OperationRunner.Kind) : BrowserMessage
}

private val DB_EXTS = setOf("db", "sqlite", "sqlite3", "db3")

data class BrowserUiState(
    val entries: List<FsEntry> = emptyList(),
    val totalCount: Int = 0,
    val hiddenCount: Int = 0,
    val loading: Boolean = true,
    val error: StorageException? = null,
    val query: String = "",
    val searchActive: Boolean = false,
    val selected: Set<String> = emptySet(),
    val dialog: BrowserDialog? = null,
    val details: EntryDetails? = null,
    val preferences: ListingPreferences,
    val clipboard: FileClipboard.Content?,
    val running: OperationRunner.Running?,
    val encryptedDbs: Set<String> = emptySet(),
) {
    val selectionMode: Boolean get() = selected.isNotEmpty()
    val allSelected: Boolean get() = entries.isNotEmpty() && entries.all { it.path in selected }
}

class BrowserViewModel(
    val route: Route.Browser,
    private val fs: FileSystem,
    private val runner: OperationRunner,
    private val clipboard: FileClipboard,
    private val preferences: BrowserPreferences,
) : ViewModel() {

    private data class Local(
        val raw: List<FsEntry> = emptyList(),
        val loading: Boolean = true,
        val error: StorageException? = null,
        val query: String = "",
        val searchActive: Boolean = false,
        val selected: Set<String> = emptySet(),
        val dialog: BrowserDialog? = null,
        val details: EntryDetails? = null,
        val encryptedDbs: Set<String> = emptySet(),
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<BrowserUiState> = combine(local, preferences.state, clipboard.content, runner.running) { l, prefs, clip, running ->
        val visible = l.raw.asSequence()
            .filter { prefs.showHidden || !it.isHidden }
            .filter { l.query.isBlank() || it.name.contains(l.query, ignoreCase = true) }
            .sortedWith(prefs.sort.comparator())
            .toList()
        BrowserUiState(
            entries = visible,
            totalCount = l.raw.size,
            hiddenCount = l.raw.count { it.isHidden },
            loading = l.loading,
            error = l.error,
            query = l.query,
            searchActive = l.searchActive,
            selected = l.selected.filterTo(HashSet()) { path -> visible.any { it.path == path } },
            dialog = l.dialog,
            details = l.details,
            preferences = prefs,
            clipboard = clip,
            running = running,
            encryptedDbs = l.encryptedDbs,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BrowserUiState(preferences = preferences.state.value, clipboard = clipboard.content.value, running = runner.running.value),
    )

    private val _messages = Channel<BrowserMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var detailsJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            runner.events.collect { event ->
                when (event) {
                    is OperationRunner.Event.Finished -> _messages.send(BrowserMessage.Finished(event.kind, event.itemCount))
                    is OperationRunner.Event.Failed -> _messages.send(BrowserMessage.Failed(event.error))
                    is OperationRunner.Event.Cancelled -> _messages.send(BrowserMessage.Cancelled(event.kind))
                }
                load()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            local.update { it.copy(loading = true, error = null) }
            try {
                val entries = fs.list(route.path)
                local.update { it.copy(raw = entries, loading = false, encryptedDbs = emptySet()) }
                probeEncryptedDbs(entries)
            } catch (e: StorageException) {
                local.update { it.copy(raw = emptyList(), loading = false, error = e) }
            }
        }
    }

    /** Flag SQLite-extension files whose header is not the SQLite magic (encrypted / SQLCipher). */
    private fun probeEncryptedDbs(entries: List<FsEntry>) {
        val dbLike = entries.filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in DB_EXTS }
        if (dbLike.isEmpty()) return
        viewModelScope.launch {
            val enc = HashSet<String>()
            for (e in dbLike) {
                val h = runCatching { fs.readBytes(e.path, 0, 16) }.getOrNull()
                if (h != null && h.size >= 16 && !String(h, Charsets.US_ASCII).startsWith("SQLite format 3")) enc += e.path
            }
            if (enc.isNotEmpty()) local.update { if (it.raw === entries) it.copy(encryptedDbs = enc) else it }
        }
    }

    // Search, sort, hidden

    fun setSearchActive(active: Boolean) = local.update { it.copy(searchActive = active, query = if (active) it.query else "") }
    fun setQuery(query: String) = local.update { it.copy(query = query) }
    fun setSort(field: SortField, ascending: Boolean) = preferences.setSort(field, ascending)
    fun setShowHidden(show: Boolean) = preferences.setShowHidden(show)

    // Selection

    fun toggleSelected(path: String) = local.update {
        it.copy(selected = if (path in it.selected) it.selected - path else it.selected + path)
    }

    fun selectAll() = local.update { current ->
        current.copy(selected = state.value.entries.map { it.path }.toSet())
    }

    fun clearSelection() = local.update { it.copy(selected = emptySet()) }

    // Dialogs

    fun showDialog(dialog: BrowserDialog) = local.update { it.copy(dialog = dialog) }
    fun dismissDialog() = local.update { it.copy(dialog = null) }

    fun validateName(name: String, current: String? = null): NameError? = when {
        name.isBlank() -> null
        name.contains('/') || name == "." || name == ".." -> NameError.INVALID
        name != current && state.value.entries.any { it.name == name } -> NameError.EXISTS
        else -> null
    }

    fun createFolder(name: String) = mutate { fs.createDirectory(File(route.path, name).absolutePath) }
    fun createFile(name: String) = mutate { fs.createFile(File(route.path, name).absolutePath) }
    fun rename(entry: FsEntry, newName: String) = mutate { fs.rename(entry.path, newName) }

    private fun mutate(block: suspend () -> Unit) {
        dismissDialog()
        viewModelScope.launch {
            try {
                block()
                load()
            } catch (e: Exception) {
                _messages.send(BrowserMessage.Failed(e))
            }
        }
    }

    // Clipboard and operations

    fun copyToClipboard(paths: Collection<String>) {
        clipboard.set(paths.toList(), FileClipboard.Mode.COPY)
        clearSelection()
    }

    fun cutToClipboard(paths: Collection<String>) {
        clipboard.set(paths.toList(), FileClipboard.Mode.MOVE)
        clearSelection()
    }

    fun cancelClipboard() = clipboard.clear()

    fun paste() {
        val content = clipboard.content.value ?: return
        val flow = when (content.mode) {
            FileClipboard.Mode.COPY -> fs.copy(content.paths, route.path)
            FileClipboard.Mode.MOVE -> fs.move(content.paths, route.path)
        }
        val kind = if (content.mode == FileClipboard.Mode.COPY) OperationRunner.Kind.COPY else OperationRunner.Kind.MOVE
        if (runner.start(kind, content.paths.size, flow)) clipboard.clear()
    }

    fun requestDelete(paths: Collection<String>) = showDialog(BrowserDialog.ConfirmDelete(paths.toList()))

    fun confirmDelete(paths: List<String>) {
        dismissDialog()
        clearSelection()
        runner.start(OperationRunner.Kind.DELETE, paths.size, fs.delete(paths))
    }

    fun cancelOperation() = runner.cancel()

    // Details

    fun showDetails(entry: FsEntry) {
        detailsJob?.cancel()
        local.update { it.copy(details = EntryDetails(entry, computingSize = entry.isDirectory)) }
        if (entry.isDirectory) {
            detailsJob = viewModelScope.launch {
                val size = runCatching { fs.directorySize(entry.path) }.getOrNull()
                local.update { it.copy(details = it.details?.takeIf { d -> d.entry.path == entry.path }?.copy(directorySize = size, computingSize = false)) }
            }
        }
    }

    fun computeHash() {
        val details = local.value.details ?: return
        if (details.entry.isDirectory || details.sha256 != null) return
        local.update { it.copy(details = details.copy(computingHash = true)) }
        detailsJob = viewModelScope.launch {
            val hash = runCatching { fs.sha256(details.entry.path) }.getOrNull()
            local.update { it.copy(details = it.details?.takeIf { d -> d.entry.path == details.entry.path }?.copy(sha256 = hash, computingHash = false)) }
        }
    }

    fun dismissDetails() {
        detailsJob?.cancel()
        local.update { it.copy(details = null) }
    }
}

enum class NameError { INVALID, EXISTS }
