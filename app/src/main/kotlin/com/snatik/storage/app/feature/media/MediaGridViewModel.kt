package com.snatik.storage.app.feature.media

import android.app.Application
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A day's worth of media, for the grouped grid. */
data class MediaSection(val label: String, val items: List<MediaItem>)

/** Smart views over the library. */
enum class MediaFilter { ALL, PHOTOS, VIDEOS, RAW, SCREENSHOTS, CAMERA, FAVORITES }

data class MediaGridState(
    val loading: Boolean = true,
    val sections: List<MediaSection> = emptyList(),
    val total: Int = 0,          // whole library
    val totalBytes: Long = 0,
    val shown: Int = 0,          // after filter + query
    val filter: MediaFilter = MediaFilter.ALL,
    val query: String = "",
    val hasCamera: Boolean = false,
    val hasFavorites: Boolean = false,
)

class MediaGridViewModel(
    application: Application,
    private val repo: MediaRepository,
    private val favorites: FavoritesStore,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MediaGridState())
    val state: StateFlow<MediaGridState> = _state.asStateFlow()

    private var items: List<MediaItem> = emptyList()
    /** Per-item lowercased haystack (name, folder, type, camera, lens, ISO, ...) for search. */
    private var index: Map<Long, String> = emptyMap()
    private var reloadJob: kotlinx.coroutines.Job? = null

    /** Refresh the grid live when media changes (camera sync, convert, delete, new shots). */
    private val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            reloadJob?.cancel()
            reloadJob = viewModelScope.launch { kotlinx.coroutines.delay(800); load(quiet = true) }
        }
    }

    init {
        load()
        val cr = application.contentResolver
        cr.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        cr.registerContentObserver(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        viewModelScope.launch {
            favorites.ids.collect { fav ->
                _state.value = _state.value.copy(hasFavorites = fav.isNotEmpty())
                recompute()
            }
        }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().contentResolver.unregisterContentObserver(observer) }
    }

    fun load(quiet: Boolean = false) {
        if (!quiet) _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            items = repo.all()
            index = baseIndex(items)
            recompute()
            _state.value = _state.value.copy(
                loading = false,
                total = items.size,
                totalBytes = items.sumOf { it.size },
                hasCamera = items.any { isCameraImport(it) },
            )
            // Enrich the index with EXIF (camera/lens/ISO/focal) in the background, then let any
            // active query re-run against the richer terms.
            enrichIndex()
        }
    }

    fun setFilter(filter: MediaFilter) {
        if (filter == _state.value.filter) return
        _state.value = _state.value.copy(filter = filter)
        recompute()
    }

    fun setQuery(query: String) {
        if (query == _state.value.query) return
        _state.value = _state.value.copy(query = query)
        recompute()
    }

    private fun recompute() {
        val filter = _state.value.filter
        val q = _state.value.query.trim().lowercase()
        val filtered = items.asSequence()
            .filter { matchesFilter(it, filter) }
            .filter { q.isEmpty() || q.split(' ').all { term -> index[it.id]?.contains(term) == true } }
            .toList()
        // Keep the pager's source in sync with what's on screen, so opening an item pages through
        // the filtered/searched set instead of the whole library.
        repo.pagerItems = filtered
        _state.value = _state.value.copy(sections = groupByDay(filtered), shown = filtered.size)
    }

    private fun matchesFilter(item: MediaItem, filter: MediaFilter): Boolean = when (filter) {
        MediaFilter.ALL -> true
        MediaFilter.PHOTOS -> !item.isVideo
        MediaFilter.VIDEOS -> item.isVideo
        MediaFilter.RAW -> isRawMedia(item.name, item.mime)
        MediaFilter.SCREENSHOTS -> isScreenshot(item)
        MediaFilter.CAMERA -> isCameraImport(item)
        MediaFilter.FAVORITES -> favorites.contains(item.id)
    }

    private fun isCameraImport(item: MediaItem): Boolean = item.path.contains("/Storage Studio/Camera")

    private fun isScreenshot(item: MediaItem): Boolean =
        item.bucket.equals("Screenshots", ignoreCase = true) ||
            item.name.startsWith("Screenshot", ignoreCase = true)

    private fun baseIndex(items: List<MediaItem>): Map<Long, String> = buildMap {
        for (it in items) {
            val tokens = buildString {
                append(it.name.lowercase()).append(' ')
                append(it.bucket.lowercase()).append(' ')
                append(it.mime.lowercase()).append(' ')
                if (it.isVideo) append("video ") else append("photo image ")
                if (isRawMedia(it.name, it.mime)) append("raw ")
                if (isScreenshot(it)) append("screenshot ")
            }
            put(it.id, tokens)
        }
    }

    private fun enrichIndex() {
        viewModelScope.launch(Dispatchers.IO) {
            val enriched = HashMap(index)
            var touched = false
            for (it in items) {
                if (it.isVideo || it.path.isBlank()) continue
                val extra = runCatching {
                    val exif = ExifInterface(it.path)
                    buildString {
                        exif.getAttribute(ExifInterface.TAG_MAKE)?.let { v -> append(v.lowercase()).append(' ') }
                        exif.getAttribute(ExifInterface.TAG_MODEL)?.let { v -> append(v.lowercase()).append(' ') }
                        exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.let { v -> append(v.lowercase()).append(' ') }
                        exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { v -> append("iso").append(v).append(' ') }
                        exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { v -> append(v.lowercase()).append("mm ") }
                    }
                }.getOrNull().orEmpty()
                if (extra.isNotEmpty()) {
                    enriched[it.id] = (enriched[it.id].orEmpty() + extra)
                    touched = true
                }
            }
            if (touched) {
                index = enriched
                withContext(Dispatchers.Main) { if (_state.value.query.isNotEmpty()) recompute() }
            }
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
