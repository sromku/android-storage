package com.snatik.storage.app.feature.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.util.readableSize
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TYPE_COLORS = listOf(Color(0xFF3B82F6), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF8B5CF6), Color(0xFFEC4899))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoInsightsScreen(onBack: () -> Unit, viewModel: PhotoInsightsViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.photoinsights_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        if (s.loading) {
            Box(Modifier.fillMaxWidth().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(padding),
        ) {
            item { Hero(s) }
            item { TypeCard(s.types) }
            if (s.months.isNotEmpty()) item { TimelineCard(s.months) }
            if (s.folders.isNotEmpty()) item { FoldersCard(s.folders) }
            if (s.largest.isNotEmpty()) item { LargestCard(s.largest) }
            item { CamerasCard(s) }
        }
    }
}

@Composable
private fun Hero(s: InsightsData) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(s.total.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.photoinsights_items_size, s.totalBytes.readableSize()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.oldest > 0) {
                val fmt = DateTimeFormatter.ofPattern("MMM yyyy")
                val zone = ZoneId.systemDefault()
                fun f(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(fmt)
                Text(
                    stringResource(R.string.photoinsights_span, f(s.oldest), f(s.newest)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TypeCard(types: List<TypeStat>) {
    if (types.isEmpty()) return
    val total = types.sumOf { it.count }.coerceAtLeast(1)
    SectionCard(stringResource(R.string.photoinsights_by_type)) {
        // Stacked proportion bar.
        Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))) {
            types.forEachIndexed { i, t ->
                Box(Modifier.weight(t.count.toFloat().coerceAtLeast(0.001f)).fillMaxWidth().height(16.dp).background(TYPE_COLORS[i % TYPE_COLORS.size]))
            }
        }
        Spacer(Modifier.height(12.dp))
        types.forEachIndexed { i, t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(TYPE_COLORS[i % TYPE_COLORS.size]))
                Text(t.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Text("${t.count}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("  ·  ${t.bytes.readableSize()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  ·  ${t.count * 100 / total}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TimelineCard(months: List<MonthStat>) {
    SectionCard(stringResource(R.string.photoinsights_timeline)) {
        val max = months.maxOf { it.count }.coerceAtLeast(1)
        val barColor = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val gap = 4.dp.toPx()
            val bw = (size.width - gap * (months.size - 1)) / months.size
            months.forEachIndexed { i, m ->
                val h = size.height * (m.count.toFloat() / max)
                val x = i * (bw + gap)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, size.height - h),
                    size = androidx.compose.ui.geometry.Size(bw, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(months.first().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(months.last().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FoldersCard(folders: List<FolderStat>) {
    SectionCard(stringResource(R.string.photoinsights_folders)) {
        val max = folders.maxOf { it.bytes }.coerceAtLeast(1)
        folders.forEach { f ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(f.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text("${f.count} · ${f.bytes.readableSize()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(f.bytes.toFloat() / max).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@Composable
private fun LargestCard(largest: List<MediaItem>) {
    SectionCard(stringResource(R.string.photoinsights_largest)) {
        largest.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(m.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                Text(m.size.readableSize(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CamerasCard(s: InsightsData) {
    SectionCard(stringResource(R.string.photoinsights_metadata)) {
        if (s.exifLoading && s.cameras.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.photoinsights_reading_exif), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.photoinsights_geotagged, s.geotagged), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
        }
        if (s.cameras.isNotEmpty()) {
            Text(stringResource(R.string.photoinsights_cameras), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            s.cameras.forEach { c -> StatRow(c.name, c.count) }
        }
        if (s.lenses.isNotEmpty()) {
            Text(stringResource(R.string.photoinsights_lenses), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            s.lenses.forEach { l -> StatRow(l.name, l.count) }
        }
    }
}

@Composable
private fun StatRow(name: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}
