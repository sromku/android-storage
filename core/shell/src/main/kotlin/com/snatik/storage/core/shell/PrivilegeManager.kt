package com.snatik.storage.core.shell

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RootStatus { UNKNOWN, UNAVAILABLE, AVAILABLE }

data class PrivilegeState(
    val tier: PrivilegeTier,
    val shizuku: ShizukuStatus,
    val root: RootStatus,
    val preferRoot: Boolean,
) {
    val isPrivileged: Boolean get() = tier != PrivilegeTier.NONE
}

/**
 * Picks the most capable [ShellExecutor] available: root when the user enabled it and the
 * device grants it, otherwise Shizuku, otherwise none.
 */
class PrivilegeManager(context: Context, val shizuku: ShizukuManager, private val scope: CoroutineScope) {

    private val prefs = context.getSharedPreferences("privilege", Context.MODE_PRIVATE)

    private val rootStatus = MutableStateFlow(RootStatus.UNKNOWN)
    private val preferRoot = MutableStateFlow(prefs.getBoolean("prefer_root", false))
    private val rootExecutor = LocalShellExecutor.root()

    val executor: StateFlow<ShellExecutor?> = combine(shizuku.executor, rootStatus, preferRoot) { remote, root, prefer ->
        when {
            prefer && root == RootStatus.AVAILABLE -> rootExecutor
            remote != null -> remote
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val state: StateFlow<PrivilegeState> = combine(executor, shizuku.status, rootStatus, preferRoot) { exec, shizukuStatus, root, prefer ->
        PrivilegeState(tier = exec?.tier ?: PrivilegeTier.NONE, shizuku = shizukuStatus, root = root, preferRoot = prefer)
    }.stateIn(scope, SharingStarted.Eagerly, PrivilegeState(PrivilegeTier.NONE, ShizukuStatus.NotInstalled, RootStatus.UNKNOWN, preferRoot.value))

    fun start() {
        shizuku.start()
        if (preferRoot.value) probeRoot()
    }

    /** Turn root use on or off. Turning it on runs `su` once, which may prompt the user. */
    fun setPreferRoot(enabled: Boolean) {
        preferRoot.value = enabled
        prefs.edit().putBoolean("prefer_root", enabled).apply()
        if (enabled) probeRoot()
    }

    fun probeRoot() {
        scope.launch {
            val result = runCatching { rootExecutor.run("id", timeoutMs = 15_000) }.getOrNull()
            rootStatus.value = if (result != null && result.ok && result.out.contains("uid=0")) RootStatus.AVAILABLE else RootStatus.UNAVAILABLE
        }
    }
}
