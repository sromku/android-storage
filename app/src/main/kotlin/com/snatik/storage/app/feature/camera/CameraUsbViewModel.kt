package com.snatik.storage.app.feature.camera

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UsbStatus { IDLE, SCANNING, READY, IMPORTING }

data class UsbItem(val uri: Uri, val name: String, val size: Long, val isRaw: Boolean)

data class CameraUsbState(
    val status: UsbStatus = UsbStatus.IDLE,
    val items: List<UsbItem> = emptyList(),
    val selected: Set<Uri> = emptySet(),
    val importedDone: Int = 0,
    val importedTotal: Int = 0,
    val scanned: Int = 0,
)

/** Imports photos/videos from a USB device or SD card chosen via the Storage Access Framework. */
class CameraUsbViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(CameraUsbState())
    val state: StateFlow<CameraUsbState> = _state.asStateFlow()

    fun onTreePicked(treeUri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                treeUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModelScope.launch {
            _state.update { it.copy(status = UsbStatus.SCANNING, items = emptyList(), selected = emptySet(), scanned = 0) }
            val root = DocumentFile.fromTreeUri(getApplication(), treeUri)
            val found = withContext(Dispatchers.IO) { scan(root) }
            _state.update { it.copy(status = UsbStatus.READY, items = found) }
        }
    }

    private suspend fun scan(dir: DocumentFile?): List<UsbItem> {
        if (dir == null) return emptyList()
        val out = ArrayList<UsbItem>()
        val stack = ArrayDeque<DocumentFile>().apply { add(dir) }
        var guard = 0
        while (stack.isNotEmpty() && guard++ < 20_000) {
            val d = stack.removeFirst()
            for (child in runCatching { d.listFiles() }.getOrDefault(emptyArray())) {
                if (child.isDirectory) {
                    stack.add(child)
                } else {
                    val name = child.name ?: continue
                    if (isMedia(name)) {
                        out.add(UsbItem(child.uri, name, child.length(), isRawExt(name)))
                        if (out.size % 25 == 0) _state.update { it.copy(scanned = out.size) }
                    }
                }
            }
        }
        return out.sortedByDescending { it.name }
    }

    fun toggle(uri: Uri) = _state.update {
        it.copy(selected = if (uri in it.selected) it.selected - uri else it.selected + uri)
    }

    fun selectAll() = _state.update { it.copy(selected = it.items.map { i -> i.uri }.toSet()) }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun importSelected() {
        val targets = _state.value.items.filter { it.uri in _state.value.selected }
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(status = UsbStatus.IMPORTING, importedDone = 0, importedTotal = targets.size) }
            val resolver = getApplication<Application>().contentResolver
            for (t in targets) {
                runCatching {
                    val sink = CameraMediaStore.begin(getApplication(), t.name) ?: return@runCatching
                    resolver.openInputStream(t.uri)?.use { input ->
                        input.copyTo(sink.output)
                        sink.commit(t.size)
                    } ?: sink.abort()
                }
                _state.update { it.copy(importedDone = it.importedDone + 1) }
            }
            _state.update { it.copy(status = UsbStatus.READY, selected = emptySet()) }
        }
    }

    private companion object {
        val MEDIA_EXT = setOf("jpg", "jpeg", "heic", "heif", "png", "dng", "arw", "raw", "cr2", "cr3", "nef", "rw2", "orf", "raf", "mp4", "mov", "mts", "m2ts")
        val RAW_EXT = setOf("arw", "raw", "dng", "cr2", "cr3", "nef", "rw2", "orf", "raf")
        fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
        fun isMedia(name: String) = ext(name) in MEDIA_EXT
        fun isRawExt(name: String) = ext(name) in RAW_EXT
    }
}
