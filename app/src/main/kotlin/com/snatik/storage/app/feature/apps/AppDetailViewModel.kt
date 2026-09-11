package com.snatik.storage.app.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.apps.AppActions
import com.snatik.storage.core.apps.AppDetails
import com.snatik.storage.core.apps.AppRepository
import com.snatik.storage.core.apps.ManifestDecoder
import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.fs.OperationRunner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class DetailTab { OVERVIEW, BEHAVIOR, MANIFEST, COMPONENTS, PERMISSIONS }

sealed interface DetailDialog {
    data object ConfirmClearData : DetailDialog
    data object ConfirmUninstall : DetailDialog
}

data class AppDetailUiState(
    val details: AppDetails? = null,
    val loading: Boolean = true,
    val missing: Boolean = false,
    val tab: DetailTab = DetailTab.OVERVIEW,
    val manifest: String? = null,
    val manifestError: String? = null,
    val manifestQuery: String = "",
    val shellAvailable: Boolean = false,
    val busy: Boolean = false,
    val dialog: DetailDialog? = null,
    val watch: com.snatik.storage.core.apps.AppWatch? = null,
    val watchLoading: Boolean = false,
)

class AppDetailViewModel(
    val packageName: String,
    private val repository: AppRepository,
    private val decoder: ManifestDecoder,
    private val actions: AppActions,
    private val fs: FileSystem,
    private val runner: OperationRunner,
    private val watchRepo: com.snatik.storage.core.apps.AppWatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppDetailUiState(shellAvailable = actions.available))
    val state: StateFlow<AppDetailUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val details = repository.details(packageName)
            _state.update { it.copy(details = details, loading = false, missing = details == null, shellAvailable = actions.available) }
        }
    }

    fun selectTab(tab: DetailTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == DetailTab.MANIFEST && _state.value.manifest == null && _state.value.manifestError == null) loadManifest()
        if (tab == DetailTab.BEHAVIOR && _state.value.watch == null) loadWatch()
    }

    fun loadWatch() {
        _state.update { it.copy(watchLoading = true) }
        viewModelScope.launch {
            val watch = watchRepo.watch(packageName)
            _state.update { it.copy(watch = watch, watchLoading = false) }
        }
    }

    private fun loadManifest() {
        val apk = _state.value.details?.summary?.apkPath ?: return
        viewModelScope.launch {
            try {
                val xml = decoder.decode(packageName, apk)
                _state.update { it.copy(manifest = xml) }
            } catch (e: Exception) {
                _state.update { it.copy(manifestError = e.message ?: e.toString()) }
            }
        }
    }

    fun setManifestQuery(query: String) = _state.update { it.copy(manifestQuery = query) }

    fun launchIntent() = repository.launchIntent(packageName)
    fun settingsIntent() = repository.appSettingsIntent(packageName)

    fun exportApk() {
        val summary = _state.value.details?.summary ?: return
        viewModelScope.launch {
            val downloads = File("/storage/emulated/0/Download")
            val target = File(downloads, "${summary.packageName}-${summary.versionName ?: summary.versionCode}.apk")
            try {
                fs.copy(listOf(summary.apkPath), downloads.absolutePath).collect { }
                // copy() keeps the source name (base.apk); rename it to something recognisable.
                val copied = File(downloads, File(summary.apkPath).name)
                val finalFile = if (copied.exists() && !target.exists()) fs.rename(copied.absolutePath, target.name).path else copied.absolutePath
                _messages.send("exported:$finalFile")
            } catch (e: Exception) {
                _messages.send(e.message ?: e.toString())
            }
        }
    }

    fun requestClearData() = _state.update { it.copy(dialog = DetailDialog.ConfirmClearData) }
    fun requestUninstall() = _state.update { it.copy(dialog = DetailDialog.ConfirmUninstall) }
    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    fun forceStop() = shellAction { actions.forceStop(packageName) }
    fun clearCache() = shellAction { actions.clearCache(packageName) }
    fun clearData() { dismissDialog(); shellAction { actions.clearData(packageName) } }
    fun uninstall() {
        dismissDialog()
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                actions.uninstall(packageName)
                _messages.send("uninstalled") // the screen pops back; don't reload a package that's gone
            } catch (e: Exception) {
                _messages.send(e.message ?: e.toString())
                _state.update { it.copy(busy = false) }
                load()
            }
        }
    }
    fun setPermission(permission: String, grant: Boolean) = shellAction { actions.grantPermission(packageName, permission, grant) }

    fun compile(mode: com.snatik.storage.core.apps.CompileMode) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                actions.compile(packageName, mode)
                val filter = actions.compilationFilter(packageName)
                _messages.send(if (filter != null) "compile:$filter" else "done")
            } catch (e: Exception) {
                _messages.send(e.message ?: e.toString())
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun shellAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
                _messages.send("done")
            } catch (e: Exception) {
                _messages.send(e.message ?: e.toString())
            } finally {
                _state.update { it.copy(busy = false) }
                load()
            }
        }
    }
}
