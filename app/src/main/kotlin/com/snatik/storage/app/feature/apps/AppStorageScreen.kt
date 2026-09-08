package com.snatik.storage.app.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.AppFootprint
import com.snatik.storage.core.apps.AppStorageAnalyzer
import com.snatik.storage.core.apps.StorageSlice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

class AppStorageViewModel(private val packageName: String, private val analyzer: AppStorageAnalyzer) : ViewModel() {
    private val _footprint = MutableStateFlow<AppFootprint?>(null)
    val footprint: StateFlow<AppFootprint?> = _footprint.asStateFlow()
    init { viewModelScope.launch { _footprint.value = analyzer.analyze(packageName) } }
}

private val SLICE_COLORS = listOf(
    Color(0xFF4F86C6), Color(0xFF57A773), Color(0xFFE0A458), Color(0xFFB56576),
    Color(0xFF8367C7), Color(0xFF4CB5AE), Color(0xFF9AA0A6),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppStorageScreen(packageName: String, onBack: () -> Unit, viewModel: AppStorageViewModel = koinViewModel { parametersOf(packageName) }) {
    val footprint by viewModel.footprint.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_storage_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val fp = footprint
            when {
                fp == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> {
                    val slices = fp.nonEmpty
                    val total = fp.total.coerceAtLeast(1)
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AppIcon(packageName, size = 40.dp)
                                Column {
                                    Text(human(fp.total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.app_storage_total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        // stacked bar
                        item {
                            Row(modifier = Modifier.fillMaxWidth().height(28.dp).clip(MaterialTheme.shapes.small)) {
                                slices.forEachIndexed { i, s ->
                                    Spacer(modifier = Modifier.weight((s.bytes.toFloat() / total).coerceAtLeast(0.001f)).fillMaxHeight().background(SLICE_COLORS[i % SLICE_COLORS.size]))
                                }
                            }
                        }
                        items(slices.size) { i -> SliceRow(slices[i], SLICE_COLORS[i % SLICE_COLORS.size], fp.total) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliceRow(slice: StorageSlice, color: Color, total: Long) {
    val pct = if (total > 0) (slice.bytes * 100.0 / total).roundToInt() else 0
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(14.dp).clip(MaterialTheme.shapes.extraSmall).background(color))
        Column(modifier = Modifier.weight(1f)) {
            Text(slice.label, style = MaterialTheme.typography.bodyLarge)
            if (slice.path.isNotEmpty()) Text(slice.path, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(human(slice.bytes), style = MaterialTheme.typography.bodyMedium)
            Text("$pct%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun human(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return "${(v * 10).roundToInt() / 10.0} ${units[i]}"
}
