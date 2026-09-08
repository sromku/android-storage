package com.snatik.storage.core.fs

import java.io.File

/** One item in a directory listing with the metadata the UI and the API need. */
data class FsEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isHidden: Boolean,
    val isSymlink: Boolean,
    val canRead: Boolean,
    val canWrite: Boolean,
    /** Number of direct children for a directory, null when unknown or not a directory. */
    val childCount: Int?,
) {
    val extension: String get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
    val kind: FileKind get() = FileKind.of(name, isDirectory)
    val mimeType: String get() = MimeTypes.of(name)
    val parentPath: String? get() = File(path).parent
}
