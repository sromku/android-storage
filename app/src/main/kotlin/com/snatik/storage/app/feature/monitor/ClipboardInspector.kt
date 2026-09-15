package com.snatik.storage.app.feature.monitor

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.snatik.storage.core.apps.ClipRecord
import com.snatik.storage.core.apps.ClipboardStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reads and records the system clipboard. Android 10+ only exposes the clipboard to the foreground
 * app or the active IME, so this captures clips only while the app is in front. Each clip is parsed
 * in full — text, HTML, URIs, MIME types, item count, the sensitive flag and its timestamp — and
 * persisted to the [ClipboardStore] so the history survives restarts.
 */
class ClipboardInspector(context: Context) {

    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val store = ClipboardStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val history = store.recent
    val count = store.count

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capture() }
    @Volatile private var lastKey: String? = null

    fun start() {
        runCatching { clipboard.addPrimaryClipChangedListener(listener) }
        capture()
    }

    fun stop() = runCatching { clipboard.removePrimaryClipChangedListener(listener) }

    /** Read the current primary clip now, parse it fully, and persist it if new. */
    fun capture() {
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return
        val record = clip.toRecord() ?: return
        if (record.key == lastKey) return
        lastKey = record.key
        scope.launch { store.record(record) }
    }

    private companion object {
        val URI_IN_TEXT = Regex("""(?:content|file|https?)://[^\s]+""", RegexOption.IGNORE_CASE)
    }

    fun set(text: String) {
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("android-storage", text)) }
    }

    /** Overwrite the clipboard with an empty clip (best-effort "clear"). */
    fun clearClipboard() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    suspend fun clearHistory() = store.clear()

    private fun ClipData.toRecord(): ClipRecord? {
        val desc: ClipDescription? = description
        val items = (0 until itemCount).map { getItemAt(it) }
        val text = items.mapNotNull { it.coerceToText(appContext)?.toString() }.joinToString("\n").trim()
        val html = items.mapNotNull { it.htmlText?.toString() }.joinToString("\n").trim()
        val itemUris = items.mapNotNull { it.uri?.toString() } +
            items.mapNotNull { it.intent?.dataString }.filter { it.isNotBlank() }
        // Most apps copy even URLs/content links as plain text, so scan the text too when the clip
        // carries no explicit URI items — those detected links are still worth acting on.
        val detected = if (itemUris.isEmpty()) URI_IN_TEXT.findAll(text).map { it.value.trimEnd('.', ',', ')') }.toList() else emptyList()
        val uris = (itemUris + detected).distinct()
        if (text.isEmpty() && html.isEmpty() && uris.isEmpty()) return null
        val mimeTypes = (0 until (desc?.mimeTypeCount ?: 0)).map { desc!!.getMimeType(it) }
        val sensitive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            desc?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
        val copiedAt = desc?.timestamp?.takeIf { it > 0 } ?: System.currentTimeMillis()
        val key = "$copiedAt|${text.hashCode()}|${uris.joinToString()}"
        return ClipRecord(
            key = key,
            label = desc?.label?.toString().orEmpty(),
            text = text,
            html = html,
            uris = uris.distinct(),
            mimeTypes = mimeTypes,
            itemCount = itemCount,
            sensitive = sensitive,
            copiedAt = copiedAt,
            capturedAt = System.currentTimeMillis(),
        )
    }
}
