package com.snatik.storage.app.explorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.FileOrder
import com.snatik.storage.Storage
import com.snatik.storage.StorageException
import com.snatik.storage.app.R
import com.snatik.storage.app.StorageApp
import com.snatik.storage.toReadableSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Volume(val labelRes: Int, val root: File, val free: Long, val total: Long)

data class Entry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeLabel: String,
    val lastModified: Long,
    val childCount: Int,
)

data class ExplorerState(
    val volumes: List<Volume> = emptyList(),
    val volume: Volume? = null,
    val directory: File? = null,
    val entries: List<Entry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val atVolumeList: Boolean get() = directory == null
    val atVolumeRoot: Boolean get() = directory != null && directory == volume?.root
}

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val storage: Storage = (application as StorageApp).storage

    private val _state = MutableStateFlow(ExplorerState())
    val state: StateFlow<ExplorerState> = _state

    init {
        refreshVolumes()
    }

    fun refreshVolumes() {
        viewModelScope.launch {
            val volumes = withContext(Dispatchers.IO) {
                buildList {
                    add(volume(R.string.volume_shared, storage.externalStorageDirectory))
                    add(volume(R.string.volume_app_files, storage.internalFilesDirectory))
                    add(volume(R.string.volume_app_cache, storage.internalCacheDirectory))
                    storage.externalFilesDirectory?.let { add(volume(R.string.volume_app_external, it)) }
                }
            }
            _state.update { it.copy(volumes = volumes) }
        }
    }

    private fun volume(labelRes: Int, root: File): Volume {
        val path = root.absolutePath
        val readable = root.exists()
        return Volume(
            labelRes = labelRes,
            root = root,
            free = if (readable) storage.freeSpace(path) else 0,
            total = if (readable) storage.totalSpace(path) else 0,
        )
    }

    fun openVolume(volume: Volume) {
        _state.update { it.copy(volume = volume) }
        open(volume.root)
    }

    fun open(directory: File) {
        _state.update { it.copy(directory = directory, loading = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                storage.listFiles(directory.absolutePath, order = FileOrder.DIRECTORIES_FIRST)
                    .map { files -> files.map(::entry) }
            }
            _state.update { current ->
                if (current.directory != directory) return@update current
                result.fold(
                    onSuccess = { entries -> current.copy(entries = entries, loading = false) },
                    onFailure = { e ->
                        current.copy(
                            entries = emptyList(),
                            loading = false,
                            error = (e as? StorageException)?.message ?: e.toString(),
                        )
                    },
                )
            }
        }
    }

    /** Go one level up. Returns false when already at the top so the caller can finish. */
    fun up(): Boolean {
        val current = _state.value
        val directory = current.directory ?: return false
        if (current.atVolumeRoot) {
            _state.update { it.copy(directory = null, entries = emptyList(), error = null, volume = null) }
            return true
        }
        val parent = directory.parentFile ?: return false
        open(parent)
        return true
    }

    fun refresh() {
        _state.value.directory?.let(::open) ?: refreshVolumes()
    }

    private fun entry(file: File): Entry {
        val isDirectory = file.isDirectory
        val childCount = if (isDirectory) file.list()?.size ?: -1 else 0
        return Entry(
            file = file,
            name = file.name,
            isDirectory = isDirectory,
            sizeLabel = if (isDirectory) "" else file.length().toReadableSize(),
            lastModified = file.lastModified(),
            childCount = childCount,
        )
    }
}
