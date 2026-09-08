package com.snatik.storage.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.snatik.storage.core.apps.AppRepository
import com.snatik.storage.core.apps.PermissionMatrixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One app's dangerous-permission count against its on-device footprint. */
data class PermFootprintRow(val packageName: String, val label: String, val dangerousGranted: Int, val bytes: Long)

class PermissionFootprintViewModel(
    private val matrix: PermissionMatrixRepository,
    private val apps: AppRepository,
) : ViewModel() {
    private val _rows = MutableStateFlow<List<PermFootprintRow>?>(null)
    val rows: StateFlow<List<PermFootprintRow>?> = _rows.asStateFlow()

    init {
        viewModelScope.launch {
            val m = matrix.matrix(includeSystem = false)
            val sizes = runCatching { apps.list().associate { it.packageName to (it.storage?.totalBytes ?: 0L) } }.getOrDefault(emptyMap())
            _rows.value = m.apps
                .map { PermFootprintRow(it.packageName, it.label, it.granted.size, sizes[it.packageName] ?: 0L) }
                .sortedWith(compareByDescending<PermFootprintRow> { it.dangerousGranted }.thenByDescending { it.bytes })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionFootprintScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit, viewModel: PermissionFootprintViewModel = org.koin.androidx.compose.koinViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permfoot_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val list = rows
            if (list == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val maxPerm = (list.maxOfOrNull { it.dangerousGranted } ?: 1).coerceAtLeast(1)
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    item {
                        Text(stringResource(R.string.permfoot_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 4.dp))
                    }
                    items(list.size) { i -> RowItem(list[i], maxPerm, onOpenApp) }
                }
            }
        }
    }
}

@Composable
private fun RowItem(r: PermFootprintRow, maxPerm: Int, onOpenApp: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpenApp(r.packageName) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(r.packageName, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(r.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // permission bar
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(modifier = Modifier.fillMaxWidth(r.dangerousGranted.toFloat() / maxPerm).height(10.dp).background(MaterialTheme.colorScheme.error))
                }
                Text("${r.dangerousGranted}", style = MonoStyle, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(human(r.bytes), style = MonoStyle, fontWeight = FontWeight.SemiBold)
    }
}

private fun human(bytes: Long): String {
    if (bytes <= 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return "${(v * 10).roundToInt() / 10.0} ${units[i]}"
}
