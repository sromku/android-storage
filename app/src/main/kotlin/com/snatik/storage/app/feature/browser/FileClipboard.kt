package com.snatik.storage.app.feature.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Paths waiting to be pasted somewhere, shared across browser screens. */
class FileClipboard {

    enum class Mode { COPY, MOVE }

    data class Content(val paths: List<String>, val mode: Mode)

    private val _content = MutableStateFlow<Content?>(null)
    val content: StateFlow<Content?> = _content.asStateFlow()

    fun set(paths: List<String>, mode: Mode) {
        _content.value = if (paths.isEmpty()) null else Content(paths, mode)
    }

    fun clear() {
        _content.value = null
    }
}
