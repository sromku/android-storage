package com.snatik.storage.app.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.data.TableInfo
import com.snatik.storage.core.data.Tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

enum class DbTab { TABLES, SQL }

data class DatabaseUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val isCopy: Boolean = false,
    val dirty: Boolean = false,
    val tables: List<TableInfo> = emptyList(),
    val tab: DbTab = DbTab.TABLES,
    val sql: String = "",
    val sqlResult: Tabular? = null,
    val sqlError: String? = null,
    val sqlRunning: Boolean = false,
    val selectedRow: Int? = null,
)

class DatabaseViewModel(val path: String, private val sessions: DatabaseSessions) : ViewModel() {

    private val _state = MutableStateFlow(DatabaseUiState())
    val state: StateFlow<DatabaseUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val db = sessions.get(path)
                val tables = withContext(Dispatchers.IO) { db.tables() }
                _state.update { it.copy(loading = false, tables = tables, isCopy = db.isCopy, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun selectTab(tab: DbTab) = _state.update { it.copy(tab = tab) }
    fun setSql(sql: String) = _state.update { it.copy(sql = sql) }
    fun selectRow(index: Int?) = _state.update { it.copy(selectedRow = index) }

    fun runSql() {
        val sql = _state.value.sql.trim()
        if (sql.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(sqlRunning = true, sqlError = null) }
            try {
                val db = sessions.get(path)
                val result = withContext(Dispatchers.IO) { db.query(sql) }
                val mutating = result.columns.isEmpty()
                _state.update { it.copy(sqlRunning = false, sqlResult = result, dirty = it.dirty || mutating) }
                if (mutating) {
                    _messages.send("ran")
                    load()
                }
            } catch (e: Exception) {
                _state.update { it.copy(sqlRunning = false, sqlError = e.message ?: e.toString()) }
            }
        }
    }

    fun saveBack() {
        viewModelScope.launch {
            try {
                sessions.get(path).saveBack()
                _state.update { it.copy(dirty = false) }
                _messages.send("saved")
            } catch (e: Exception) {
                _messages.send(e.message ?: e.toString())
            }
        }
    }

    override fun onCleared() {
        runBlocking { sessions.close(path) }
    }
}
