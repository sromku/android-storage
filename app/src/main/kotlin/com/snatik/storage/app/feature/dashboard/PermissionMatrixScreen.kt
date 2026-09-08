package com.snatik.storage.app.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.AppIcon
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.apps.PermissionMatrix
import com.snatik.storage.core.apps.PermissionMatrixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class PermissionMatrixViewModel(private val repo: PermissionMatrixRepository) : ViewModel() {
    private val _state = MutableStateFlow<PermissionMatrix?>(null)
    val state: StateFlow<PermissionMatrix?> = _state.asStateFlow()
    init { viewModelScope.launch { _state.update { repo.matrix(includeSystem = false) } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionMatrixScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit, viewModel: PermissionMatrixViewModel = koinViewModel()) {
    val matrix by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.matrix_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val m = matrix
            if (m == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val horizontal = rememberScrollState()
                val cell = 34.dp
                val nameW = 150.dp
                val granted = MaterialTheme.colorScheme.primary
                val requestedOnly = MaterialTheme.colorScheme.surfaceVariant
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Box(modifier = Modifier.size(14.dp).padding(1.dp).background(granted, MaterialTheme.shapes.extraSmall))
                        Text(stringResource(R.string.matrix_granted), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, end = 12.dp))
                        Box(modifier = Modifier.size(14.dp).padding(1.dp).background(requestedOnly, MaterialTheme.shapes.extraSmall))
                        Text(stringResource(R.string.matrix_requested), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                    }
                    // header row: short aligned codes
                    Row(modifier = Modifier.horizontalScroll(horizontal).padding(start = nameW)) {
                        m.permissions.forEach { perm ->
                            Text(
                                abbrev(perm),
                                style = MonoStyle.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.width(cell).padding(vertical = 6.dp),
                            )
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(m.apps, key = { it.packageName }) { app ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenApp(app.packageName) }) {
                                Row(modifier = Modifier.width(nameW).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(app.packageName, size = 22.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(app.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Row(modifier = Modifier.horizontalScroll(horizontal)) {
                                    m.permissions.forEach { perm ->
                                        val color = when { perm in app.granted -> granted; perm in app.requested -> requestedOnly; else -> Color.Transparent }
                                        Box(modifier = Modifier.width(cell).height(28.dp).padding(1.dp).background(color, MaterialTheme.shapes.extraSmall))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ABBREV = mapOf(
    "ACCESS_FINE_LOCATION" to "LOC", "ACCESS_BACKGROUND_LOCATION" to "BGLOC", "CAMERA" to "CAM", "RECORD_AUDIO" to "MIC",
    "READ_CONTACTS" to "CONT", "READ_SMS" to "SMS", "READ_CALL_LOG" to "CALL", "READ_CALENDAR" to "CAL",
    "READ_PHONE_STATE" to "PHON", "BLUETOOTH_SCAN" to "BTSC", "BLUETOOTH_CONNECT" to "BTCN", "NEARBY_WIFI_DEVICES" to "WIFI",
    "POST_NOTIFICATIONS" to "NOTIF", "BODY_SENSORS" to "SENS", "ACTIVITY_RECOGNITION" to "ACT",
    "READ_MEDIA_IMAGES" to "IMG", "READ_MEDIA_VIDEO" to "VID", "READ_EXTERNAL_STORAGE" to "EXT",
    "MANAGE_EXTERNAL_STORAGE" to "ALLFL", "SYSTEM_ALERT_WINDOW" to "OVER", "REQUEST_INSTALL_PACKAGES" to "INST",
    "QUERY_ALL_PACKAGES" to "QALL", "GET_ACCOUNTS" to "ACCT", "INTERNET" to "NET",
)
private fun abbrev(perm: String): String = ABBREV[perm] ?: perm.take(4)
