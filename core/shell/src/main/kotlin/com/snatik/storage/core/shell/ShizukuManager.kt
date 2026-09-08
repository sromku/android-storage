package com.snatik.storage.core.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

sealed interface ShizukuStatus {
    /** The Shizuku app is not on this device. */
    data object NotInstalled : ShizukuStatus

    /** Installed but its service is not running; the user starts it from the Shizuku app. */
    data object NotRunning : ShizukuStatus

    /** Running, but this app has not been allowed to use it yet. */
    data class PermissionRequired(val canAsk: Boolean) : ShizukuStatus

    data object Connecting : ShizukuStatus

    /** Our shell service is up and answering. */
    data class Connected(val uid: Int) : ShizukuStatus
}

/**
 * Tracks the Shizuku service, asks for permission, and keeps our [ShellUserService] bound so
 * commands can run as the shell user.
 */
class ShizukuManager(private val context: Context, private val debuggable: Boolean) {

    private val _status = MutableStateFlow<ShizukuStatus>(ShizukuStatus.NotInstalled)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val _executor = MutableStateFlow<RemoteShellExecutor?>(null)
    val executor: StateFlow<RemoteShellExecutor?> = _executor.asStateFlow()

    private val serviceArgs = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(debuggable)
        .version(SERVICE_VERSION)

    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) {
                _status.value = ShizukuStatus.NotRunning
                return
            }
            val executor = RemoteShellExecutor(binder)
            val uid = runCatching { executor.ping() }.getOrDefault(-1)
            _executor.value = executor
            _status.value = ShizukuStatus.Connected(uid)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _executor.value = null
            bound = false
            refresh()
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        _executor.value = null
        bound = false
        _status.value = ShizukuStatus.NotRunning
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == PERMISSION_REQUEST_CODE) {
            if (result == PackageManager.PERMISSION_GRANTED) connect() else refresh()
        }
    }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        disconnect()
    }

    val isInstalled: Boolean
        get() = runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess

    /** Re-evaluate installed, running and permission state, and connect when everything is in place. */
    fun refresh() {
        when {
            !isInstalled -> _status.value = ShizukuStatus.NotInstalled
            !Shizuku.pingBinder() -> _status.value = ShizukuStatus.NotRunning
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                _status.value = ShizukuStatus.PermissionRequired(canAsk = !Shizuku.shouldShowRequestPermissionRationale())
            _executor.value?.isAlive == true -> Unit
            else -> connect()
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun connect() {
        if (bound && _executor.value?.isAlive == true) return
        _status.value = ShizukuStatus.Connecting
        bound = true
        runCatching { Shizuku.bindUserService(serviceArgs, connection) }
            .onFailure {
                bound = false
                _status.value = ShizukuStatus.NotRunning
            }
    }

    fun disconnect() {
        if (!bound) return
        bound = false
        _executor.value = null
        runCatching { Shizuku.unbindUserService(serviceArgs, connection, true) }
        refresh()
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST_CODE = 0x5a1c
        private const val SERVICE_VERSION = 1
    }
}
