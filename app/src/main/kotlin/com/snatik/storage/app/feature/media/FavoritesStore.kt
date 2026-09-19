package com.snatik.storage.app.feature.media

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Favorite photos, kept as a set of MediaStore ids in private prefs. Observable so UI reacts live. */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("media_favorites", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(load())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    fun contains(id: Long): Boolean = id in _ids.value

    fun toggle(id: Long) {
        val next = _ids.value.toMutableSet().apply { if (!add(id)) remove(id) }
        _ids.value = next
        prefs.edit { putStringSet(KEY, next.map { it.toString() }.toSet()) }
    }

    private fun load(): Set<Long> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    private companion object { const val KEY = "ids" }
}
