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
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TypeStat(val label: String, val count: Int, val bytes: Long)
data class FolderStat(val name: String, val count: Int, val bytes: Long)
data class MonthStat(val label: String, val count: Int)
data class NameCount(val name: String, val count: Int)

data class InsightsData(
    val loading: Boolean = true,
    val total: Int = 0,
    val totalBytes: Long = 0,
    val oldest: Long = 0,
    val newest: Long = 0,
    val types: List<TypeStat> = emptyList(),
    val folders: List<FolderStat> = emptyList(),
    val months: List<MonthStat> = emptyList(),
    val largest: List<MediaItem> = emptyList(),
    val cameras: List<NameCount> = emptyList(),
    val lenses: List<NameCount> = emptyList(),
    val geotagged: Int = 0,
    val exifLoading: Boolean = false,
)

/** Aggregates library-wide statistics for the visual insights screen. */
class PhotoInsightsViewModel(
    application: Application,
    private val repo: MediaRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(InsightsData())
    val state: StateFlow<InsightsData> = _state.asStateFlow()

    init { compute() }

    private fun compute() {
        viewModelScope.launch {
            val items = repo.cached.ifEmpty { repo.all() }
            val base = withContext(Dispatchers.Default) { basicStats(items) }
            _state.value = base
            enrichWithExif(items)
        }
    }

    private fun basicStats(items: List<MediaItem>): InsightsData {
        val videos = items.filter { it.isVideo }
        val raw = items.filter { !it.isVideo && isRawMedia(it.name, it.mime) }
        val screenshots = items.filter { !it.isVideo && !isRawMedia(it.name, it.mime) && isScreenshot(it) }
        val photos = items.filter { !it.isVideo && !isRawMedia(it.name, it.mime) && !isScreenshot(it) }

        fun stat(label: String, list: List<MediaItem>) = TypeStat(label, list.size, list.sumOf { it.size })
        val types = listOf(
            stat("Photos", photos),
            stat("Videos", videos),
            stat("RAW", raw),
            stat("Screenshots", screenshots),
        ).filter { it.count > 0 }

        val folders = items.groupBy { it.bucket.ifBlank { "Unknown" } }
            .map { (name, list) -> FolderStat(name, list.size, list.sumOf { it.size }) }
            .sortedByDescending { it.bytes }
            .take(6)

        val zone = ZoneId.systemDefault()
        val byMonth = items.groupBy { YearMonth.from(Instant.ofEpochMilli(it.takenAt.coerceAtLeast(1)).atZone(zone)) }
        val fmt = DateTimeFormatter.ofPattern("MMM ''yy")
        val months = byMonth.keys.sorted().takeLast(18).map { ym -> MonthStat(ym.format(fmt), byMonth[ym]?.size ?: 0) }

        val times = items.map { it.takenAt }.filter { it > 0 }
        return InsightsData(
            loading = false,
            total = items.size,
            totalBytes = items.sumOf { it.size },
            oldest = times.minOrNull() ?: 0,
            newest = times.maxOrNull() ?: 0,
            types = types,
            folders = folders,
            months = months,
            largest = items.sortedByDescending { it.size }.take(6),
            exifLoading = true,
        )
    }

    private fun enrichWithExif(items: List<MediaItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            val cameras = HashMap<String, Int>()
            val lenses = HashMap<String, Int>()
            var geotagged = 0
            for (it in items) {
                if (it.isVideo || it.path.isBlank()) continue
                runCatching {
                    val exif = ExifInterface(it.path)
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
                    val cam = listOf(make, model).filter { s -> s.isNotBlank() }.joinToString(" ")
                    if (cam.isNotBlank()) cameras[cam] = (cameras[cam] ?: 0) + 1
                    exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.takeIf { s -> s.isNotBlank() }?.let { l -> lenses[l] = (lenses[l] ?: 0) + 1 }
                    if (exif.latLong != null) geotagged++
                }
            }
            val camList = cameras.entries.sortedByDescending { it.value }.take(6).map { NameCount(it.key, it.value) }
            val lensList = lenses.entries.sortedByDescending { it.value }.take(6).map { NameCount(it.key, it.value) }
            _state.value = _state.value.copy(cameras = camList, lenses = lensList, geotagged = geotagged, exifLoading = false)
        }
    }

    private fun isScreenshot(item: MediaItem): Boolean =
        item.bucket.equals("Screenshots", ignoreCase = true) || item.name.startsWith("Screenshot", ignoreCase = true)
}
