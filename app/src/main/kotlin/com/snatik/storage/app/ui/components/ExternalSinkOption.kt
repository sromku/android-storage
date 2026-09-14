package com.snatik.storage.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.ExternalSink
import kotlinx.coroutines.launch

private enum class TestState { IDLE, TESTING, OK, FAIL }

/**
 * A recording sheet's "also stream to an external collector" control. Shows the current setup,
 * lets the user flip streaming on/off, test the connection, or — when nothing is configured —
 * points them to Settings.
 */
@Composable
fun ExternalSinkOption(sink: ExternalSink, modifier: Modifier = Modifier) {
    val enabled by sink.enabledFlow.collectAsStateWithLifecycle()
    val url by sink.url.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var test by remember { mutableStateOf(TestState.IDLE) }
    val configured = url.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sink_option_title), style = MaterialTheme.typography.bodyLarge)
                if (configured) {
                    Text(url, style = MonoStyle.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
            }
            if (configured) {
                Switch(checked = enabled, onCheckedChange = { sink.setEnabled(it); test = TestState.IDLE })
            }
        }

        if (!configured) {
            Text(stringResource(R.string.sink_option_setup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (enabled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        test = TestState.TESTING
                        scope.launch { test = if (sink.test().isSuccess) TestState.OK else TestState.FAIL }
                    },
                    enabled = test != TestState.TESTING,
                ) { Text(stringResource(R.string.sink_option_test)) }
                when (test) {
                    TestState.TESTING -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    TestState.OK -> StatusLine(Icons.Default.CheckCircle, stringResource(R.string.sink_option_ok), MaterialTheme.colorScheme.primary)
                    TestState.FAIL -> StatusLine(Icons.Default.ErrorOutline, stringResource(R.string.sink_option_fail), MaterialTheme.colorScheme.error)
                    TestState.IDLE -> {}
                }
            }
        } else {
            Text(stringResource(R.string.sink_option_off), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
