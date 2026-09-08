package com.snatik.storage.core.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream

/** Who the commands run as. Ordered from least to most capable. */
enum class PrivilegeTier { NONE, SHIZUKU, ROOT }

/** A running command with its three streams. */
interface ShellProcess {
    val stdin: OutputStream
    val stdout: InputStream
    val stderr: InputStream

    /** Block until the command finishes and return its exit code. */
    suspend fun waitFor(): Int
    fun kill()
}

data class ShellResult(val exitCode: Int, val stdout: ByteArray, val stderr: ByteArray) {
    val ok: Boolean get() = exitCode == 0
    val out: String get() = String(stdout, Charsets.UTF_8)
    val err: String get() = String(stderr, Charsets.UTF_8)
}

class ShellException(val command: String, val result: ShellResult) :
    RuntimeException("Command failed (${result.exitCode}): ${result.err.trim().ifEmpty { result.out.trim() }.take(200)}")

/**
 * Runs shell commands somewhere: this process, the Shizuku shell process, or a root shell.
 * Every command is a single `sh -c` string.
 */
interface ShellExecutor {
    val tier: PrivilegeTier

    /** Start a command and return its process. The caller owns the streams. */
    suspend fun start(command: String): ShellProcess
}

/** Run to completion, capturing both streams. Feeds [stdin] first when given. */
suspend fun ShellExecutor.run(command: String, stdin: ByteArray? = null, timeoutMs: Long = 60_000): ShellResult =
    withContext(Dispatchers.IO) {
        val process = start(command)
        try {
            val result = withTimeoutOrNull(timeoutMs) {
                coroutineScope {
                    if (stdin != null) {
                        launch { process.stdin.use { it.write(stdin) } }
                    } else {
                        process.stdin.close()
                    }
                    val out = async { process.stdout.use { it.readBytes() } }
                    val err = async { process.stderr.use { it.readBytes() } }
                    val code = process.waitFor()
                    ShellResult(code, out.await(), err.await())
                }
            }
            result ?: run {
                process.kill()
                ShellResult(-1, ByteArray(0), "Timed out after ${timeoutMs}ms".toByteArray())
            }
        } catch (e: Throwable) {
            process.kill()
            throw e
        }
    }

/** Run and throw [ShellException] on a non-zero exit. */
suspend fun ShellExecutor.runOrThrow(command: String, stdin: ByteArray? = null, timeoutMs: Long = 60_000): ShellResult {
    val result = run(command, stdin, timeoutMs)
    if (!result.ok) throw ShellException(command, result)
    return result
}

/** Stream stdout line by line while the command runs, for logcat and friends. */
fun ShellExecutor.lines(command: String): Flow<String> = flow {
    val process = start(command)
    try {
        process.stdin.close()
        process.stdout.bufferedReader().useLines { seq -> seq.forEach { emit(it) } }
        process.waitFor()
    } finally {
        process.kill()
    }
}.flowOn(Dispatchers.IO)
