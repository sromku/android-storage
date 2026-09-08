package com.snatik.storage.app.feature.viewer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.Tag
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.app.util.readableSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.security.MessageDigest

data class ApkDetails(
    val label: String, val packageName: String, val versionName: String, val versionCode: Long,
    val minSdk: Int, val targetSdk: Int, val size: Long, val permissions: List<String>,
    val activities: Int, val services: Int, val receivers: Int, val providers: Int, val signatures: List<String>,
)

class ApkViewModel(private val path: String, private val context: Context) : ViewModel() {
    val name = path.substringAfterLast('/')
    private val _state = MutableStateFlow<ApkDetails?>(null)
    val state: StateFlow<ApkDetails?> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _state.value = withContext(Dispatchers.IO) { parse() }
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun parse(): ApkDetails {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_SIGNING_CERTIFICATES
        val info: PackageInfo = pm.getPackageArchiveInfo(path, flags) ?: error("Not a valid APK")
        val app = info.applicationInfo!!.apply { sourceDir = path; publicSourceDir = path }
        val signatures = info.signingInfo?.let { s -> (if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory).orEmpty() }
            ?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString(":") { b -> "%02X".format(b) } } ?: emptyList()
        return ApkDetails(
            label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(info.packageName),
            packageName = info.packageName,
            versionName = info.versionName ?: "",
            versionCode = info.longVersionCode,
            minSdk = app.minSdkVersion,
            targetSdk = app.targetSdkVersion,
            size = File(path).length(),
            permissions = info.requestedPermissions?.toList().orEmpty(),
            activities = info.activities?.size ?: 0,
            services = info.services?.size ?: 0,
            receivers = info.receivers?.size ?: 0,
            providers = info.providers?.size ?: 0,
            signatures = signatures,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkViewerScreen(path: String, onBack: () -> Unit, viewModel: ApkViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val apk by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.apk_invalid), error)
                apk == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> {
                    val a = apk!!
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                        item {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(a.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                SelectionContainer { Text(a.packageName, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Text("${a.versionName} (${a.versionCode}) · ${a.size.readableSize()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                        }
                        item { InfoLine(stringResource(R.string.info_sdk), stringResource(R.string.info_sdk_value, a.targetSdk, a.minSdk)) }
                        item { InfoLine(stringResource(R.string.apk_components), "${a.activities} activities · ${a.services} services · ${a.receivers} receivers · ${a.providers} providers") }
                        items(a.signatures) { sig -> InfoLine(stringResource(R.string.info_signing), sig) }
                        item { Text(stringResource(R.string.tab_permissions) + " · " + a.permissions.size, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp)) }
                        items(a.permissions) { perm ->
                            Text(perm, style = MonoStyle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        SelectionContainer(modifier = Modifier.weight(1f)) { Text(value, style = MonoStyle) }
    }
}
