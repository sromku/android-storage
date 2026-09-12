package com.snatik.storage.app.feature.browser

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.snatik.storage.app.R
import com.snatik.storage.core.fs.FileKind
import com.snatik.storage.core.fs.FsEntry

/**
 * Every action the browser row menu can offer, with its label and icon. Destructive items render
 * in the error color. Adding a new action = one entry here plus a branch in the screen's dispatcher.
 */
enum class FileAction(@StringRes val label: Int, val icon: ImageVector, val destructive: Boolean = false) {
    OPEN_WITH(R.string.open_with, Icons.AutoMirrored.Filled.OpenInNew),
    SHARE(R.string.share, Icons.Default.Share),
    VIEW_TEXT(R.string.view_as_text, Icons.Default.Description),
    VIEW_HEX(R.string.view_as_hex, Icons.Default.DataObject),
    OPEN_DB(R.string.open_as_database, Icons.Default.Storage),
    OPEN_PREFS(R.string.open_as_prefs, Icons.Default.Tune),
    COPY(R.string.copy, Icons.Default.ContentCopy),
    CUT(R.string.cut, Icons.Default.ContentCut),
    RENAME(R.string.rename, Icons.Default.DriveFileRenameOutline),
    DETAILS(R.string.details, Icons.Default.Info),
    DELETE(R.string.delete, Icons.Default.Delete, destructive = true),
}

/**
 * The row overflow menu for [entry], as ordered groups rendered with dividers between them:
 * type-specific "open/view as" actions first, then the common file operations, then delete.
 * This is the single source of truth for which actions a given file type gets — the same
 * type awareness that [FileKind] drives the row icon with.
 */
fun fileMenu(entry: FsEntry): List<List<FileAction>> = buildList {
    openActionsFor(entry).takeIf { it.isNotEmpty() }?.let { add(it) }
    add(listOf(FileAction.COPY, FileAction.CUT, FileAction.RENAME, FileAction.DETAILS))
    add(listOf(FileAction.DELETE))
}

/** Which "open as / view as" actions make sense for this entry's type; directories get none. */
private fun openActionsFor(entry: FsEntry): List<FileAction> {
    if (entry.isDirectory) return emptyList()
    return buildList {
        add(FileAction.OPEN_WITH)
        add(FileAction.SHARE)
        // "View as text" only where the primary tap isn't already a text view and bytes are readable.
        if (entry.kind in TEXTUAL) add(FileAction.VIEW_TEXT)
        add(FileAction.VIEW_HEX) // any file can be peeked in hex
        if (entry.kind == FileKind.DATABASE || entry.kind == FileKind.OTHER) add(FileAction.OPEN_DB)
        if (entry.kind == FileKind.XML) add(FileAction.OPEN_PREFS)
    }
}

private val TEXTUAL = setOf(FileKind.XML, FileKind.JSON, FileKind.DATABASE, FileKind.OTHER)
