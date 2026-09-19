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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SliderDefaults
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

private val Ink = Color(0xFF0B0B0C)      // preview surround
private val Panel = Color(0xFF161719)    // controls panel
private val OnDark = Color(0xFFF2F3F5)   // primary text on the panel
private val OnDarkDim = Color(0xB3F2F3F5) // secondary text
private val Accent = Color(0xFF7EC8FF)   // cool accent that reads on dark

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
            onDng = { showExport = false; viewModel.exportDng() },
        )
    }
}

private enum class DevTab(val label: String) { LIGHT("Light"), TONE("Tone"), COLOR("Color"), WB("White bal."), DETAIL("Detail") }

@Composable
private fun Controls(params: DevelopParams, onChange: (DevelopParams) -> Unit) {
    var tab by remember { mutableStateOf(DevTab.LIGHT) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .navigationBarsPadding(),
    ) {
        // Tab strip
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DevTab.entries.forEach { t -> DarkChip(selected = tab == t, label = t.label) { tab = t } }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp, max = 260.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            when (tab) {
                DevTab.LIGHT -> {
                    SliderRow("Exposure", "%+.2f EV".format(params.exposure), params.exposure, -3f..3f) { onChange(params.copy(exposure = it)) }
                    SliderRow("Brightness", "%.2fx".format(params.bright), params.bright, 0.3f..2.5f) { onChange(params.copy(bright = it)) }
                    LabelRow("Highlights")
                    ChipRow(listOf(0 to "Clip", 2 to "Blend", 3 to "Rebuild"), params.highlight) { onChange(params.copy(highlight = it)) }
                    if (params.highlight >= 3) {
                        SliderRow("Rebuild strength", "${params.highlightLevel}", params.highlightLevel.toFloat(), 3f..9f) { onChange(params.copy(highlightLevel = it.roundToInt())) }
                    }
                }
                DevTab.TONE -> {
                    SliderRow("Contrast", sign(params.contrast), params.contrast, -100f..100f) { onChange(params.copy(contrast = it)) }
                    SliderRow("Highlights", sign(params.highlightsTone), params.highlightsTone, -100f..100f) { onChange(params.copy(highlightsTone = it)) }
                    SliderRow("Shadows", sign(params.shadows), params.shadows, -100f..100f) { onChange(params.copy(shadows = it)) }
                    SliderRow("Whites", sign(params.whites), params.whites, -100f..100f) { onChange(params.copy(whites = it)) }
                    SliderRow("Blacks", sign(params.blacks), params.blacks, -100f..100f) { onChange(params.copy(blacks = it)) }
                }
                DevTab.COLOR -> {
                    SliderRow("Saturation", sign(params.saturation), params.saturation, -100f..100f) { onChange(params.copy(saturation = it)) }
                    SliderRow("Vibrance", sign(params.vibrance), params.vibrance, -100f..100f) { onChange(params.copy(vibrance = it)) }
                    LabelRow("Output color space")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorSpace.entries.forEach { cs -> DarkChip(selected = params.colorSpace == cs, label = cs.label) { onChange(params.copy(colorSpace = cs)) } }
                    }
                }
                DevTab.WB -> {
                    LabelRow("White balance")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(RawWb.CAMERA to "As shot", RawWb.AUTO to "Auto", RawWb.CUSTOM to "Custom").forEach { (wb, label) ->
                            DarkChip(selected = params.wb == wb, label = label) { onChange(params.copy(wb = wb)) }
                        }
                    }
                    if (params.wb == RawWb.CUSTOM) {
                        SliderRow("Temperature", "${params.temp.roundToInt()} K", params.temp, 2500f..10000f) { onChange(params.copy(temp = it)) }
                        SliderRow("Tint", if (params.tint >= 0) "Magenta" else "Green", params.tint, -100f..100f) { onChange(params.copy(tint = it)) }
                    } else {
                        LabelRow(if (params.wb == RawWb.AUTO) "Auto-balanced from the whole frame." else "Using the camera's as-shot balance.")
                    }
                }
                DevTab.DETAIL -> {
                    LabelRow("Demosaic")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Demosaic.entries.forEach { dm -> DarkChip(selected = params.demosaic == dm, label = dm.label) { onChange(params.copy(demosaic = dm)) } }
                    }
                    LabelRow("Noise reduction")
                    ChipRow(listOf(0 to "Off", 1 to "Light", 2 to "Full"), params.fbdd) { onChange(params.copy(fbdd = it)) }
                    SliderRow("Wavelet NR", if (params.threshold <= 0) "Off" else "${params.threshold.roundToInt()}", params.threshold, 0f..1000f) { onChange(params.copy(threshold = it)) }
                }
            }
        }
    }
}

private fun sign(v: Float): String = if (v > 0) "+${v.roundToInt()}" else "${v.roundToInt()}"

@Composable
private fun ChipRow(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) -> DarkChip(selected = selected == v, label = label) { onSelect(v) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DarkChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = OnDarkDim,
            selectedContainerColor = Accent.copy(alpha = 0.20f),
            selectedLabelColor = Accent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.White.copy(alpha = 0.22f),
            selectedBorderColor = Accent.copy(alpha = 0.55f),
        ),
    )
}

@Composable
private fun LabelRow(label: String) {
    Text(label, color = OnDarkDim, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun SliderRow(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = OnDark, style = MaterialTheme.typography.titleSmall)
        Text(value, color = Accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value = current,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = Accent,
            activeTrackColor = Accent,
            inactiveTrackColor = Color.White.copy(alpha = 0.14f),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(onDismiss: () -> Unit, onJpeg: () -> Unit, onTiff: () -> Unit, onDng: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Export developed image", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            Text("Rendered from the sensor at full resolution.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 18.dp))
            Button(onClick = onJpeg, modifier = Modifier.fillMaxWidth()) { Text("JPEG  ·  8-bit, quality 95") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onTiff, modifier = Modifier.fillMaxWidth()) { Text("TIFF  ·  16-bit, full range") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onDng, modifier = Modifier.fillMaxWidth()) { Text("DNG  ·  raw sensor data, archival") }
            Text("DNG keeps the original mosaiced sensor data and camera color, editable anywhere. Develop edits above are not baked in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
