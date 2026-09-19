package com.snatik.storage.app.feature.media

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

private val Ink = Color(0xFF0B0B0C)
private val Panel = Color(0xFF161618)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopScreen(path: String, onBack: () -> Unit, viewModel: DevelopViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showExport by remember { mutableStateOf(false) }

    LaunchedEffect(s.exportMsg) {
        s.exportMsg?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessage() }
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Develop", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                },
                actions = {
                    if (s.params != DevelopParams()) {
                        IconButton(onClick = { viewModel.update(DevelopParams()) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White)
                        }
                    }
                    TextButton(onClick = { showExport = true }, enabled = !s.failed && !s.loading) {
                        Text("Export", color = if (!s.failed && !s.loading) MaterialTheme.colorScheme.primary else Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Preview
            Box(Modifier.fillMaxWidth().weight(1f).background(Ink), contentAlignment = Alignment.Center) {
                val preview = s.preview
                if (preview != null && !preview.isRecycled) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    )
                }
                when {
                    s.failed -> Text("Could not develop this RAW.", color = Color.White)
                    s.loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("Unpacking sensor data", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (s.rendering && !s.loading) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).width(22.dp).height(22.dp))
                }
                if (s.exporting) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            Text("Developing at full resolution", color = Color.White)
                        }
                    }
                }
            }

            if (!s.failed) Controls(s.params, onChange = viewModel::update)
        }
    }

    if (showExport) {
        ExportSheet(
            onDismiss = { showExport = false },
            onJpeg = { showExport = false; viewModel.exportJpeg() },
            onTiff = { showExport = false; viewModel.exportTiff() },
        )
    }
}

@Composable
private fun Controls(params: DevelopParams, onChange: (DevelopParams) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SliderRow("Exposure", "%+.2f EV".format(params.exposure), params.exposure, -3f..3f) { onChange(params.copy(exposure = it)) }

        LabelRow("Highlights")
        val hlOptions = listOf(0 to "Clip", 2 to "Blend", 3 to "Rebuild")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hlOptions.forEach { (v, label) ->
                FilterChip(selected = params.highlight == v, onClick = { onChange(params.copy(highlight = v)) }, label = { Text(label) })
            }
        }

        LabelRow("White balance")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RawWb.entries.forEach { wb ->
                FilterChip(selected = params.wb == wb, onClick = { onChange(params.copy(wb = wb)) }, label = { Text(wb.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        if (params.wb == RawWb.CUSTOM) {
            SliderRow("Temperature", if (params.wbTemp >= 0) "Warm" else "Cool", params.wbTemp, -1f..1f) { onChange(params.copy(wbTemp = it)) }
        }

        SliderRow("Brightness", "%.2fx".format(params.bright), params.bright, 0.3f..2.5f) { onChange(params.copy(bright = it)) }
    }
}

@Composable
private fun LabelRow(label: String) {
    Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun SliderRow(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
        Text(value, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
    Slider(value = current, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(onDismiss: () -> Unit, onJpeg: () -> Unit, onTiff: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Export developed image", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            Text("Rendered from the sensor at full resolution.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 18.dp))
            Button(onClick = onJpeg, modifier = Modifier.fillMaxWidth()) { Text("JPEG  ·  8-bit, quality 95") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onTiff, modifier = Modifier.fillMaxWidth()) { Text("TIFF  ·  16-bit, full range") }
        }
    }
}
