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

data class QueryForm(
    val uri: String,
    val projection: String = "",
    val selection: String = "",
    val args: String = "",
    val sort: String = "",
    val limit: String = "100",
) {
    fun toRequest(offset: Int) = QueryRequest(
        uri = uri.trim(),
        projection = projection.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        selection = selection.trim().ifEmpty { null },
        selectionArgs = args.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        sortOrder = sort.trim().ifEmpty { null },
        limit = limit.trim().toIntOrNull()?.coerceIn(1, 5000) ?: 100,
        offset = offset,
    )
}

data class ProviderQueryUiState(
    val form: QueryForm,
    val editing: Boolean = true,
    val loading: Boolean = false,
    val result: Tabular? = null,
    val page: Int = 0,
    val error: String? = null,
    val permissionDenied: Boolean = false,
    val mime: String? = null,
    val selectedRow: Int? = null,
)

class ProviderQueryViewModel(route: Route.ProviderQuery, private val query: ProviderQuery, private val fs: FileSystem) : ViewModel() {

    val title: String = route.title

    private val _state = MutableStateFlow(ProviderQueryUiState(form = QueryForm(uri = route.uri)))
    val state: StateFlow<ProviderQueryUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        run()
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
                _state.update { it.copy(loading = false, result = result, page = page, mime = mime) }
            } catch (e: ProviderQuery.QueryException) {
                _state.update { it.copy(loading = false, result = null, error = e.message, permissionDenied = e.permissionDenied, mime = mime) }
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
