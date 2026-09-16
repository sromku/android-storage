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

    // `pm clear` can hang indefinitely on some builds (e.g. Android 17); cap it so callers fail fast.
    suspend fun clearCache(packageName: String) {
        shell().runOrThrow("pm clear --cache-only ${packageName.shellQuote()}", timeoutMs = 15_000)
    }

    suspend fun clearData(packageName: String) {
        shell().runOrThrow("pm clear ${packageName.shellQuote()}", timeoutMs = 15_000)
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
            CompileMode.SPEED -> "-m speed -f -v"
            CompileMode.SPEED_PROFILE -> "-m speed-profile -f -v"
            CompileMode.EVERYTHING -> "-m everything -f -v"
            CompileMode.VERIFY -> "-m verify -f -v"
            CompileMode.RESET -> "--reset"
        }
        return shell().runOrThrow("cmd package compile $flag ${packageName.shellQuote()}", timeoutMs = 180_000).out.trim()
    }

    /**
     * Current ART compilation filter for one package. `pm art dump <pkg>` is scoped to the package;
     * `dumpsys package dexopt` dumps every package, so its output is narrowed to the package's block
     * before reading the status (the old code read the first status in the whole dump).
     */
    suspend fun compilationFilter(packageName: String): String? {
        val art = runCatching { shell().run("pm art dump ${packageName.shellQuote()} 2>/dev/null").out }.getOrNull()
        statusIn(art)?.let { return it }
        val dump = runCatching { shell().run("dumpsys package dexopt 2>/dev/null").out }.getOrNull() ?: return null
        return statusIn(blockFor(dump, packageName))
    }

    private fun statusIn(text: String?): String? =
        text?.let { Regex("\\[status=([\\w-]+)").find(it)?.groupValues?.get(1) }

    /** The lines of a `dumpsys package dexopt` dump that belong to one package's `[pkg]` block. */
    private fun blockFor(dump: String, pkg: String): String? {
        val lines = dump.lines()
        val start = lines.indexOfFirst { it.trim() == "[$pkg]" }
        if (start < 0) return null
        val rest = lines.drop(start + 1)
        val end = rest.indexOfFirst { val t = it.trim(); t.startsWith("[") && t.endsWith("]") }
        return (if (end < 0) rest else rest.take(end)).joinToString("\n")
    }
}

enum class CompileMode { SPEED_PROFILE, SPEED, EVERYTHING, VERIFY, RESET }

/** One dex container's result from a verbose `cmd package compile -v` run. */
data class DexoptFileResult(
    val file: String,
    val abi: String,
    val filter: String,
    val status: String,
    val sizeBytes: Long,
    val sizeBeforeBytes: Long,
    val wallMs: Long,
    val cpuMs: Long,
    val flags: String,
) {
    val simpleFile: String get() = file.substringAfterLast('/')
}

/** Parse the `DexContainerFileDexoptResult{...}` lines a verbose compile prints. */
fun parseDexoptResults(raw: String): List<DexoptFileResult> =
    Regex("DexContainerFileDexoptResult\\{([^}]*)\\}").findAll(raw).map { m ->
        val body = m.groupValues[1]
        fun str(k: String) = Regex("(?:^|[ ,])$k=([^,}]+)").find(body)?.groupValues?.get(1)?.trim()
        fun num(k: String) = str(k)?.toLongOrNull() ?: 0L
        DexoptFileResult(
            file = str("dexContainerFile").orEmpty(),
            abi = str("abi").orEmpty(),
            filter = str("actualCompilerFilter").orEmpty(),
            status = str("status").orEmpty(),
            sizeBytes = num("sizeBytes"),
            sizeBeforeBytes = num("sizeBeforeBytes"),
            wallMs = num("dex2oatWallTimeMillis"),
            cpuMs = num("dex2oatCpuTimeMillis"),
            flags = Regex("extendedStatusFlags=(\\[[^\\]]*\\])").find(body)?.groupValues?.get(1) ?: "[]",
        )
    }.toList()

/** The overall "Final Status: X" line a verbose compile prints. */
fun dexoptFinalStatus(raw: String): String? = Regex("Final Status:\\s*(\\w+)").find(raw)?.groupValues?.get(1)
