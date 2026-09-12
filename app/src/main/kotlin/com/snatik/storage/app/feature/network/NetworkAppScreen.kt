package com.snatik.storage.app.feature.network

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.app.util.relativeTime
import com.snatik.storage.core.apps.AppNetworkUsage
import com.snatik.storage.core.apps.Connection
import com.snatik.storage.core.apps.Protocol
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkAppScreen(packageName: String, onBack: () -> Unit, onOpenGraph: () -> Unit = {}, onOpenSettings: () -> Unit = {}, viewModel: NetworkViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = state.forPackage(packageName)

    val scroll = rememberScrollState()
    var headerHeightPx by remember { mutableStateOf(1f) }
    val topPadPx = with(LocalDensity.current) { 16.dp.toPx() }
    // 0 while the big page header is fully visible; 1 once it has scrolled under the bar.
    val fraction = (scroll.value / (headerHeightPx + topPadPx)).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(contentAlignment = Alignment.CenterStart) {
                        Text(stringResource(R.string.net_title), modifier = Modifier.alpha(1f - fraction))
                        if (app != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.alpha(fraction),
                            ) {
                                AppIcon(app.packageName, size = 30.dp)
                                Column {
                                    Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.settings_title)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && app == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                app == null -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Lan, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.net_no_history), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.net_no_history_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Detail(app, state.hostNames, state.orgNames, scroll, fraction, onOpenGraph) { headerHeightPx = it }
            }
        }
    }
}

