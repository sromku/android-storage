package com.snatik.storage.app.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.StorageAccess
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.fs.Volume
import com.snatik.storage.core.fs.VolumeKind
import com.snatik.storage.core.shell.DebuggableApp
import com.snatik.storage.core.shell.PrivilegeState
import com.snatik.storage.core.shell.PrivilegeTier
import com.snatik.storage.core.shell.RootStatus
import com.snatik.storage.core.shell.ShizukuManager
import com.snatik.storage.core.shell.ShizukuStatus
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import android.content.Intent
import android.net.Uri
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenVolume: (label: String, path: String) -> Unit, viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Permission may have been granted in Settings while we were in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh(userInitiated = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.hasFullAccess) {
                    item(key = "permission") {
                        PermissionCard(
                            onGrant = {
                                if (!StorageAccess.openAllFilesSettings(context)) legacyLauncher.launch(StorageAccess.legacyPermissions)
                            },
                        )
                    }
                }
                item(key = "access") {
                    AccessCard(
                        privilege = state.privilege,
                        onRequestPermission = viewModel::requestShizukuPermission,
                        onConnect = viewModel::connectShizuku,
                        onOpenShizuku = {
                            val launch = context.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
                            context.startActivity(launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
                        },
                        onPreferRoot = viewModel::setPreferRoot,
                    )
                }
                item(key = "volumes-header") { SectionHeader(stringResource(R.string.section_volumes), topPadding = 12.dp) }
                items(state.volumes, key = { it.path }) { volume ->
                    val label = volume.label()
                    VolumeCard(volume, onClick = { onOpenVolume(label, volume.path) })
                }
                item(key = "app-header") { SectionHeader(stringResource(R.string.section_app_storage), topPadding = 12.dp) }
                items(state.appVolumes, key = { it.path }) { volume ->
                    val label = volume.label()
                    AppVolumeRow(volume, onClick = { onOpenVolume(label, volume.path) })
                }
                if (state.debuggableApps.isNotEmpty()) {
                    item(key = "debuggable-header") { SectionHeader(stringResource(R.string.section_debuggable), topPadding = 12.dp) }
                    items(state.debuggableApps, key = { "dbg:" + it.packageName }) { app ->
                        DebuggableAppRow(app, onClick = { onOpenVolume(app.label, app.dataDir) })
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = topPadding, bottom = 2.dp),
    )
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.permission_all_files_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.permission_all_files_body), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onGrant, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.permission_grant))
                }
            }
        }
    }
}

@Composable
private fun VolumeCard(volume: Volume, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = volume.isAvailable,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(volume.kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(volume.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(volume.path, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                if (volume.totalBytes > 0 && volume.kind != VolumeKind.SYSTEM_ROOT) {
                    LinearProgressIndicator(
                        progress = { volume.usedFraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Text(
                        stringResource(R.string.used_of, volume.usedBytes.readableSize(), volume.totalBytes.readableSize()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!volume.isAvailable) {
                    Text(stringResource(R.string.volume_unavailable), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AppVolumeRow(volume: Volume, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(volume.kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(volume.label(), style = MaterialTheme.typography.bodyLarge)
            Text(volume.path, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
}

@Composable
private fun AccessCard(
    privilege: PrivilegeState,
    onRequestPermission: () -> Unit,
    onConnect: () -> Unit,
    onOpenShizuku: () -> Unit,
    onPreferRoot: (Boolean) -> Unit,
) {
    val tierLabel = when (privilege.tier) {
        PrivilegeTier.NONE -> stringResource(R.string.access_tier_none)
        PrivilegeTier.SHIZUKU -> stringResource(R.string.access_tier_shizuku)
        PrivilegeTier.ROOT -> stringResource(R.string.access_tier_root)
    }
    val (statusText, action) = when (val s = privilege.shizuku) {
        ShizukuStatus.NotInstalled -> stringResource(R.string.shizuku_not_installed) to (stringResource(R.string.shizuku_get) to onOpenShizuku)
        ShizukuStatus.NotRunning -> stringResource(R.string.shizuku_not_running) to (stringResource(R.string.shizuku_open) to onOpenShizuku)
        is ShizukuStatus.PermissionRequired -> stringResource(R.string.shizuku_permission) to (stringResource(R.string.shizuku_allow) to onRequestPermission)
        ShizukuStatus.Connecting -> stringResource(R.string.shizuku_connecting) to null
        is ShizukuStatus.Connected -> stringResource(R.string.shizuku_connected, s.uid) to null
    }
    val privileged = privilege.isPrivileged
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (privileged) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (privileged) Icons.Default.Terminal else Icons.Default.Security, contentDescription = null)
                Text(tierLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Adb, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(statusText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (privilege.shizuku is ShizukuStatus.Connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else if (action != null) {
                    TextButton(onClick = action.second) { Text(action.first) }
                }
            }
            if (privilege.shizuku is ShizukuStatus.NotRunning) {
                Text(stringResource(R.string.shizuku_not_running_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val rootText = when {
                    !privilege.preferRoot -> stringResource(R.string.root_off)
                    privilege.root == RootStatus.AVAILABLE -> stringResource(R.string.root_available)
                    privilege.root == RootStatus.UNAVAILABLE -> stringResource(R.string.root_unavailable)
                    else -> stringResource(R.string.root_checking)
                }
                Text(rootText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = privilege.preferRoot, onCheckedChange = onPreferRoot)
            }
        }
    }
}

@Composable
private fun DebuggableAppRow(app: DebuggableApp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            Text(app.dataDir, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
}

private fun VolumeKind.icon(): ImageVector = when (this) {
    VolumeKind.SHARED -> Icons.Default.PhoneAndroid
    VolumeKind.SD_CARD -> Icons.Default.SdCard
    VolumeKind.USB -> Icons.Default.Usb
    VolumeKind.APP_FILES -> Icons.Default.Folder
    VolumeKind.APP_CACHE -> Icons.Default.Cached
    VolumeKind.APP_EXTERNAL -> Icons.Default.FolderSpecial
    VolumeKind.SYSTEM_ROOT -> Icons.Default.Terminal
}

@Composable
fun Volume.label(): String = when (kind) {
    VolumeKind.SHARED -> stringResource(R.string.volume_shared)
    VolumeKind.SD_CARD -> systemLabel.ifEmpty { stringResource(R.string.volume_sd_card) }
    VolumeKind.USB -> systemLabel.ifEmpty { stringResource(R.string.volume_usb) }
    VolumeKind.APP_FILES -> stringResource(R.string.volume_app_files)
    VolumeKind.APP_CACHE -> stringResource(R.string.volume_app_cache)
    VolumeKind.APP_EXTERNAL -> stringResource(R.string.volume_app_external)
    VolumeKind.SYSTEM_ROOT -> stringResource(R.string.volume_root)
}
