package com.snatik.storage.app.feature.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.Intents
import com.snatik.storage.app.util.readableSize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontViewerScreen(path: String, onBack: () -> Unit, viewModel: FontViewerViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { IconButton(onClick = { Intents.openWith(context, path) }) { Icon(Icons.Default.OpenInNew, contentDescription = stringResource(R.string.open_with)) } },
            )
        },
    ) { padding ->
        when {
            state.loading -> androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding)) { EmptyState(Icons.Default.FontDownload, stringResource(R.string.font_failed), state.error) }
            else -> FontBody(state, Modifier.padding(padding), onOpenExternal = { Intents.openWith(context, path) })
        }
    }
}

@Composable
private fun FontBody(state: FontViewerState, modifier: Modifier, onOpenExternal: () -> Unit) {
    val family: FontFamily? = remember(state.typeface) {
        state.typeface?.let { tf -> FontFamily(tf) }
    }
    var sample by rememberSaveable { mutableStateOf("The quick brown fox jumps over the lazy dog") }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        if (family != null) {
            // Live specimen. The heading shows the font's own name set in the font.
            Text(
                state.info?.family ?: File(state.path).nameWithoutExtension,
                fontFamily = family,
                fontSize = 40.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            )
            OutlinedTextField(
                value = sample,
                onValueChange = { sample = it },
                label = { Text(stringResource(R.string.font_preview_text)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            listOf(16, 20, 28, 40, 56).forEach { sizeSp ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("$sizeSp", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(30.dp))
                    Text(sample.ifBlank { " " }, fontFamily = family, fontSize = sizeSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(Modifier.padding(16.dp))
            SectionLabel(stringResource(R.string.font_characters))
            listOf(
                "A B C D E F G H I J K L M N O P Q R S T U V W X Y Z",
                "a b c d e f g h i j k l m n o p q r s t u v w x y z",
                "0 1 2 3 4 5 6 7 8 9",
                "& @ # % ( ) { } [ ] / \\ ? ! . , : ; \" ' * + - = < >",
            ).forEach { line ->
                Text(line, fontFamily = family, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.font_preview_unavailable, state.info?.format ?: "?"), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenExternal) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.open_with), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(16.dp))
        SectionLabel(stringResource(R.string.font_details))
        val info = state.info
        SelectionContainer {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                info?.family?.let { Detail(stringResource(R.string.font_family), it) }
                info?.subfamily?.let { Detail(stringResource(R.string.font_style), it) }
                info?.fullName?.let { Detail(stringResource(R.string.font_full_name), it) }
                info?.version?.let { Detail(stringResource(R.string.font_version), it) }
                info?.postScriptName?.let { Detail(stringResource(R.string.font_postscript), it) }
                info?.glyphCount?.let { Detail(stringResource(R.string.font_glyphs), it.toString()) }
                Detail(stringResource(R.string.font_format), info?.format ?: "?")
                Detail(stringResource(R.string.font_size), state.sizeBytes.readableSize())
                info?.copyright?.let { Detail(stringResource(R.string.font_copyright), it) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
