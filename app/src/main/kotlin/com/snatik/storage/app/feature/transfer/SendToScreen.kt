package com.snatik.storage.app.feature.transfer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import com.snatik.storage.core.net.Peer
import com.snatik.storage.core.net.SendProgress
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

data class SendUiState(
    val paths: List<String>,
    val totalBytes: Long,
    val manual: String = "",
    val target: Peer? = null,
    val code: String = "",
    val checking: Boolean = false,
    val progress: SendProgress? = null,
    val sending: Boolean = false,
)

class SendToViewModel(route: Route.SendTo, private val hub: TransferHub) : ViewModel() {
    val peers = hub.discovery.peers
    private val _state = MutableStateFlow(SendUiState(route.paths, route.paths.sumOf { File(it).length() }))
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun startDiscovery() = hub.discovery.startDiscovery()
    fun stopDiscovery() = hub.discovery.stopDiscovery()
    fun setManual(text: String) = _state.update { it.copy(manual = text) }
    fun pick(peer: Peer?) = _state.update { it.copy(target = peer, code = "") }
    fun setCode(code: String) = _state.update { it.copy(code = code.uppercase().filter { c -> c.isLetterOrDigit() }.take(6)) }

    fun pickManual() {
        val text = _state.value.manual.trim()
        val host = text.substringBefore(':')
        val port = text.substringAfter(':', "").toIntOrNull() ?: com.snatik.storage.core.net.DEFAULT_PORT
        if (host.isNotEmpty()) pick(Peer(text, host, port))
    }

    fun send() {
        val s = _state.value
        val peer = s.target ?: return
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            val ok = runCatching { hub.client.checkCode(peer.baseUrl, s.code) }.getOrDefault(false)
            if (!ok) {
                _state.update { it.copy(checking = false) }
                _messages.send("wrong-code")
                return@launch
            }
            _state.update { it.copy(checking = false, sending = true) }
            try {
                hub.client.progressSink = { sent -> _state.update { it.copy(progress = it.progress?.copy(sentBytes = sent)) } }
                hub.client.send(peer.baseUrl, s.code, s.paths).collect { p -> _state.update { it.copy(progress = p) } }
                _messages.send("done:${s.paths.size}")
            } catch (e: Exception) {
                _messages.send(e.message ?: e.toString())
            } finally {
                hub.client.progressSink = null
                _state.update { it.copy(sending = false, target = null) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendToScreen(route: Route.SendTo, onBack: () -> Unit, viewModel: SendToViewModel = koinViewModel(parameters = { parametersOf(route) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, resources) {
        viewModel.messages.collect { m ->
            snackbar.showSnackbar(
                when {
                    m == "wrong-code" -> resources.getString(R.string.send_wrong_code)
                    m.startsWith("done:") -> resources.getString(R.string.send_done, m.removePrefix("done:").toInt())
                    else -> resources.getString(R.string.send_failed) + ": " + m
                },
            )
        }
    }
    DisposableEffect(viewModel) {
        viewModel.startDiscovery()
        onDispose { viewModel.stopDiscovery() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.send_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.send_files, state.paths.size, state.totalBytes.readableSize()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.progress?.let { p ->
                item(key = "progress") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                        Text(if (p.done) stringResource(R.string.send_done, p.fileCount) else stringResource(R.string.send_progress, p.name, p.fileIndex + 1, p.fileCount) + "  ·  " + p.sentBytes.readableSize() + " / " + p.totalBytes.readableSize(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(key = "searching") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(if (peers.isEmpty()) stringResource(R.string.send_none) else stringResource(R.string.send_searching), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(peers, key = { it.name + it.host }) { peer ->
                Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.pick(peer) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(peer.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("${peer.host}:${peer.port}", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "manual") {
                Text(stringResource(R.string.send_manual), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = state.manual, onValueChange = viewModel::setManual, label = { Text(stringResource(R.string.send_address)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.weight(1f))
                    Button(onClick = viewModel::pickManual, enabled = state.manual.isNotBlank()) { Text(stringResource(R.string.send_send)) }
                }
            }
        }
    }

    state.target?.let { peer ->
        if (!state.sending) AlertDialog(
            onDismissRequest = { viewModel.pick(null) },
            title = { Text(peer.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.send_code_prompt, peer.name))
                    OutlinedTextField(value = state.code, onValueChange = viewModel::setCode, singleLine = true, textStyle = MonoStyle.copy(fontSize = 22.sp), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                if (state.checking) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else TextButton(onClick = viewModel::send, enabled = state.code.length == 6) { Text(stringResource(R.string.send_send)) }
            },
            dismissButton = { TextButton(onClick = { viewModel.pick(null) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
