package com.snatik.storage.app.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.util.StorageAccess
import com.snatik.storage.core.fs.Volume
import com.snatik.storage.core.fs.VolumeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val volumes: List<Volume> = emptyList(),
    val appVolumes: List<Volume> = emptyList(),
    val hasFullAccess: Boolean = true,
    val loading: Boolean = true,
    /** True only while a pull-to-refresh started by the user is in flight. */
    val refreshing: Boolean = false,
)

class HomeViewModel(application: Application, private val repository: VolumeRepository) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh(userInitiated: Boolean = false) {
        _state.update { it.copy(loading = true, refreshing = userInitiated) }
        viewModelScope.launch {
            val volumes = repository.volumes()
            val appVolumes = repository.appVolumes()
            _state.update {
                it.copy(
                    volumes = volumes,
                    appVolumes = appVolumes,
                    hasFullAccess = StorageAccess.hasFullAccess(getApplication()),
                    loading = false,
                    refreshing = false,
                )
            }
        }
    }
}
