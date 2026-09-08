package com.snatik.storage.app.feature.viewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.snatik.storage.app.util.readableSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageInfoSheet(path: String, onDismiss: () -> Unit) {
    val info = remember(path) { readImageInfo(path) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
            items(info) { (k, v) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(k, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                    SelectionContainer { Text(v, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

private fun readImageInfo(path: String): List<Pair<String, String>> {
    val out = LinkedHashMap<String, String>()
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth > 0) out["Dimensions"] = "${bounds.outWidth} × ${bounds.outHeight}"
        bounds.outMimeType?.let { out["Type"] = it }
    }
    out["Size"] = File(path).length().readableSize()
    runCatching {
        val exif = ExifInterface(path)
        fun put(label: String, tag: String) { exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { out[label] = it } }
        put("Taken", ExifInterface.TAG_DATETIME_ORIGINAL)
        put("Camera", ExifInterface.TAG_MAKE)
        put("Model", ExifInterface.TAG_MODEL)
        put("Lens", ExifInterface.TAG_LENS_MODEL)
        put("Exposure", ExifInterface.TAG_EXPOSURE_TIME)
        put("Aperture", ExifInterface.TAG_F_NUMBER)
        put("ISO", ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
        put("Focal length", ExifInterface.TAG_FOCAL_LENGTH)
        put("Orientation", ExifInterface.TAG_ORIENTATION)
        exif.latLong?.let { out["Location"] = "%.5f, %.5f".format(it[0], it[1]) }
    }
    return out.toList()
}