@Composable
private fun Detail(
    app: AppNetworkUsage,
    hosts: Map<String, String>,
    orgs: Map<String, String>,
    scroll: androidx.compose.foundation.ScrollState,
    headerFraction: Float,
    onOpenGraph: () -> Unit,
    onHeaderHeight: (Float) -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Identity — crossfades up into the top bar as it scrolls away.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.alpha(1f - headerFraction).onSizeChanged { onHeaderHeight(it.height.toFloat()) },
        ) {
            AppIcon(app.packageName, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleLarge)
                Text(app.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }

        // Received / Sent headline
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DirMetric(Icons.AutoMirrored.Filled.CallReceived, stringResource(R.string.net_received), app.rxBytes, app.rxPackets, Modifier.weight(1f))
                    DirMetric(Icons.AutoMirrored.Filled.CallMade, stringResource(R.string.net_sent), app.txBytes, app.txPackets, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.net_total_data), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(app.totalBytes.readableSize(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // Usage over time — only when the shell gave us enough buckets to form a trend. A uid-2000
        // shell usually sees just the current "since boot" bucket, which is not a series.
        val chartBuckets = app.buckets.filter { it.totalBytes > 0 }
        if (chartBuckets.size >= 4) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.net_over_time))
                Text(stringResource(R.string.net_tap_expand), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Box(modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onOpenGraph)) {
                UsageChart(chartBuckets)
            }
        }

        // Transport split
        if (app.wifiBytes + app.mobileBytes > 0) {
            SectionTitle(stringResource(R.string.net_transport))
            SplitCard(
                aLabel = stringResource(R.string.net_wifi), aBytes = app.wifiBytes, aColor = MaterialTheme.colorScheme.primary,
                bLabel = stringResource(R.string.net_mobile), bBytes = app.mobileBytes, bColor = MaterialTheme.colorScheme.tertiary,
            )
        }

        // Foreground / background split
        if (app.foregroundBytes + app.backgroundBytes > 0) {
            SectionTitle(stringResource(R.string.net_app_state))
            SplitCard(
                aLabel = stringResource(R.string.net_foreground), aBytes = app.foregroundBytes, aColor = MaterialTheme.colorScheme.primary,
                bLabel = stringResource(R.string.net_background), bBytes = app.backgroundBytes, bColor = MaterialTheme.colorScheme.secondary,
            )
        }

        // Recorded window
        if (app.firstMs in 1 until Long.MAX_VALUE) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.net_first_seen, app.firstMs.relativeTime(ctx)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (app.lastMs > 0) Text(stringResource(R.string.net_last_active, app.lastMs.relativeTime(ctx)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Live connections
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.net_live))
                if (app.connections.isNotEmpty()) Tag(stringResource(R.string.net_live_count, app.connections.size), MaterialTheme.colorScheme.primary)
            }
            if (app.connections.isNotEmpty()) {
                val tally = app.connections.groupingBy { serviceType(it.remotePort, it.protocol) }.eachCount()
                    .entries.sortedByDescending { it.value }.joinToString("  ·  ") { "${it.key} ×${it.value}" }
                Text(tally, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (app.connections.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.net_live_none), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.net_live_none_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            app.connections.forEach { conn -> ConnectionCard(conn, hosts[conn.remoteAddress], orgs[conn.remoteAddress]) }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DirMetric(icon: ImageVector, label: String, bytes: Long, packets: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(bytes.readableSize(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(stringResource(R.string.net_packets, packets.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

/** Stacked rx/tx bars over the most recent buckets. */
@Composable
private fun UsageChart(all: List<com.snatik.storage.core.apps.UsageBucket>) {
    val buckets = all.takeLast(48)
    // Clamp the scale to the 90th percentile so one initial-sync spike doesn't flatten the rest.
    val sorted = buckets.map { it.totalBytes }.sorted()
    val max = sorted[(sorted.size * 0.9f).toInt().coerceIn(0, sorted.lastIndex)].coerceAtLeast(1L)
    val rx = MaterialTheme.colorScheme.primary
    val tx = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
                val n = buckets.size
                if (n == 0) return@Canvas
                val gap = 2.dp.toPx()
                val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
                buckets.forEachIndexed { i, b ->
                    val x = i * (bw + gap)
                    // faint track for empty buckets
                    drawRoundRect(color = track, topLeft = androidx.compose.ui.geometry.Offset(x, size.height - 2f), size = androidx.compose.ui.geometry.Size(bw, 2f))
                    // Scale rx+tx together, clamped to the drawing height (spikes above the cap clip flat).
                    val scale = (size.height * (b.totalBytes.toFloat() / max)).coerceAtMost(size.height) / b.totalBytes.coerceAtLeast(1L)
                    val rxH = b.rxBytes * scale
                    val txH = b.txBytes * scale
                    if (txH > 0f) drawRect(color = tx, topLeft = androidx.compose.ui.geometry.Offset(x, size.height - txH - rxH), size = androidx.compose.ui.geometry.Size(bw, txH))
                    if (rxH > 0f) drawRect(color = rx, topLeft = androidx.compose.ui.geometry.Offset(x, size.height - rxH), size = androidx.compose.ui.geometry.Size(bw, rxH))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(rx, stringResource(R.string.net_received))
                LegendDot(tx, stringResource(R.string.net_sent))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SplitCard(aLabel: String, aBytes: Long, aColor: Color, bLabel: String, bBytes: Long, bColor: Color) {
    val total = (aBytes + bBytes).coerceAtLeast(1L)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(track)) {
                val aw = aBytes.toFloat() / total
                val bwf = bBytes.toFloat() / total
                if (aw > 0f) Box(modifier = Modifier.fillMaxSize().weight(aw.coerceAtLeast(0.001f)).background(aColor))
                if (bwf > 0f) Box(modifier = Modifier.fillMaxSize().weight(bwf.coerceAtLeast(0.001f)).background(bColor))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SplitLegend(aColor, aLabel, aBytes)
                SplitLegend(bColor, bLabel, bBytes)
            }
        }
    }
}

@Composable
private fun SplitLegend(color: Color, label: String, bytes: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(bytes.readableSize(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ConnectionCard(conn: Connection, host: String?, org: String?) {
    var expanded by rememberSaveable(conn.inode, conn.remoteAddress, conn.remotePort) { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    if (org != null) Text(org, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val display = host ?: if (conn.ipv6) "[${conn.remoteAddress}]" else conn.remoteAddress
                    Text("$display:${conn.remotePort}", style = MonoStyle.copy(color = if (org != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    if (host != null && org != null) Text(conn.remoteAddress, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Tag(serviceType(conn.remotePort, conn.protocol), MaterialTheme.colorScheme.tertiary)
                val established = conn.state == "ESTABLISHED"
                Tag(stateLabel(conn.state), if (established) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (org != null) Line(stringResource(R.string.net_owner), org)
                    Line(stringResource(R.string.net_protocol), "${conn.protocol}${if (conn.ipv6) "6" else ""}")
                    Line(stringResource(R.string.net_local), endpoint(conn.localAddress, conn.localPort, conn.ipv6))
                    Line(stringResource(R.string.net_remote), endpoint(conn.remoteAddress, conn.remotePort, conn.ipv6))
                    Line(stringResource(R.string.net_state), conn.state.lowercase())
                    if (conn.protocol == Protocol.TCP) {
                        Line(stringResource(R.string.net_retransmits), conn.retransmits.toString())
                        Line(stringResource(R.string.net_queue), "${conn.txQueue} / ${conn.rxQueue}")
                    }
                }
            }
        }
    }
}

private fun endpoint(address: String, port: Int, ipv6: Boolean): String =
    if (ipv6) "[$address]:$port" else "$address:$port"

/** Best-effort service name from the remote port — the only "type" a raw socket exposes. */
private fun serviceType(port: Int, protocol: Protocol): String = when (port) {
    443 -> if (protocol == Protocol.UDP) "QUIC" else "HTTPS"
    80 -> "HTTP"
    53 -> "DNS"
    853 -> "DoT"
    993 -> "IMAPS"
    995 -> "POP3S"
    465, 587 -> "SMTP"
    5222, 5223, 5228, 5229, 5230 -> "Push"      // XMPP / FCM
    3478, 3479, 5349, 5350 -> "STUN/TURN"        // calls / WebRTC
    8009 -> "Cast"
    1900 -> "SSDP"
    123 -> "NTP"
    22 -> "SSH"
    else -> "$protocol:$port"
}

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoStyle, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun stateLabel(state: String): String = when (state) {
    "ESTABLISHED" -> stringResource(R.string.net_state_established)
    "SYN_SENT", "SYN_RECV" -> stringResource(R.string.net_state_syn_sent)
    "TIME_WAIT", "FIN_WAIT1", "FIN_WAIT2", "CLOSING", "LAST_ACK" -> stringResource(R.string.net_state_time_wait)
    "CLOSE_WAIT" -> stringResource(R.string.net_state_close_wait)
    "LISTEN" -> stringResource(R.string.net_state_listen)
    else -> state.lowercase()
}
