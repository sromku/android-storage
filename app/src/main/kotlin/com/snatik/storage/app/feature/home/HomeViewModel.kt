package com.snatik.storage.app.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.util.StorageAccess
import com.snatik.storage.core.fs.Volume
import com.snatik.storage.core.fs.VolumeRepository
import com.snatik.storage.core.shell.DebuggableApp
import com.snatik.storage.core.shell.DebuggablePackages
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.PrivilegeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val volumes: List<Volume> = emptyList(),
    val appVolumes: List<Volume> = emptyList(),
    val debuggableApps: List<DebuggableApp> = emptyList(),
    val hasFullAccess: Boolean = true,
    val loading: Boolean = true,
    /** True only while a pull-to-refresh started by the user is in flight. */
    val refreshing: Boolean = false,
    val privilege: PrivilegeState,
)

class HomeViewModel(
    application: Application,
    private val repository: VolumeRepository,
    private val privilege: PrivilegeManager,
    private val debuggable: DebuggablePackages,
) : AndroidViewModel(application) {

    private data class Local(
        val volumes: List<Volume> = emptyList(),
        val appVolumes: List<Volume> = emptyList(),
        val debuggableApps: List<DebuggableApp> = emptyList(),
        val hasFullAccess: Boolean = true,
        val loading: Boolean = true,
        val refreshing: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<HomeUiState> = combine(local, privilege.state) { l, p ->
        HomeUiState(l.volumes, l.appVolumes, l.debuggableApps, l.hasFullAccess, l.loading, l.refreshing, p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(privilege = privilege.state.value))

    init {
        refresh()
        // Volumes depend on the privilege tier: the system root appears once a shell is available.
        viewModelScope.launch {
            var lastTier = privilege.state.value.tier
            privilege.state.collect { p ->
                if (p.tier != lastTier) {
                    lastTier = p.tier
                    refresh()
                }
            }
        }
    }

    fun refresh(userInitiated: Boolean = false) {
        local.update { it.copy(loading = true, refreshing = userInitiated) }
        privilege.shizuku.refresh()
        viewModelScope.launch {
            val privileged = privilege.state.value.isPrivileged
            val volumes = repository.volumes(includeSystemRoot = privileged)
            val appVolumes = repository.appVolumes()
            val apps = if (privileged) debuggable.list() else emptyList()
            local.update {
                it.copy(
                    volumes = volumes,
                    appVolumes = appVolumes,
                    debuggableApps = apps,
                    hasFullAccess = StorageAccess.hasFullAccess(getApplication()),
                    loading = false,
                    refreshing = false,
                )
            }
        }
    }

    fun requestShizukuPermission() = privilege.shizuku.requestPermission()
    fun connectShizuku() = privilege.shizuku.connect()
    fun setPreferRoot(enabled: Boolean) = privilege.setPreferRoot(enabled)
}
