package com.snatik.storage.core.apps

import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.ShellException
import com.snatik.storage.core.shell.run
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

    /**
     * Recompile an app's dex to a chosen ART compilation filter with `cmd package compile`.
     * "speed" fully AOT-compiles, "speed-profile" uses the collected profile, "verify" drops to
     * verify-only, and "-r bg-dexopt" mimics the background job. Returns the command output.
     */
    suspend fun compile(packageName: String, mode: CompileMode): String {
        val flag = when (mode) {
            CompileMode.SPEED -> "-m speed -f"
            CompileMode.SPEED_PROFILE -> "-m speed-profile -f"
            CompileMode.EVERYTHING -> "-m everything -f"
            CompileMode.VERIFY -> "-m verify -f"
            CompileMode.RESET -> "--reset"
        }
        return shell().runOrThrow("cmd package compile $flag ${packageName.shellQuote()}").out.trim()
    }

    /** Current ART compilation filter for a package, read from dumpsys. */
    suspend fun compilationFilter(packageName: String): String? {
        val out = runCatching { shell().run("dumpsys package dexopt ${packageName.shellQuote()} 2>/dev/null").out }.getOrNull()
            ?: runCatching { shell().run("dumpsys package ${packageName.shellQuote()} 2>/dev/null").out }.getOrNull()
            ?: return null
        // lines look like:  [status=speed] [reason=install] ... or  status: speed-profile
        val m = Regex("\\[status=([\\w-]+)").find(out) ?: Regex("status:?\\s*([\\w-]+)").find(out)
        return m?.groupValues?.get(1)
    }
}

enum class CompileMode { SPEED_PROFILE, SPEED, EVERYTHING, VERIFY, RESET }
