package com.snatik.storage.app.feature.browser

import android.content.Context
import androidx.core.content.edit
import com.snatik.storage.core.fs.FsEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SortField { NAME, MODIFIED, SIZE, TYPE }

data class SortSpec(val field: SortField, val ascending: Boolean) {
    fun comparator(): Comparator<FsEntry> {
        val directoriesFirst = compareBy<FsEntry> { !it.isDirectory }
        val key: Comparator<FsEntry> = when (field) {
            SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.MODIFIED -> compareBy { it.lastModified }
            SortField.SIZE -> compareBy { it.size }
            SortField.TYPE -> compareBy<FsEntry> { it.extension }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        return directoriesFirst.then(if (ascending) key else key.reversed())
    }
}

data class ListingPreferences(val sort: SortSpec, val showHidden: Boolean)

/** Sort order and hidden-file visibility, shared by every browser screen and persisted. */
class BrowserPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("browser", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        ListingPreferences(
            sort = SortSpec(
                field = runCatching { SortField.valueOf(prefs.getString("sort_field", null) ?: "") }.getOrDefault(SortField.NAME),
                ascending = prefs.getBoolean("sort_asc", true),
            ),
            showHidden = prefs.getBoolean("show_hidden", false),
        ),
    )
    val state: StateFlow<ListingPreferences> = _state.asStateFlow()

    fun setSort(field: SortField, ascending: Boolean) {
        _state.update { it.copy(sort = SortSpec(field, ascending)) }
        prefs.edit { putString("sort_field", field.name); putBoolean("sort_asc", ascending) }
    }

    fun setShowHidden(show: Boolean) {
        _state.update { it.copy(showHidden = show) }
        prefs.edit { putBoolean("show_hidden", show) }
    }
}
