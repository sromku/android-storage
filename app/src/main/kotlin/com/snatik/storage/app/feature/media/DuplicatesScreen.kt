package com.snatik.storage.app.feature.media

import android.app.Activity
import android.app.Application
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.snatik.storage.app.R
import com.snatik.storage.app.util.readableSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.platform.LocalContext

data class DuplicatesState(
    val loading: Boolean = true,
    val groups: List<List<MediaItem>> = emptyList(),
    val reclaimable: Long = 0,
)

class DuplicatesViewModel(
    application: Application,
    private val repo: MediaRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DuplicatesState())
    val state: StateFlow<DuplicatesState> = _state.asStateFlow()

    init { scan() }

    fun scan() {
        _state.value = DuplicatesState(loading = true)
        viewModelScope.launch {
            val items = repo.cached.ifEmpty { repo.all() }
            val groups = DuplicateFinder.findGroups(getApplication(), items)
            _state.value = DuplicatesState(false, groups, DuplicateFinder.reclaimable(groups))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(onBack: () -> Unit, viewModel: DuplicatesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> if (result.resultCode == Activity.RESULT_OK) viewModel.scan() }

    fun requestDelete(victims: List<MediaItem>) {
        val uris = victims.map { it.uri }
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            runCatching { uris.forEach { context.contentResolver.delete(it, null, null) } }
            viewModel.scan()
        }
    }

    if (pending.isNotEmpty()) {
        val victims = pending
        AlertDialog(
            onDismissRequest = { pending = emptyList() },
            title = { Text(stringResource(R.string.dupes_delete_title)) },
            text = { Text(pluralStringResource(R.plurals.dupes_delete_body, victims.size, victims.size)) },
            confirmButton = {
                TextButton(onClick = { pending = emptyList(); requestDelete(victims) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pending = emptyList() }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dupes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.dupes_scanning), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                }
            }
            state.groups.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.dupes_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item {
                    Text(
                        stringResource(R.string.dupes_summary, state.groups.size, state.reclaimable.readableSize()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(state.groups.size) { idx ->
                    DuplicateGroupCard(state.groups[idx]) { victims -> pending = victims }
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(group: List<MediaItem>, onDelete: (List<MediaItem>) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.dupes_group_count, group.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(group.size) { i ->
                    val item = group[i]
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        AsyncImage(
                            model = mediaModel(item),
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        if (i == 0) {
                            Row(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Text(stringResource(R.string.dupes_keep), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 3.dp))
                            }
                        }
                        Text(
                            item.size.readableSize(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .background(Color(0x99000000))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            TextButton(
                onClick = { onDelete(group.drop(1)) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    pluralStringResource(R.plurals.dupes_delete_action, group.size - 1, group.size - 1),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
