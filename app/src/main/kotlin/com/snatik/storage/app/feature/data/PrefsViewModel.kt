package com.snatik.storage.app.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.data.PrefEntry
import com.snatik.storage.core.data.PrefType
import com.snatik.storage.core.data.SharedPrefsFile
import com.snatik.storage.core.fs.FileSystem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrefsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val entries: List<PrefEntry> = emptyList(),
    val dirty: Boolean = false,
    val editing: PrefEntry? = null,
    val adding: Boolean = false,
)

class PrefsViewModel(val path: String, private val fs: FileSystem) : ViewModel() {

    private val _state = MutableStateFlow(PrefsUiState())
    val state: StateFlow<PrefsUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val xml = String(fs.readBytes(path))
                _state.update { it.copy(loading = false, entries = SharedPrefsFile.parse(xml), error = null, dirty = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun edit(entry: PrefEntry?) = _state.update { it.copy(editing = entry, adding = false) }
    fun startAdding() = _state.update { it.copy(adding = true, editing = null) }
    fun cancelDialog() = _state.update { it.copy(editing = null, adding = false) }

    fun put(entry: PrefEntry) {
        _state.update { s ->
            val replaced = s.entries.any { it.key == entry.key }
            val entries = if (replaced) s.entries.map { if (it.key == entry.key) entry else it } else s.entries + entry
            s.copy(entries = entries, dirty = true, editing = null, adding = false)
        }
    }

    fun remove(key: String) = _state.update { s -> s.copy(entries = s.entries.filterNot { it.key == key }, dirty = true, editing = null) }

    fun save() {
        viewModelScope.launch {
            try {
                fs.writeText(path, SharedPrefsFile.serialize(_state.value.entries))
                _state.update { it.copy(dirty = false) }
                _messages.send("saved")
            } catch (e: Exception) {
                _messages.send(e.message ?: e.toString())
            }
        }
    }

    fun validate(type: PrefType, value: String): String? = SharedPrefsFile.validate(type, value)
}
