package com.snatik.storage.app.feature.intents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentExamplesScreen(
    onBack: () -> Unit,
    onOpenExample: (String) -> Unit,
    viewModel: IntentExamplesViewModel = koinViewModel(),
) {
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val activitiesLabel = stringResource(R.string.builder_activity)
    val broadcastsLabel = stringResource(R.string.builder_broadcast)
    val servicesLabel = stringResource(R.string.builder_service)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.intent_examples_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text(stringResource(R.string.intent_examples_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            exampleGroup(activitiesLabel, IntentExamples.activities, counts, onOpenExample)
            exampleGroup(broadcastsLabel, IntentExamples.broadcasts, counts, onOpenExample)
            exampleGroup(servicesLabel, IntentExamples.services, counts, onOpenExample)
        }
    }
}

private fun LazyListScope.exampleGroup(
    label: String,
    examples: List<IntentExamples.Example>,
    counts: Map<String, Int>,
    onOpenExample: (String) -> Unit,
) {
    item(key = "g:$label") {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        )
    }
    items(examples, key = { "ex:" + it.name }) { ex ->
        ExampleRow(ex, counts[ex.name], onClick = { onOpenExample(ex.spec.toJson()) })
    }
}

@Composable
private fun ExampleRow(example: IntentExamples.Example, count: Int?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(example.name, style = MaterialTheme.typography.bodyLarge)
            Text(example.summary, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            count == null -> Unit
            count > 0 -> Tag(pluralStringResource(R.plurals.builder_pick_apps, count, count), MaterialTheme.colorScheme.tertiary)
            else -> Tag(stringResource(R.string.intent_examples_no_match), MaterialTheme.colorScheme.outline)
        }
    }
}
