package com.snatik.storage.app.feature.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertSheet(count: Int, onDismiss: () -> Unit, onConvert: (ConvertOptions) -> Unit) {
    var quality by remember { mutableFloatStateOf(95f) }
    var maxDim by remember { mutableIntStateOf(0) }
    var keepMeta by remember { mutableStateOf(false) }
    val sizes = listOf(0 to R.string.convert_size_original, 4000 to R.string.convert_size_4000, 2048 to R.string.convert_size_2048, 1024 to R.string.convert_size_1024)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp),
        ) {
            Text(stringResource(R.string.convert_title, count), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.convert_quality), style = MaterialTheme.typography.titleSmall)
                Text("${quality.roundToInt()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Slider(value = quality, onValueChange = { quality = it }, valueRange = 50f..100f, modifier = Modifier.fillMaxWidth())

            Text(stringResource(R.string.convert_max_size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sizes.forEach { (px, label) ->
                    FilterChip(selected = maxDim == px, onClick = { maxDim = px }, label = { Text(stringResource(label)) })
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.convert_keep_meta), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.convert_keep_meta_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = keepMeta, onCheckedChange = { keepMeta = it })
            }

            Button(
                onClick = { onConvert(ConvertOptions(quality.roundToInt(), maxDim, keepMeta)) },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) { Text(stringResource(R.string.convert_action, count)) }
        }
    }
}
