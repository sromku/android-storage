package com.snatik.storage.app.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.core.data.ColumnInfo
import com.snatik.storage.core.data.Tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DbTableUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val columns: List<ColumnInfo> = emptyList(),
    val createSql: String? = null,
    val rows: Tabular? = null,
    val page: Int = 0,
    val total: Long? = null,
    val orderBy: String? = null,
    val descending: Boolean = false,
    val showSchema: Boolean = false,
    val selectedRow: Int? = null,
)

class DbTableViewModel(private val route: Route.DbTable, private val sessions: DatabaseSessions) : ViewModel() {

    val table: String = route.table

    private val _state = MutableStateFlow(DbTableUiState())
    val state: StateFlow<DbTableUiState> = _state.asStateFlow()

    init {
        load(0)
    }

    fun load(page: Int) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val db = sessions.get(route.path)
                val s = _state.value
                val order = s.orderBy?.let { "\"${it.replace("\"", "\"\"")}\"" + if (s.descending) " DESC" else " ASC" }
                val result = withContext(Dispatchers.IO) {
                    Triple(db.columns(table), db.rows(table, PAGE, page * PAGE, order), runCatching { db.count(table) }.getOrNull())
                }
                _state.update { it.copy(loading = false, columns = result.first, rows = result.second, total = result.third, page = page, createSql = db.createSql(table)) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun sortBy(column: String) {
        _state.update {
            if (it.orderBy == column) it.copy(descending = !it.descending) else it.copy(orderBy = column, descending = false)
        }
        load(0)
    }

    fun nextPage() { if (_state.value.rows?.truncated == true) load(_state.value.page + 1) }
    fun previousPage() { if (_state.value.page > 0) load(_state.value.page - 1) }
    fun toggleSchema() = _state.update { it.copy(showSchema = !it.showSchema) }
    fun selectRow(index: Int?) = _state.update { it.copy(selectedRow = index) }

    private companion object {
        const val PAGE = 100
    }
}
