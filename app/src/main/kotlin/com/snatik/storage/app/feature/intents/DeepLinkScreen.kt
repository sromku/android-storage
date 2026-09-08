package com.snatik.storage.app.feature.intents

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.intents.IntentSender
import com.snatik.storage.core.intents.IntentSpec
import com.snatik.storage.core.intents.ResolvedTarget
import com.snatik.storage.core.intents.SendAs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.androidx.compose.koinViewModel

data class DeepLinkUiState(val url: String = "https://", val targets: List<ResolvedTarget>? = null)

class DeepLinkViewModel(private val sender: IntentSender) : ViewModel() {
    private val _state = MutableStateFlow(DeepLinkUiState())
    val state: StateFlow<DeepLinkUiState> = _state.asStateFlow()

    private fun spec() = IntentSpec(action = Intent.ACTION_VIEW, data = _state.value.url.trim(), categories = listOf(Intent.CATEGORY_BROWSABLE), sendAs = SendAs.ACTIVITY)

    fun setUrl(url: String) = _state.update { it.copy(url = url, targets = null) }
    fun resolve() = _state.update { it.copy(targets = runCatching { sender.resolve(spec()) }.getOrDefault(emptyList())) }
    fun launch(target: ResolvedTarget) = runCatching { sender.send(spec().copy(packageName = target.packageName, className = target.className)) }
    fun chooser(title: String): Intent = sender.chooser(spec(), title)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepLinkScreen(onBack: () -> Unit, viewModel: DeepLinkViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.deeplink_open_chooser)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deeplink_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(value = state.url, onValueChange = viewModel::setUrl, label = { Text(stringResource(R.string.deeplink_url)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::resolve) { Text(stringResource(R.string.deeplink_resolve)) }
                    OutlinedButton(onClick = { context.startActivity(viewModel.chooser(chooserTitle)) }) { Text(stringResource(R.string.deeplink_open_chooser)) }
                }
            }
            state.targets?.let { targets ->
                if (targets.isEmpty()) {
                    item { Text(stringResource(R.string.deeplink_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(targets, key = { it.packageName + "/" + it.className }) { target ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.launch(target) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(target.packageName, size = 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(target.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (target.isDefault) Tag(stringResource(R.string.builder_default), MaterialTheme.colorScheme.primary)
                            }
                            Text(target.className, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        }
                        TextButton(onClick = { viewModel.launch(target) }) { Text(stringResource(R.string.deeplink_launch)) }
                    }
                }
            }
        }
    }
}
