package com.snatik.storage.core.shell

import com.snatik.storage.StorageException
import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.fs.FsEntry
import com.snatik.storage.core.fs.OperationProgress
import com.snatik.storage.core.fs.WalkEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * A [FileSystem] that runs toybox commands through a [ShellExecutor], reaching whatever that
 * executor's uid may. Listings come from one `stat` call per directory.
 */
class ShellFileSystem(private val shell: ShellExecutor) : FileSystem {

    override suspend fun stat(path: String): FsEntry? {
        val result = shell.run("stat -c $STAT_FORMAT -- ${path.shellQuote()}")
        if (!result.ok) {
            if (result.err.contains("No such file")) return null
            throw failure(path, result)
        }
        val line = result.out.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val entry = parseStat(line, parentPath = parentOf(path)) ?: return null
        return if (entry.isSymlink) resolveLinks(listOf(entry)).single() else entry
    }

    override suspend fun list(path: String): List<FsEntry> {
        val q = path.shellQuote()
        val script = "[ -e $q ] || exit 3; [ -d $q ] || exit 4; [ -r $q ] || exit 6; cd $q || exit 5; stat -c $STAT_FORMAT -- * .[!.]* ..?* 2>/dev/null; exit 0"
        val result = shell.run(script)
        when (result.exitCode) {
            0 -> Unit
            3 -> throw StorageException.NotFound(path)
            4 -> throw StorageException.NotADirectory(path)
            6 -> throw StorageException.Io(path, IOException("Permission denied for uid of the ${shell.tier.name.lowercase()} shell"))
            else -> throw failure(path, result)
        }
        val entries = result.out.lineSequence().filter { it.isNotBlank() }.mapNotNull { parseStat(it, path) }.toList()
        return resolveLinks(entries)
    }

    override suspend fun readBytes(path: String, offset: Long, maxLength: Int): ByteArray {
        val entry = stat(path) ?: throw StorageException.NotFound(path)
        if (entry.isDirectory) throw StorageException.NotAFile(path)
        val q = path.shellQuote()
        val command = if (maxLength == Int.MAX_VALUE) {
            "tail -c +${offset + 1} -- $q"
        } else {
            "tail -c +${offset + 1} -- $q | head -c $maxLength"
        }
        val result = shell.run(command, timeoutMs = 5 * 60_000)
        if (!result.ok && result.stdout.isEmpty()) throw failure(path, result)
        return result.stdout
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray) {
        val result = shell.run("cat > ${path.shellQuote()}", stdin = bytes, timeoutMs = 10 * 60_000)
        if (!result.ok) throw failure(path, result)
    }

    override suspend fun createDirectory(path: String): FsEntry {
        val q = path.shellQuote()
        val result = shell.run("[ -e $q ] && exit 3; mkdir -p -- $q")
        if (result.exitCode == 3) throw StorageException.AlreadyExists(path)
        if (!result.ok) throw failure(path, result)
        return stat(path) ?: throw StorageException.Io(path, IOException("Created but cannot stat"))
    }

    override suspend fun createFile(path: String): FsEntry {
        val q = path.shellQuote()
        val result = shell.run("[ -e $q ] && exit 3; : > $q")
        if (result.exitCode == 3) throw StorageException.AlreadyExists(path)
        if (!result.ok) throw failure(path, result)
        return stat(path) ?: throw StorageException.Io(path, IOException("Created but cannot stat"))
    }

    override suspend fun rename(path: String, newName: String): FsEntry {
        require(newName.isNotBlank() && !newName.contains('/')) { "Invalid name" }
        val target = parentOf(path).trimEnd('/') + "/" + newName
        val result = shell.run("[ -e ${target.shellQuote()} ] && exit 3; mv -- ${path.shellQuote()} ${target.shellQuote()}")
        if (result.exitCode == 3) throw StorageException.AlreadyExists(target)
        if (!result.ok) throw failure(path, result)
        return stat(target) ?: throw StorageException.Io(target, IOException("Renamed but cannot stat"))
    }

    override suspend fun directorySize(path: String): Long {
        val result = shell.run("find ${path.shellQuote()} -type f -exec stat -c %s -- {} +", timeoutMs = 10 * 60_000)
        if (!result.ok && result.stdout.isEmpty()) throw failure(path, result)
        return result.out.lineSequence().mapNotNull { it.trim().toLongOrNull() }.sum()
    }

    override suspend fun sha256(path: String): String {
        val result = shell.run("sha256sum -- ${path.shellQuote()}", timeoutMs = 10 * 60_000)
        if (!result.ok) throw failure(path, result)
        return result.out.trim().substringBefore(' ')
    }

