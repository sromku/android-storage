package com.snatik.storage.app.feature.media

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.snatik.storage.app.util.readableSize
import java.io.File
import kotlin.math.roundToInt

private data class InfoRow(val key: String, val value: String, val geo: Pair<Double, Double>? = null)
private data class InfoGroup(val title: String, val rows: List<InfoRow>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val groups = remember(item.id) { buildInfo(context, item) }
    val raw = remember(item.id) { if (item.isVideo) emptyList() else readAllExif(item.path) }
    var rawExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
        ) {
            groups.forEach { group ->
                item(key = "h_${group.title}") { GroupHeader(group.title) }
                items(group.rows.size, key = { "${group.title}_$it" }) { idx ->
                    Row(group.rows[idx], context)
                }
            }
            if (raw.isNotEmpty()) {
                item(key = "raw_toggle") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { rawExpanded = !rawExpanded }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "All metadata  ·  ${raw.size} tags",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            if (rawExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(raw.size, key = { "raw_$it" }) { idx ->
                    AnimatedVisibility(visible = rawExpanded) { Row(raw[idx], context) }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun Row(row: InfoRow, context: Context) {
    val clickable = row.geo != null
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) Modifier.clickable { openMap(context, row.geo!!, row.value) } else Modifier,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.key,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(132.dp),
        )
        if (clickable) {
            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(end = 2.dp))
            Text(row.value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        } else {
            SelectionContainer {
                Text(
                    row.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (row.key.startsWith("0x") || row.key.first().isDigit()) FontFamily.Monospace else FontFamily.Default,
                )
            }
        }
    }
}

private fun openMap(context: Context, geo: Pair<Double, Double>, label: String) {
    val (lat, lng) = geo
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

private fun buildInfo(context: Context, item: MediaItem): List<InfoGroup> {
    val groups = ArrayList<InfoGroup>()

    // File
    val file = File(item.path)
    val fileRows = buildList {
        add(InfoRow("Name", item.name))
        if (item.bucket.isNotBlank()) add(InfoRow("Folder", item.bucket))
        add(InfoRow("Size", item.size.readableSize()))
        if (item.mime.isNotBlank()) add(InfoRow("Type", item.mime))
        if (item.path.isNotBlank()) add(InfoRow("Path", item.path))
    }
    groups += InfoGroup("File", fileRows)

    if (item.isVideo) {
        groups += InfoGroup("Video", readVideo(context, item))
    } else {
        // Image geometry
        val w = item.width
        val h = item.height
        val imageRows = buildList {
            if (w > 0 && h > 0) {
                add(InfoRow("Dimensions", "$w × $h"))
                add(InfoRow("Megapixels", "%.1f MP".format(w.toLong() * h / 1_000_000.0)))
                add(InfoRow("Aspect ratio", aspectRatio(w, h)))
            }
        }
        if (imageRows.isNotEmpty()) groups += InfoGroup("Image", imageRows)

        readExifGroups(item)?.let { groups += it }
    }
    return groups
}

private fun readExifGroups(item: MediaItem): InfoGroup? {
    val rows = ArrayList<InfoRow>()
    runCatching {
        val exif = ExifInterface(item.path)
        fun raw(tag: String) = exif.getAttribute(tag)?.takeIf { it.isNotBlank() }
        raw(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { rows += InfoRow("Taken", it) }
        val make = raw(ExifInterface.TAG_MAKE)
        val model = raw(ExifInterface.TAG_MODEL)
        if (make != null || model != null) rows += InfoRow("Camera", listOfNotNull(make, model).joinToString(" "))
        raw(ExifInterface.TAG_LENS_MODEL)?.let { rows += InfoRow("Lens", it) }
        raw(ExifInterface.TAG_EXPOSURE_TIME)?.let { rows += InfoRow("Exposure", formatExposure(it)) }
        raw(ExifInterface.TAG_F_NUMBER)?.let { rows += InfoRow("Aperture", "f/$it") }
        (raw(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: raw(ExifInterface.TAG_ISO_SPEED_RATINGS))?.let { rows += InfoRow("ISO", it) }
        raw(ExifInterface.TAG_FOCAL_LENGTH)?.let { rows += InfoRow("Focal length", "${formatRational(it)} mm") }
        raw(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.let { rows += InfoRow("35mm equiv.", "$it mm") }
        raw(ExifInterface.TAG_FLASH)?.let { rows += InfoRow("Flash", formatFlash(it)) }
        raw(ExifInterface.TAG_WHITE_BALANCE)?.let { rows += InfoRow("White balance", if (it == "0") "Auto" else "Manual") }
        raw(ExifInterface.TAG_SOFTWARE)?.let { rows += InfoRow("Software", it) }
        exif.latLong?.let { rows += InfoRow("Location", "%.5f, %.5f".format(it[0], it[1]), geo = it[0] to it[1]) }
        raw(ExifInterface.TAG_GPS_ALTITUDE)?.let { rows += InfoRow("Altitude", "${formatRational(it)} m") }
    }
    return if (rows.isEmpty()) null else InfoGroup("Camera", rows)
}

/** Every EXIF tag present in the file, for the expandable power-user dump. */
private fun readAllExif(path: String): List<InfoRow> {
    val out = ArrayList<InfoRow>()
    runCatching {
        val exif = ExifInterface(path)
        ExifInterface::class.java.declaredFields
            .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
            .mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }
            .distinct()
            .sorted()
            .forEach { tag ->
                exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { v ->
                    out += InfoRow(tag, v)
                }
            }
    }
    return out
}

private fun readVideo(context: Context, item: MediaItem): List<InfoRow> {
    val rows = ArrayList<InfoRow>()
    val r = MediaMetadataRetriever()
    runCatching {
        if (item.path.isNotBlank() && File(item.path).exists()) r.setDataSource(item.path)
        else r.setDataSource(context, item.uri)
        fun meta(key: Int) = r.extractMetadata(key)?.takeIf { it.isNotBlank() }

        val w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        if (w != null && h != null) rows += InfoRow("Resolution", "$w × $h")
        meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { rows += InfoRow("Duration", formatDurationMs(it)) }
        meta(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.let { rows += InfoRow("Frame rate", "%.0f fps".format(it)) }
        meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { rows += InfoRow("Bitrate", "%.1f Mbps".format(it / 1_000_000.0)) }
        meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let { rows += InfoRow("Rotation", "$it°") }
        meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { rows += InfoRow("Container", it) }
        meta(MediaMetadataRetriever.METADATA_KEY_DATE)?.let { rows += InfoRow("Recorded", it) }
        meta(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { rows += InfoRow("Location", it) }
    }
    runCatching { r.release() }
    if (rows.none { it.key == "Resolution" } && item.width > 0) {
        rows.add(0, InfoRow("Resolution", "${item.width} × ${item.height}"))
    }
    return rows
}

private fun aspectRatio(w: Int, h: Int): String {
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val g = gcd(w, h).coerceAtLeast(1)
    val rw = w / g
    val rh = h / g
    return if (rw <= 32 && rh <= 32) "$rw:$rh" else "%.2f:1".format(w.toDouble() / h)
}

private fun formatExposure(v: String): String {
    val t = v.toDoubleOrNull() ?: return "$v s"
    return if (t in 0.0..0.0 || t >= 1.0) "%.1f s".format(t) else "1/${(1.0 / t).roundToInt()} s"
}

private fun formatRational(v: String): String {
    if ("/" in v) {
        val (a, b) = v.split("/").let { it[0].toDoubleOrNull() to it.getOrNull(1)?.toDoubleOrNull() }
        if (a != null && b != null && b != 0.0) return "%.1f".format(a / b).removeSuffix(".0")
    }
    return v.toDoubleOrNull()?.let { "%.1f".format(it).removeSuffix(".0") } ?: v
}

private fun formatFlash(v: String): String {
    val n = v.toIntOrNull() ?: return v
    return if (n and 0x1 != 0) "Fired" else "Did not fire"
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
