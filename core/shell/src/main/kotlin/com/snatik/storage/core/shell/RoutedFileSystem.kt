package com.snatik.storage.core.shell

import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.fs.FsEntry
import com.snatik.storage.core.fs.OperationProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Sends each call to the local file system when this app's uid can do the job, and to the
 * privileged shell when it cannot. Data of debuggable packages goes through `run-as` when the
 * shell is only Shizuku.
 */
class RoutedFileSystem(
    private val local: FileSystem,
    private val privilege: PrivilegeManager,
    private val debuggable: DebuggablePackages,
) : FileSystem {

    private var cachedExecutor: ShellExecutor? = null
    private var cachedShellFs: ShellFileSystem? = null
    private val runAsCache = HashMap<String, ShellFileSystem>()

    private fun shellFs(executor: ShellExecutor): ShellFileSystem {
        if (cachedExecutor !== executor) {
            cachedExecutor = executor
            cachedShellFs = ShellFileSystem(executor)
            runAsCache.clear()
        }
        return cachedShellFs!!
    }

    /** Which implementation should handle [path]. [write] asks for write access to its parent. */
    fun pick(path: String, write: Boolean = false): FileSystem {
        val executor = privilege.executor.value ?: return local
        val owner = debuggable.ownerOf(path)
        if (owner != null && executor.tier == PrivilegeTier.SHIZUKU) {
            return runAsCache.getOrPut(owner) { ShellFileSystem(RunAsShellExecutor(executor, owner)) }
        }
        if (isAppSandboxOfOthers(path)) return shellFs(executor)
        val file = File(path)
        val accessible = if (write) {
            (file.exists() && file.canWrite()) || (!file.exists() && file.parentFile?.canWrite() == true)
        } else {
            file.canRead()
        }
        return if (accessible) local else shellFs(executor)
    }

    private fun isAppSandboxOfOthers(path: String): Boolean {
        val match = ANDROID_SANDBOX.find(path) ?: return false
        return match.groupValues[1] != ownPackage
    }

    private val ownPackage: String get() = debuggable.ownPackageName

    override suspend fun stat(path: String): FsEntry? = pick(path).stat(path)
    override suspend fun list(path: String): List<FsEntry> = pick(path).list(path)
    override suspend fun readBytes(path: String, offset: Long, maxLength: Int): ByteArray = pick(path).readBytes(path, offset, maxLength)
    override suspend fun writeBytes(path: String, bytes: ByteArray) = pick(path, write = true).writeBytes(path, bytes)
    override suspend fun createDirectory(path: String): FsEntry = pick(path, write = true).createDirectory(path)
    override suspend fun createFile(path: String): FsEntry = pick(path, write = true).createFile(path)
    override suspend fun rename(path: String, newName: String): FsEntry = pick(path, write = true).rename(path, newName)
    override suspend fun directorySize(path: String): Long = pick(path).directorySize(path)
    override suspend fun sha256(path: String): String = pick(path).sha256(path)
    override fun walk(path: String): Flow<Pair<String, Long>> = pick(path).walk(path)

    override fun copy(sources: List<String>, destinationDir: String): Flow<OperationProgress> =
        routedOperation(sources, destinationDir) { fs, list -> fs.copy(list, destinationDir) }

    override fun move(sources: List<String>, destinationDir: String): Flow<OperationProgress> =
        routedOperation(sources, destinationDir) { fs, list -> fs.move(list, destinationDir) }

    override fun delete(paths: List<String>): Flow<OperationProgress> = flow {
        // Group by implementation so a mixed selection still works.
        val groups = paths.groupBy { pick(it, write = true) }
        var done = 0
        for ((fs, group) in groups) {
            fs.delete(group).collect { p -> emit(p.copy(totalItems = paths.size, doneItems = done + p.doneItems)) }
            done += group.size
        }
    }

    private fun routedOperation(
        sources: List<String>,
        destinationDir: String,
        op: (FileSystem, List<String>) -> Flow<OperationProgress>,
    ): Flow<OperationProgress> {
        // A privileged destination or source forces the whole batch through the shell.
        val destination = pick(destinationDir, write = true)
        val fs = if (destination !== local) destination else sources.map { pick(it) }.firstOrNull { it !== local } ?: local
        return op(fs, sources)
    }

    private companion object {
        val ANDROID_SANDBOX = Regex("^/storage/[^/]+/(?:\\d+/)?Android/(?:data|obb)/([^/]+)")
    }
}
