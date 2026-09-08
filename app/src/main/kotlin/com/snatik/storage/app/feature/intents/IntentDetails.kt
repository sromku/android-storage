package com.snatik.storage.app.feature.intents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.intents.IntentSpec
import com.snatik.storage.core.intents.intentFlagNames

/** Key/value listing of an intent, shared by the sink, the log and the monitor. */
@Composable
fun IntentDetails(spec: IntentSpec, clip: String? = null) {
    SelectionContainer {
        Column {
            Line("action", spec.action ?: stringResource(R.string.intent_no_action))
            spec.data?.let { Line("data", it) }
            spec.type?.let { Line("type", it) }
            if (spec.categories.isNotEmpty()) Line("categories", spec.categories.joinToString("\n"))
            spec.packageName?.let { Line("package", it) }
            spec.className?.let { Line("class", it) }
            if (spec.flags != 0) Line("flags", "0x%x  ".format(spec.flags) + intentFlagNames(spec.flags).joinToString(" "))
            clip?.takeIf { it.isNotBlank() }?.let { Line("clip", it) }
            spec.extras.forEach { e -> Line("${e.key} · ${e.type.name.lowercase()}", e.value) }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(120.dp))
        Text(value, style = MonoStyle, modifier = Modifier.weight(1f))
    }
}
