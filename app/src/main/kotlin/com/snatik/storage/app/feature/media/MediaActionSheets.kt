package com.snatik.storage.app.feature.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import com.snatik.storage.app.util.readableSize

/** One tappable action. [trailingCheck] shows an active toggle; [chevron] flags "opens more". */
@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingCheck: Boolean = false,
    chevron: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        when {
            trailingCheck -> Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            chevron -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SheetHeader(item: MediaItem) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 10.dp)) {
        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        val kind = if (item.isVideo) stringResource(R.string.kind_video_one) else stringResource(R.string.kind_photo)
        Text("$kind  ·  ${item.size.readableSize()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The primary action menu, grouped: inspect / edit / share / delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagerActionSheet(
    item: MediaItem,
    isRaw: Boolean,
    canTile: Boolean,
    onDismiss: () -> Unit,
    onInspect: () -> Unit,
    onDetails: () -> Unit,
    onDevelop: () -> Unit,
    onEdit: () -> Unit,
    onFullRes: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShareSheet: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            SheetHeader(item)
            HorizontalDivider(Modifier.padding(bottom = 4.dp))

            if (item.isVideo) {
                ActionRow(Icons.Default.Info, stringResource(R.string.details), onClick = onDetails)
            } else {
                ActionRow(
                    Icons.Default.ManageSearch,
                    stringResource(R.string.action_inspect),
                    subtitle = stringResource(R.string.action_inspect_sub),
                    chevron = true,
                    onClick = onInspect,
                )
            }
            if (isRaw) ActionRow(Icons.Default.Tune, stringResource(R.string.develop_raw), subtitle = stringResource(R.string.develop_raw_sub), onClick = onDevelop)
            if (!item.isVideo && !isRaw) ActionRow(Icons.Default.Crop, stringResource(R.string.edit_crop), subtitle = stringResource(R.string.edit_crop_sub), onClick = onEdit)
            if (canTile) ActionRow(Icons.Default.ZoomIn, stringResource(R.string.full_resolution), onClick = onFullRes)

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ActionRow(Icons.Default.Edit, stringResource(R.string.rename), onClick = onRename)
            ActionRow(Icons.Default.DriveFileMove, stringResource(R.string.move_to_folder), onClick = onMove)

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ActionRow(
                Icons.Default.Share,
                stringResource(R.string.action_share_send),
                subtitle = stringResource(R.string.action_share_send_sub),
                chevron = true,
                onClick = onShareSheet,
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ActionRow(Icons.Default.Delete, stringResource(R.string.delete), destructive = true, onClick = onDelete)
        }
    }
}

/** Follow-up sheet: everything that inspects the image (info + live overlays). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectSheet(
    showHistogram: Boolean,
    analysis: AnalysisMode,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onToggleHistogram: () -> Unit,
    onToggleClipping: () -> Unit,
    onTogglePeaking: () -> Unit,
    onPrivacy: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.action_inspect),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 10.dp),
            )
            HorizontalDivider(Modifier.padding(bottom = 4.dp))
            ActionRow(Icons.Default.Info, stringResource(R.string.details), onClick = onDetails)
            ActionRow(Icons.Default.BarChart, stringResource(R.string.histogram), subtitle = stringResource(R.string.inspect_histogram_sub), trailingCheck = showHistogram, onClick = onToggleHistogram)
            ActionRow(Icons.Default.Contrast, stringResource(R.string.analysis_clipping), subtitle = stringResource(R.string.inspect_clipping_sub), trailingCheck = analysis == AnalysisMode.CLIPPING, onClick = onToggleClipping)
            ActionRow(Icons.Default.FilterCenterFocus, stringResource(R.string.analysis_peaking), subtitle = stringResource(R.string.inspect_peaking_sub), trailingCheck = analysis == AnalysisMode.PEAKING, onClick = onTogglePeaking)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ActionRow(Icons.Default.Shield, stringResource(R.string.privacy_report), onClick = onPrivacy)
        }
    }
}

/** Follow-up sheet: send this photo out (share, strip, open with, use as). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareActionSheet(
    isVideo: Boolean,
    onDismiss: () -> Unit,
    onShareOriginal: () -> Unit,
    onShareStripped: () -> Unit,
    onWifiShare: () -> Unit,
    onOpenWith: () -> Unit,
    onUseAs: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.action_share_send),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 10.dp),
            )
            HorizontalDivider(Modifier.padding(bottom = 4.dp))
            ActionRow(Icons.Default.Wifi, stringResource(R.string.wifi_share), subtitle = stringResource(R.string.wifi_share_sub_pager), onClick = onWifiShare)
            ActionRow(Icons.Default.Share, stringResource(R.string.share_original), onClick = onShareOriginal)
            if (!isVideo) ActionRow(Icons.Default.Shield, stringResource(R.string.share_stripped), subtitle = stringResource(R.string.share_stripped_sub), onClick = onShareStripped)
            ActionRow(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_with), onClick = onOpenWith)
            if (!isVideo) ActionRow(Icons.Default.Wallpaper, stringResource(R.string.use_as), onClick = onUseAs)
        }
    }
}

/** Library-level tools, reached from the Photos grid overflow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryToolsSheet(
    onDismiss: () -> Unit,
    onFindDuplicates: () -> Unit,
    onBrowseCloud: () -> Unit,
    onSyncCamera: () -> Unit,
    onInsights: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.library_tools),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 10.dp),
            )
            HorizontalDivider(Modifier.padding(bottom = 4.dp))
            ActionRow(Icons.Default.ContentCopy, stringResource(R.string.dupes_find), subtitle = stringResource(R.string.dupes_find_sub), onClick = onFindDuplicates)
            ActionRow(Icons.Default.Insights, stringResource(R.string.photoinsights_menu), subtitle = stringResource(R.string.photoinsights_menu_sub), onClick = onInsights)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ActionRow(Icons.Default.PhotoCamera, stringResource(R.string.camera_sync_menu), subtitle = stringResource(R.string.camera_sync_menu_sub), onClick = onSyncCamera)
            ActionRow(Icons.Default.Cloud, stringResource(R.string.cloud_browse), subtitle = stringResource(R.string.cloud_browse_sub), onClick = onBrowseCloud)
        }
    }
}
