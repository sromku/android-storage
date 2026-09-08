package com.snatik.storage.core.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Commands run in this process through `sh -c`, or through `su -c` when the device is rooted.
 * Also the building block the Shizuku service uses inside the shell-uid process.
 */
class LocalShellExecutor private constructor(
    private val launcher: List<String>,
    override val tier: PrivilegeTier,
) : ShellExecutor {

    override suspend fun start(command: String): ShellProcess = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(launcher + command).start()
        JavaShellProcess(process)
    }

    companion object {
        /** The app's own uid. */
        fun plain(): LocalShellExecutor = LocalShellExecutor(listOf("sh", "-c"), PrivilegeTier.NONE)

        /** Through the `su` binary of a rooted device. */
        fun root(): LocalShellExecutor = LocalShellExecutor(listOf("su", "-c"), PrivilegeTier.ROOT)
    }
}

class JavaShellProcess(private val process: Process) : ShellProcess {
    override val stdin: OutputStream get() = process.outputStream
    override val stdout: InputStream get() = process.inputStream
    override val stderr: InputStream get() = process.errorStream

    override suspend fun waitFor(): Int = runInterruptible(Dispatchers.IO) { process.waitFor() }

    override fun kill() {
        process.destroyForcibly()
    }
}
