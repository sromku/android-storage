package com.snatik.storage.app.feature.benchmark

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.BenchmarkEvent
import com.snatik.storage.core.apps.BenchmarkResult
import com.snatik.storage.core.apps.StorageBenchmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File

class BenchmarkViewModel(private val context: Context, private val bench: StorageBenchmark) : ViewModel() {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _phase = MutableStateFlow<Pair<String, Float>?>(null)
    val phase: StateFlow<Pair<String, Float>?> = _phase.asStateFlow()
    private val _result = MutableStateFlow<BenchmarkResult?>(null)
    val result: StateFlow<BenchmarkResult?> = _result.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun targets(): List<Pair<String, File>> = buildList {
        add("Internal" to context.filesDir)
        context.getExternalFilesDir(null)?.let { add("Shared" to it) }
    }

    fun run(dir: File) {
        _running.value = true; _result.value = null; _error.value = null
        viewModelScope.launch {
            bench.run(dir).collect { ev ->
                when (ev) {
                    is BenchmarkEvent.Progress -> _phase.value = ev.phase to ev.fraction
                    is BenchmarkEvent.Done -> { _result.value = ev.result; _running.value = false; _phase.value = null }
                    is BenchmarkEvent.Failed -> { _error.value = ev.reason; _running.value = false; _phase.value = null }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(onBack: () -> Unit, viewModel: BenchmarkViewModel = koinViewModel()) {
    val running by viewModel.running.collectAsStateWithLifecycle()
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val targets = remember { viewModel.targets() }
    var target by remember { mutableStateOf(targets.first()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bench_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.bench_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEach { t -> FilterChip(selected = target == t, onClick = { target = t }, label = { Text(t.first) }) }
            }
            Button(onClick = { viewModel.run(target.second) }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                Text(if (running) stringResource(R.string.bench_running) else stringResource(R.string.bench_run))
            }
            phase?.let { (name, frac) ->
                Column {
                    Text(name, style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            result?.let { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.bench_result, r.fileSizeMB), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Metric(stringResource(R.string.bench_seq_write), "${r.seqWriteMBps} MB/s")
                        Metric(stringResource(R.string.bench_seq_read), "${r.seqReadMBps} MB/s")
                        Metric(stringResource(R.string.bench_rand_read), "${r.randReadMBps} MB/s")
                        Metric(stringResource(R.string.bench_iops), "${r.randReadIops.toInt()}")
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoStyle.copy(fontWeight = FontWeight.SemiBold))
    }
}
