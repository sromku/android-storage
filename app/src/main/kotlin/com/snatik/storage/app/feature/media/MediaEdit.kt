package com.snatik.storage.app.feature.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/** Rename and move media through MediaStore (the app holds all-files access, so no consent prompts). */
object MediaEdit {

    fun rename(context: Context, uri: Uri, newName: String): Boolean = runCatching {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
        context.contentResolver.update(uri, values, null, null) > 0
    }.getOrDefault(false)

    /** Moves items into Pictures/[folder] (API 29+) via RELATIVE_PATH. Returns how many moved. */
    fun move(context: Context, uris: List<Uri>, folder: String): Int {
        if (Build.VERSION.SDK_INT < 29) return 0
        val relative = "${Environment.DIRECTORY_PICTURES}/${folder.trim('/')}/"
        return uris.count { uri ->
            runCatching {
                val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relative) }
                context.contentResolver.update(uri, values, null, null) > 0
            }.getOrDefault(false)
        }
    }

    /** Distinct image folders (bucket display names), for the move picker. */
    fun folders(context: Context): List<String> = runCatching {
        val out = LinkedHashSet<String>()
        val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
            "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC",
        )?.use { c ->
            val col = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) c.getString(col)?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        }
        out.toList()
    }.getOrDefault(emptyList())
}
