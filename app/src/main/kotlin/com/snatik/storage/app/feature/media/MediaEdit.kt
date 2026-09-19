package com.snatik.storage.app.feature.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import java.io.File

/** Rename and move media. The app holds all-files access, so moves are real file moves anywhere. */
object MediaEdit {

    fun rename(context: Context, uri: Uri, newName: String): Boolean = runCatching {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
        context.contentResolver.update(uri, values, null, null) > 0
    }.getOrDefault(false)

    /**
     * Moves the given files into [targetDir] (any folder - all-files access) and re-indexes both the
     * old and new locations so MediaStore, and thus the gallery, follow. Returns how many moved.
     */
    fun move(context: Context, paths: List<String>, targetDir: File): Int {
        if (!targetDir.exists()) targetDir.mkdirs()
        val toScan = ArrayList<String>()
        var moved = 0
        for (p in paths) {
            val src = File(p)
            if (!src.isFile) continue
            val dst = File(targetDir, src.name)
            if (dst.absolutePath == src.absolutePath) continue
            val ok = runCatching { src.renameTo(dst) }.getOrDefault(false) ||
                runCatching { src.copyTo(dst, overwrite = true); src.delete(); true }.getOrDefault(false)
            if (ok) { moved++; toScan += src.absolutePath; toScan += dst.absolutePath }
        }
        if (toScan.isNotEmpty()) {
            runCatching { MediaScannerConnection.scanFile(context, toScan.toTypedArray(), null, null) }
        }
        return moved
    }

    /** Immediate subdirectories of [dir], for the folder browser (hidden folders excluded). */
    fun subdirs(dir: File): List<File> = runCatching {
        dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }.getOrDefault(emptyList())
}
