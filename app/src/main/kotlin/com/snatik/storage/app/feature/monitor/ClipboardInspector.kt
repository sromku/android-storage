package com.snatik.storage.app.feature.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One clipboard entry captured while this app held focus. */
data class ClipEntry(val text: String, val at: Long, val description: String)

/**
 * Reads and records the system clipboard. Android 10+ only exposes the clipboard to the foreground
 * app or the active IME, so this can capture clips only while this app is in front. The current
 * clip and any changes seen while running are kept in a session history.
 */
class ClipboardInspector(context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val _history = MutableStateFlow<List<ClipEntry>>(emptyList())
    val history: StateFlow<List<ClipEntry>> = _history.asStateFlow()

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capture() }

    fun start() {
        runCatching { clipboard.addPrimaryClipChangedListener(listener) }
        capture()
    }

    fun stop() {
        runCatching { clipboard.removePrimaryClipChangedListener(listener) }
    }

    /** Read the current primary clip now, adding it to history if new. */
    fun capture() {
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return
        val text = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).coerceToText(null)?.toString() }
            .joinToString("\n").trim()
        if (text.isEmpty()) return
        if (_history.value.firstOrNull()?.text == text) return
        _history.value = (listOf(ClipEntry(text, System.currentTimeMillis(), clip.description?.label?.toString() ?: "clip")) + _history.value).take(200)
    }

    fun set(text: String) {
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("android-storage", text)) }
    }

    fun clearHistory() {
        _history.value = emptyList()
    }
}
