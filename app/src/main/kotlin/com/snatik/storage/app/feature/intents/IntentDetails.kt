package com.snatik.storage.app.feature.intents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.intents.IntentSpec
import com.snatik.storage.core.intents.intentFlagNames

/** Key/value listing of an intent, shared by the log and the monitors. */
@Composable
fun IntentDetails(spec: IntentSpec, clip: String? = null) {
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("action", spec.action ?: stringResource(R.string.intent_no_action))
            spec.data?.let { Field("data", it) }
            spec.type?.let { Field("type", it) }
            if (spec.categories.isNotEmpty()) Field("categories", spec.categories.joinToString("\n"))
            spec.packageName?.let { Field("package", it) }
            spec.className?.let { Field("class", it) }
            if (spec.flags != 0) Field("flags", "0x%x".format(spec.flags), chips = intentFlagNames(spec.flags))
            clip?.takeIf { it.isNotBlank() }?.let { Field("clip", it) }
            if (spec.extras.isNotEmpty()) {
                Text(
                    stringResource(R.string.intent_details_extras),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp,
                )
                spec.extras.forEach { e -> Field(e.key, e.value, type = e.type.name.lowercase()) }
            }
        }
    }
}

/**
 * One labelled field: an uppercase key eyebrow (with an optional value-type badge), the value in
 * mono below it so long and short values both read cleanly, and optional chips (used for flags).
 */
@Composable
private fun Field(label: String, value: String, type: String? = null, chips: List<String> = emptyList()) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
            type?.let { Tag(it, MaterialTheme.colorScheme.tertiary) }
        }
        if (value.isNotEmpty()) {
            Text(value, style = MonoStyle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
        }
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { Tag(it, MaterialTheme.colorScheme.secondary) }
            }
        }
    }
}
