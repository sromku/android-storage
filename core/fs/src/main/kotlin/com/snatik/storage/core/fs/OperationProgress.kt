package com.snatik.storage.core.fs

/** Progress of a long running file operation such as copy, move or delete. */
data class OperationProgress(
    val totalItems: Int,
    val doneItems: Int,
    val totalBytes: Long,
    val doneBytes: Long,
    val currentName: String,
) {
    /** 0..1 when the total is known, null for indeterminate. */
    val fraction: Float?
        get() = when {
            totalBytes > 0 -> (doneBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
            totalItems > 0 -> (doneItems.toFloat() / totalItems).coerceIn(0f, 1f)
            else -> null
        }
}