    override fun walk(path: String): Flow<WalkEntry> =
        shell.lines("find ${path.shellQuote()} -type f -exec stat -c '%s %Y %n' -- {} + 2>/dev/null")
            .map { line ->
                val first = line.indexOf(' ')
                val second = if (first > 0) line.indexOf(' ', first + 1) else -1
                if (first <= 0 || second <= 0) return@map null
                val size = line.substring(0, first).toLongOrNull() ?: return@map null
                val mtime = line.substring(first + 1, second).toLongOrNull() ?: return@map null
                WalkEntry(line.substring(second + 1), size, mtime * 1000)
            }
            .filterNotNull()

    override fun copy(sources: List<String>, destinationDir: String): Flow<OperationProgress> = flow {
        emit(OperationProgress(sources.size, 0, 0, 0, ""))
        sources.forEachIndexed { index, source ->
            val target = uniqueTarget(destinationDir, nameOf(source))
            val result = shell.run("cp -a -- ${source.shellQuote()} ${target.shellQuote()}", timeoutMs = 60 * 60_000)
            if (!result.ok) throw failure(source, result)
            emit(OperationProgress(sources.size, index + 1, 0, 0, nameOf(source)))
        }
    }.flowOn(Dispatchers.IO)

    override fun move(sources: List<String>, destinationDir: String): Flow<OperationProgress> = flow {
        emit(OperationProgress(sources.size, 0, 0, 0, ""))
        sources.forEachIndexed { index, source ->
            val target = uniqueTarget(destinationDir, nameOf(source))
            val result = shell.run("mv -- ${source.shellQuote()} ${target.shellQuote()}", timeoutMs = 60 * 60_000)
            if (!result.ok) throw failure(source, result)
            emit(OperationProgress(sources.size, index + 1, 0, 0, nameOf(source)))
        }
    }.flowOn(Dispatchers.IO)

    override fun delete(paths: List<String>): Flow<OperationProgress> = flow {
        emit(OperationProgress(paths.size, 0, 0, 0, ""))
        paths.forEachIndexed { index, path ->
            val q = path.shellQuote()
            val result = shell.run("[ -e $q ] || [ -L $q ] || exit 3; rm -rf -- $q", timeoutMs = 60 * 60_000)
            if (result.exitCode == 3) throw StorageException.NotFound(path)
            if (!result.ok) throw failure(path, result)
            emit(OperationProgress(paths.size, index + 1, 0, 0, nameOf(path)))
        }
    }.flowOn(Dispatchers.IO)

    // Parsing

    private suspend fun uniqueTarget(dir: String, name: String): String {
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.') && !name.startsWith('.')) "." + name.substringAfterLast('.') else ""
        var candidate = dir.trimEnd('/') + "/" + name
        var i = 1
        while (stat(candidate) != null) {
            candidate = dir.trimEnd('/') + "/" + "$base ($i)$ext"
            i++
        }
        return candidate
    }

    /** Replace each symlink entry with one that knows whether its target is a directory. */
    private suspend fun resolveLinks(entries: List<FsEntry>): List<FsEntry> {
        val links = entries.filter { it.isSymlink }
        if (links.isEmpty()) return entries
        val script = links.joinToString("; ") { "stat -L -c %F -- ${it.path.shellQuote()} 2>/dev/null || echo broken" }
        val result = shell.run(script)
        val types = result.out.lines()
        var i = 0
        return entries.map { entry ->
            if (!entry.isSymlink) return@map entry
            val type = types.getOrNull(i++) ?: "broken"
            entry.copy(isDirectory = type == "directory")
        }
    }

    private fun failure(path: String, result: ShellResult): StorageException {
        val err = result.err
        return when {
            err.contains("No such file") -> StorageException.NotFound(path)
            err.contains("Not a directory") -> StorageException.NotADirectory(path)
            err.contains("Is a directory") -> StorageException.NotAFile(path)
            else -> StorageException.Io(path, IOException(err.trim().ifEmpty { "exit ${result.exitCode}" }))
        }
    }

    companion object {
        /** type|size|mtime|mode|name. `%n` comes last because names may contain the separator. */
        internal const val STAT_FORMAT = "'%F|%s|%Y|%a|%n'"

        internal fun parentOf(path: String): String = path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }
        internal fun nameOf(path: String): String = path.trimEnd('/').substringAfterLast('/')

        internal fun parseStat(line: String, parentPath: String): FsEntry? {
            val parts = line.split('|', limit = 5)
            if (parts.size < 5) return null
            val type = parts[0]
            val size = parts[1].toLongOrNull() ?: 0
            val mtime = (parts[2].toLongOrNull() ?: 0) * 1000
            val mode = parts[3].toIntOrNull(8) ?: 0
            val name = parts[4].substringAfterLast('/')
            if (name.isEmpty() || name == "." || name == "..") return null
            val isLink = type == "symbolic link"
            return FsEntry(
                path = parentPath.trimEnd('/') + "/" + name,
                name = name,
                isDirectory = type == "directory",
                size = if (type == "directory") 0 else size,
                lastModified = mtime,
                isHidden = name.startsWith("."),
                isSymlink = isLink,
                canRead = true,
                canWrite = mode and 0b010_010_010 != 0,
                childCount = null,
            )
        }
    }
}
