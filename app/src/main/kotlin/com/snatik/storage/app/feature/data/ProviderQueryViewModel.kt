package com.snatik.storage.app.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.core.data.ProviderQuery
import com.snatik.storage.core.data.QueryRequest
import com.snatik.storage.core.data.Tabular
import com.snatik.storage.core.fs.FileSystem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QueryOp(val sql: String, val hasValue: Boolean) {
    EQ("=", true), NE("!=", true), GT(">", true), LT("<", true), GE(">=", true), LE("<=", true),
    LIKE("LIKE", true), NULL("IS NULL", false), NOT_NULL("IS NOT NULL", false);
}

data class Condition(val column: String = "", val op: QueryOp = QueryOp.EQ, val value: String = "")

data class QueryForm(
    val uri: String,
    /** Empty = all columns. */
    val projection: Set<String> = emptySet(),
    val conditions: List<Condition> = emptyList(),
    val sortColumn: String = "",
    val sortDesc: Boolean = false,
    val limit: String = "100",
) {
    fun toRequest(offset: Int): QueryRequest {
        val active = conditions.filter { it.column.isNotBlank() }
        val where = active.joinToString(" AND ") { c ->
            if (c.op.hasValue) "${c.column} ${c.op.sql} ?" else "${c.column} ${c.op.sql}"
        }.ifEmpty { null }
        val args = active.filter { it.op.hasValue }.map { it.value }
        return QueryRequest(
            uri = uri.trim(),
            projection = projection.toList(),
            selection = where,
            selectionArgs = args,
            sortOrder = sortColumn.trim().ifEmpty { null }?.let { "$it ${if (sortDesc) "DESC" else "ASC"}" },
            limit = limit.trim().toIntOrNull()?.coerceIn(1, 5000) ?: 100,
            offset = offset,
        )
    }
}

data class ProviderQueryUiState(
    val form: QueryForm,
    /** Columns discovered from the last successful result, to drive the builder. */
    val columns: List<String> = emptyList(),
    val editing: Boolean = true,
    val loading: Boolean = false,
    val result: Tabular? = null,
    val page: Int = 0,
    val error: String? = null,
    val permissionDenied: Boolean = false,
    /** True when even the privileged shell was refused — so "connect Shizuku" is not the fix. */
    val shellDenied: Boolean = false,
    val mime: String? = null,
    val selectedRow: Int? = null,
    /** URIs currently in Quick queries, so the menu can show Save vs Remove. */
    val savedUris: Set<String> = emptySet(),
)

class ProviderQueryViewModel(route: Route.ProviderQuery, private val query: ProviderQuery, private val fs: FileSystem, private val savedStore: SavedQueryStore) : ViewModel() {

    val title: String = route.title

    private val _state = MutableStateFlow(ProviderQueryUiState(form = QueryForm(uri = route.uri)))
    val state: StateFlow<ProviderQueryUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        run()
        viewModelScope.launch {
            savedStore.saved.collect { saved -> _state.update { it.copy(savedUris = saved.map { s -> s.uri }.toSet()) } }
        }
    }

    /** Save (or un-save) the current URI to Quick queries so it can be reopened without rebuilding. */
    fun toggleSave() {
        val uri = _state.value.form.uri.trim()
        if (uri.isEmpty()) return
        if (savedStore.isSaved(uri)) savedStore.remove(uri)
        else savedStore.save(title = title.ifBlank { uri.substringAfter("://").substringAfter('/') }, uri = uri)
    }

    fun updateForm(form: QueryForm) = _state.update { it.copy(form = form) }
    fun setEditing(editing: Boolean) = _state.update { it.copy(editing = editing) }
    fun selectRow(index: Int?) = _state.update { it.copy(selectedRow = index) }

    fun run(page: Int = 0) {
        val form = _state.value.form
        val request = form.toRequest(offset = page * (form.limit.toIntOrNull() ?: 100))
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, permissionDenied = false, editing = false) }
            val mime = query.type(request.uri)
            try {
                val result = query.query(request)
                _state.update { it.copy(loading = false, result = result, page = page, mime = mime, columns = result.columns.ifEmpty { it.columns }) }
            } catch (e: ProviderQuery.QueryException) {
                // Surface the builder so the user can fix the URI (e.g. add a table path).
                _state.update { it.copy(loading = false, result = null, error = e.message, permissionDenied = e.permissionDenied, shellDenied = e.shellDenied, mime = mime, editing = true) }
            }
        }
    }

    fun nextPage() { if (_state.value.result?.truncated == true) run(_state.value.page + 1) }
    fun previousPage() { if (_state.value.page > 0) run(_state.value.page - 1) }

    fun export(asJson: Boolean) {
        val result = _state.value.result ?: return
        viewModelScope.launch {
            val name = title.replace(Regex("[^A-Za-z0-9._-]"), "_") + if (asJson) ".json" else ".csv"
            val path = "/storage/emulated/0/Download/$name"
            try {
                fs.writeText(path, if (asJson) result.toJson() else result.toCsv())
                _messages.send("saved:$path")
            } catch (e: Exception) {
                _messages.send(e.message ?: e.toString())
            }
        }
    }
}
