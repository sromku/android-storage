package com.snatik.storage.app.feature.disk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.fs.DirNode
import com.snatik.storage.core.fs.DiskScanner
import com.snatik.storage.core.fs.FileKind
import com.snatik.storage.core.fs.LargeFile
import com.snatik.storage.core.fs.ScanEvent
import com.snatik.storage.core.fs.ScanProgress
import com.snatik.storage.core.fs.TypeStat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiskView { TREE, TYPES, LARGEST }

data class DiskUsageUiState(
    val scanning: Boolean = true,
    val progress: ScanProgress? = null,
    val error: String? = null,
    val root: DirNode? = null,
    /** Path from the root to the node being shown. */
    val trail: List<DirNode> = emptyList(),
    val largest: List<LargeFile> = emptyList(),
    val types: List<TypeStat> = emptyList(),
    /** Every scanned file grouped by category, for the "By type" drill-down. */
    val filesByKind: Map<FileKind, List<LargeFile>> = emptyMap(),
    /** The category being drilled into on the "By type" tab, if any. */
    val selectedKind: FileKind? = null,
    val view: DiskView = DiskView.TREE,
) {
    val current: DirNode? get() = trail.lastOrNull() ?: root
    val selectedFiles: List<LargeFile> get() = selectedKind?.let { filesByKind[it] }.orEmpty()
}

class DiskUsageViewModel(private val path: String, private val scanner: DiskScanner) : ViewModel() {

    private val _state = MutableStateFlow(DiskUsageUiState())
    val state: StateFlow<DiskUsageUiState> = _state.asStateFlow()

    init {
        scan()
    }

    fun scan() {
        viewModelScope.launch {
            _state.update { DiskUsageUiState() }
            try {
                scanner.scan(path).collect { event ->
                    when (event) {
                        is ScanEvent.Progress -> _state.update { it.copy(progress = event.progress) }
                        is ScanEvent.Done -> _state.update { it.copy(scanning = false, root = event.root, trail = listOf(event.root), largest = event.largest, types = event.types, filesByKind = event.filesByKind) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(scanning = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun enter(node: DirNode) = _state.update { it.copy(trail = it.trail + node) }

    /** Go up one level. Returns false at the root so the caller can leave the screen. */
    fun up(): Boolean {
        val trail = _state.value.trail
        if (trail.size <= 1) return false
        _state.update { it.copy(trail = trail.dropLast(1)) }
        return true
    }

    fun jumpTo(index: Int) = _state.update { it.copy(trail = it.trail.take(index + 1)) }
    fun setView(view: DiskView) = _state.update { it.copy(view = view, selectedKind = null) }

    fun openKind(kind: FileKind) = _state.update { it.copy(selectedKind = kind) }
    fun closeKind() = _state.update { it.copy(selectedKind = null) }
}
