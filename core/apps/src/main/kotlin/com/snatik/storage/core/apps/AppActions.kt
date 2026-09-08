package com.snatik.storage.core.apps

import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.ShellException
import com.snatik.storage.core.shell.runOrThrow
import com.snatik.storage.core.shell.shellQuote

/**
 * Things a shell can do to other packages. Each call throws [ShellException] when the command fails
 * and [IllegalStateException] when no shell is connected.
 */
class AppActions(private val privilege: PrivilegeManager) {

    val available: Boolean get() = privilege.executor.value != null

    private fun shell() = privilege.executor.value ?: throw IllegalStateException("No shell access")

    suspend fun forceStop(packageName: String) {
        shell().runOrThrow("am force-stop ${packageName.shellQuote()}")
    }

    suspend fun clearCache(packageName: String) {
        shell().runOrThrow("pm clear --cache-only ${packageName.shellQuote()}")
    }

    suspend fun clearData(packageName: String) {
        shell().runOrThrow("pm clear ${packageName.shellQuote()}")
    }

    suspend fun uninstall(packageName: String) {
        shell().runOrThrow("pm uninstall ${packageName.shellQuote()}")
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean) {
        shell().runOrThrow("pm ${if (enabled) "enable" else "disable-user --user 0"} ${packageName.shellQuote()}")
    }

    suspend fun grantPermission(packageName: String, permission: String, grant: Boolean) {
        shell().runOrThrow("pm ${if (grant) "grant" else "revoke"} ${packageName.shellQuote()} ${permission.shellQuote()}")
    }
}
