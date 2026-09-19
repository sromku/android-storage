package com.snatik.storage.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.snatik.storage.app.R
import com.snatik.storage.core.fs.MimeTypes
import java.io.File

object Intents {

    private fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

    fun openWith(context: Context, path: String) {
        val file = File(path)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(context, file), MimeTypes.of(file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            val chooser = Intent.createChooser(intent, context.getString(R.string.open_with)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_for_file, Toast.LENGTH_SHORT).show()
        }
    }

    /** "Use as" - lets the system offer Set as wallpaper, contact photo, etc. */
    fun useAs(context: Context, path: String) {
        val file = File(path)
        val intent = Intent(Intent.ACTION_ATTACH_DATA)
            .setDataAndType(uri(context, file), MimeTypes.of(file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra("mimeType", MimeTypes.of(file.name))
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.use_as)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_for_file, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, paths: List<String>) {
        val files = paths.map(::File).filter { it.isFile }
        if (files.isEmpty()) {
            Toast.makeText(context, R.string.share_files_only, Toast.LENGTH_SHORT).show()
            return
        }
        val uris = ArrayList(files.map { uri(context, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType(MimeTypes.of(files.single().name)).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
