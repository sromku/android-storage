package com.snatik.storage.app.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.apps.AppRepository
import com.snatik.storage.core.apps.AppSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppFilter { ALL, USER, SYSTEM, DEBUGGABLE }
enum class AppSort { NAME, SIZE, UPDATED }

data class AppsUiState(
    val apps: List<AppSummary> = emptyList(),
    val totalCount: Int = 0,
    val totalBytes: Long = 0,
    val loading: Boolean = true,
    val hasUsageAccess: Boolean = true,
    val filter: AppFilter = AppFilter.USER,
    val sort: AppSort = AppSort.NAME,
    val query: String = "",
    val searchActive: Boolean = false,
)

class AppsViewModel(private val repository: AppRepository) : ViewModel() {

    private data class Local(
        val all: List<AppSummary> = emptyList(),
        val loading: Boolean = true,
        val hasUsageAccess: Boolean = true,
        val filter: AppFilter = AppFilter.USER,
        val sort: AppSort = AppSort.NAME,
        val query: String = "",
        val searchActive: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<AppsUiState> = combine(local, local) { l, _ ->
        val visible = l.all.asSequence()
            .filter {
                when (l.filter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> !it.isSystem
                    AppFilter.SYSTEM -> it.isSystem
                    AppFilter.DEBUGGABLE -> it.isDebuggable
                }
            }
            .filter { l.query.isBlank() || it.label.contains(l.query, true) || it.packageName.contains(l.query, true) }
            .sortedWith(
                when (l.sort) {
                    AppSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                    AppSort.SIZE -> compareByDescending<AppSummary> { it.storage?.totalBytes ?: -1 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                    AppSort.UPDATED -> compareByDescending<AppSummary> { it.lastUpdateTime }
                },
            )
            .toList()
        AppsUiState(
            apps = visible,
            totalCount = visible.size,
            totalBytes = visible.sumOf { it.storage?.totalBytes ?: 0 },
            loading = l.loading,
            hasUsageAccess = l.hasUsageAccess,
            filter = l.filter,
            sort = l.sort,
            query = l.query,
            searchActive = l.searchActive,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    // Re-listed on every screen resume (see AppsScreen) so uninstalls/installs/updates made
    // elsewhere show up on return. Only flip the loading state on the first, empty load so a
    // routine refresh doesn't flash a spinner over the existing list.
    fun refresh() {
        viewModelScope.launch {
            if (local.value.all.isEmpty()) local.update { it.copy(loading = true) }
            val apps = repository.list()
            local.update { it.copy(all = apps, loading = false, hasUsageAccess = repository.hasUsageAccess()) }
        }
    }

    fun setFilter(filter: AppFilter) = local.update { it.copy(filter = filter) }
    fun setSort(sort: AppSort) = local.update { it.copy(sort = sort) }
    fun setQuery(query: String) = local.update { it.copy(query = query) }
    fun setSearchActive(active: Boolean) = local.update { it.copy(searchActive = active, query = if (active) it.query else "") }
    fun usageAccessIntent() = repository.usageAccessSettingsIntent()
}
