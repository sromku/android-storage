package com.snatik.storage.app.feature.network

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Network-tool preferences. [resolveHosts] gates reverse-DNS: when on, opening the Network screen
 * looks up host names for remote IPs, which makes the app itself emit DNS queries (self-inflicted
 * traffic on its own uid). Off by default — the offline owner labels don't need it.
 */
class NetworkPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("network", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(prefs.getBoolean(KEY_RESOLVE, false))
    val resolveHosts: StateFlow<Boolean> = _state.asStateFlow()

    fun setResolveHosts(enabled: Boolean) {
        _state.update { enabled }
        prefs.edit { putBoolean(KEY_RESOLVE, enabled) }
    }

    private companion object {
        const val KEY_RESOLVE = "resolve_hosts"
    }
}
