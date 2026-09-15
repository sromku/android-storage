package com.snatik.storage.core.apps

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SQLite-backed clipboard history. Clips captured while the app is in the foreground (the only time
 * Android exposes the clipboard) are persisted here, so the history survives restarts. De-dupes by
 * [ClipRecord.key] and keeps at most [CAPACITY] clips.
 */
class ClipboardStore(context: Context) {

    private val dao = ClipboardDatabase.create(context.applicationContext).dao()
    private var adds = 0

    val recent: Flow<List<ClipRecord>> = dao.recent(DISPLAY_MAX).map { rows -> rows.map { it.toModel() } }
    val count: Flow<Int> = dao.count()

    /** Persist a clip. Returns true if it was new (not a duplicate). */
    suspend fun record(record: ClipRecord): Boolean {
        val inserted = dao.insert(record.toEntity()) >= 0
        if (inserted && ++adds >= TRIM_EVERY) { adds = 0; dao.trim(CAPACITY) }
        return inserted
    }

    suspend fun snapshot(limit: Int = DISPLAY_MAX): List<ClipRecord> = dao.snapshot(limit).map { it.toModel() }
    suspend fun clear() = dao.clear()

    private fun ClipRecord.toEntity() = ClipEntity(
        key = key, label = label, text = text, html = html,
        uris = uris.joinToString("\n"), mimeTypes = mimeTypes.joinToString(","),
        itemCount = itemCount, sensitive = sensitive, copiedAt = copiedAt, capturedAt = capturedAt,
    )

    private fun ClipEntity.toModel() = ClipRecord(
        key = key, label = label, text = text, html = html,
        uris = uris.split("\n").filter { it.isNotBlank() },
        mimeTypes = mimeTypes.split(",").filter { it.isNotBlank() },
        itemCount = itemCount, sensitive = sensitive, copiedAt = copiedAt, capturedAt = capturedAt,
    )

    companion object {
        const val CAPACITY = 500
        private const val DISPLAY_MAX = 500
        private const val TRIM_EVERY = 50
    }
}
