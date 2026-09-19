package com.snatik.storage.app.feature.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A day's worth of media, for the grouped grid. */
data class MediaSection(val label: String, val items: List<MediaItem>)

data class MediaGridState(
    val loading: Boolean = true,
    val sections: List<MediaSection> = emptyList(),
    val total: Int = 0,
    val totalBytes: Long = 0,
)

class MediaGridViewModel(
    application: Application,
    private val repo: MediaRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MediaGridState())
    val state: StateFlow<MediaGridState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val items = repo.all()
            _state.value = MediaGridState(
                loading = false,
                sections = groupByDay(items),
                total = items.size,
                totalBytes = items.sumOf { it.size },
            )
        }
    }

    private fun groupByDay(items: List<MediaItem>): List<MediaSection> {
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("EEEE · MMM d, yyyy")
        return items
            .groupBy { Instant.ofEpochMilli(it.takenAt.coerceAtLeast(1)).atZone(zone).toLocalDate() }
            .toSortedMap(reverseOrder())
            .map { (date, list) -> MediaSection(date.format(fmt), list) }
    }
}
