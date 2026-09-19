package com.snatik.storage.app.feature.media

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.snatik.storage.app.R
import com.snatik.storage.app.util.Intents
import kotlinx.coroutines.launch
import android.widget.Toast

private class PrivacyFinding(
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val sensitive: Boolean,
    val geo: Pair<Double, Double>? = null,
)

/**
 * "What this photo reveals about you" - a friendly, shareable read of the metadata a recipient
 * would receive: location, device, exact time, editing history. One tap strips it all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyReportSheet(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val findings = remember(item.id) { readFindings(context, item) }
    val sensitiveCount = findings.count { it.sensitive }
    var stripping by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(stringResource(R.string.privacy_report), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    if (sensitiveCount > 0) R.string.privacy_report_lead_reveals else R.string.privacy_report_lead_clean,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            findings.forEach { f -> FindingRow(f, context) }

            if (sensitiveCount > 0) {
                Button(
                    onClick = {
                        if (stripping) return@Button
                        stripping = true
                        Toast.makeText(context, R.string.share_stripping, Toast.LENGTH_SHORT).show()
                        scope.launch {
                            val clean = MediaShare.stripToCache(context, item)
                            if (clean != null) Intents.share(context, listOf(clean.absolutePath))
                            else Toast.makeText(context, R.string.share_strip_failed, Toast.LENGTH_SHORT).show()
                            stripping = false
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.privacy_share_safely), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun FindingRow(f: PrivacyFinding, context: Context) {
    val tint = if (f.sensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(f.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp).padding(top = 2.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(f.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(f.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (f.geo != null) {
                Text(
                    stringResource(R.string.privacy_open_map),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp).clickable { openMap(context, f.geo) },
                )
            }
        }
    }
}

private fun openMap(context: Context, geo: Pair<Double, Double>) {
    val (lat, lng) = geo
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "geo:$lat,$lng?q=$lat,$lng".toUri())) }
}

private fun readFindings(context: Context, item: MediaItem): List<PrivacyFinding> {
    val out = ArrayList<PrivacyFinding>()
    val res = context.resources
    runCatching {
        val exif = ExifInterface(item.path)
        fun attr(tag: String) = exif.getAttribute(tag)?.takeIf { it.isNotBlank() }

        val ll = exif.latLong
        if (ll != null) {
            out += PrivacyFinding(
                Icons.Filled.LocationOn,
                res.getString(R.string.privacy_location_title),
                res.getString(R.string.privacy_location_body, ll[0], ll[1]),
                sensitive = true,
                geo = ll[0] to ll[1],
            )
        } else {
            out += PrivacyFinding(
                Icons.Filled.CheckCircle,
                res.getString(R.string.privacy_no_location_title),
                res.getString(R.string.privacy_no_location_body),
                sensitive = false,
            )
        }

        val make = attr(ExifInterface.TAG_MAKE)
        val model = attr(ExifInterface.TAG_MODEL)
        if (make != null || model != null) {
            out += PrivacyFinding(
                Icons.Filled.CameraAlt,
                res.getString(R.string.privacy_device_title),
                listOfNotNull(make, model).joinToString(" "),
                sensitive = true,
            )
        }

        attr(ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
            out += PrivacyFinding(
                Icons.Filled.Schedule,
                res.getString(R.string.privacy_time_title),
                it,
                sensitive = true,
            )
        }

        attr(ExifInterface.TAG_SOFTWARE)?.let {
            out += PrivacyFinding(
                Icons.Filled.Edit,
                res.getString(R.string.privacy_software_title),
                it,
                sensitive = true,
            )
        }
    }
    if (out.isEmpty()) {
        out += PrivacyFinding(
            Icons.Filled.CheckCircle,
            context.getString(R.string.privacy_no_metadata_title),
            context.getString(R.string.privacy_no_metadata_body),
            sensitive = false,
        )
    }
    return out
}
