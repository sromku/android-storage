package com.snatik.storage.core.fs

import com.snatik.storage.Storage
import com.snatik.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest

/** [FileSystem] over `java.io.File`, reaching whatever the app's own uid may read. */
class LocalFileSystem(private val storage: Storage) : FileSystem {

    override suspend fun stat(path: String): FsEntry? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) entry(file) else null
    }

    override suspend fun list(path: String): List<FsEntry> = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists()) throw StorageException.NotFound(path)
        if (!dir.isDirectory) throw StorageException.NotADirectory(path)
        val children = dir.listFiles() ?: throw StorageException.Io(path, IOException("Permission denied or unreadable directory"))
        children.map(::entry)
    }

    override suspend fun readBytes(path: String, offset: Long, maxLength: Int): ByteArray = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) throw StorageException.NotFound(path)
        if (!file.isFile) throw StorageException.NotAFile(path)
        try {
            RandomAccessFile(file, "r").use { raf ->
                val available = (raf.length() - offset).coerceAtLeast(0)
                val length = minOf(available, maxLength.toLong()).toInt()
                val out = ByteArray(length)
                raf.seek(offset)
                var read = 0
                while (read < length) {
                    val n = raf.read(out, read, length - read)
                    if (n < 0) break
                    read += n
                }
                if (read == length) out else out.copyOf(read)
            }
        } catch (e: IOException) {
            throw StorageException.Io(path, e)
        }
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        storage.createFile(path, bytes).getOrThrow()
        Unit
    }

    override suspend fun createDirectory(path: String): FsEntry = withContext(Dispatchers.IO) {
        entry(storage.createDirectory(path).getOrThrow())
    }

    override suspend fun createFile(path: String): FsEntry = withContext(Dispatchers.IO) {
        if (File(path).exists()) throw StorageException.AlreadyExists(path)
        entry(storage.createFile(path, ByteArray(0)).getOrThrow())
    }

    override suspend fun rename(path: String, newName: String): FsEntry = withContext(Dispatchers.IO) {
        require(newName.isNotBlank() && !newName.contains(File.separatorChar)) { "Invalid name" }
        val target = File(File(path).parentFile, newName)
        entry(storage.rename(path, target.absolutePath).getOrThrow())
    }

    override suspend fun directorySize(path: String): Long = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists()) throw StorageException.NotFound(path)
        var total = 0L
        dir.walkTopDown().forEach { f ->
            currentCoroutineContext().ensureActive()
            if (f.isFile) total += f.length()
        }
        total
    }

    override suspend fun sha256(path: String): String = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.isFile) throw StorageException.NotAFile(path)
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
        } catch (e: IOException) {
            throw StorageException.Io(path, e)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun walk(path: String): Flow<Pair<String, Long>> = walkLocal(path)

    override fun copy(sources: List<String>, destinationDir: String): Flow<OperationProgress> = flow {
        val plan = plan(sources, destinationDir)
        val tracker = Tracker(plan.items.size, plan.totalBytes)
        emit(tracker.snapshot(""))
        for (item in plan.items) {
            currentCoroutineContext().ensureActive()
            copyItem(item, tracker)
            tracker.itemDone()
            emit(tracker.snapshot(item.source.name))
        }
    }.flowOn(Dispatchers.IO)

    override fun move(sources: List<String>, destinationDir: String): Flow<OperationProgress> = flow {
        val destination = File(destinationDir)
        if (!destination.isDirectory) throw StorageException.NotADirectory(destinationDir)
        val tracker = Tracker(sources.size, 0)
        emit(tracker.snapshot(""))
        for (source in sources) {
            currentCoroutineContext().ensureActive()
            val from = File(source)
            if (!from.exists()) throw StorageException.NotFound(source)
            val to = uniqueTarget(File(destination, from.name))
            if (!from.renameTo(to)) {
                // Different volume: copy the tree then delete the source.
                val plan = plan(listOf(source), destinationDir)
                val inner = Tracker(plan.items.size, plan.totalBytes)
                for (item in plan.items) {
                    currentCoroutineContext().ensureActive()
                    copyItem(item, inner)
                    emit(tracker.snapshot(item.source.name))
                }
                if (!from.deleteRecursively()) throw StorageException.Io(source, IOException("Could not delete source after copy"))
            }
            tracker.itemDone()
            emit(tracker.snapshot(from.name))
        }
    }.flowOn(Dispatchers.IO)

    override fun delete(paths: List<String>): Flow<OperationProgress> = flow {
        val targets = paths.map(::File)
        targets.firstOrNull { !it.exists() }?.let { throw StorageException.NotFound(it.absolutePath) }
        val all = targets.flatMap { it.walkBottomUp().toList() }
        val tracker = Tracker(all.size, 0)
        emit(tracker.snapshot(""))
        for (file in all) {
            currentCoroutineContext().ensureActive()
            if (!file.delete()) throw StorageException.Io(file.absolutePath, IOException("Could not delete"))
            tracker.itemDone()
            emit(tracker.snapshot(file.name))
        }
    }.flowOn(Dispatchers.IO)

    // Internals

    private fun entry(file: File): FsEntry {
        val isDirectory = file.isDirectory
        return FsEntry(
            path = file.absolutePath,
            name = file.name,
            isDirectory = isDirectory,
            size = if (isDirectory) 0 else file.length(),
            lastModified = file.lastModified(),
            isHidden = file.name.startsWith("."),
            isSymlink = runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false),
            canRead = file.canRead(),
            canWrite = file.canWrite(),
            childCount = if (isDirectory) file.list()?.size else null,
        )
    }

    private class CopyItem(val source: File, val target: File, val isDirectory: Boolean)
    private class Plan(val items: List<CopyItem>, val totalBytes: Long)

    private fun plan(sources: List<String>, destinationDir: String): Plan {
        val destination = File(destinationDir)
        if (!destination.isDirectory) throw StorageException.NotADirectory(destinationDir)
        val items = ArrayList<CopyItem>()
        var total = 0L
        for (source in sources) {
            val from = File(source)
            if (!from.exists()) throw StorageException.NotFound(source)
            if (destination.absolutePath.startsWith(from.absolutePath + File.separator) || destination.absolutePath == from.absolutePath) {
                throw StorageException.Unsupported("Cannot copy a directory into itself")
            }
            val root = uniqueTarget(File(destination, from.name))
            from.walkTopDown().forEach { f ->
                val relative = f.relativeTo(from).path
                val target = if (relative.isEmpty()) root else File(root, relative)
                val isDir = f.isDirectory
                if (!isDir) total += f.length()
                items += CopyItem(f, target, isDir)
            }
        }
        return Plan(items, total)
    }

    private suspend fun FlowCollector<OperationProgress>.copyItem(item: CopyItem, tracker: Tracker) {
        if (item.isDirectory) {
            if (!item.target.isDirectory && !item.target.mkdirs()) {
                throw StorageException.Io(item.target.absolutePath, IOException("Could not create directory"))
            }
            return
        }
        item.target.parentFile?.mkdirs()
        try {
            FileInputStream(item.source).use { input ->
                FileOutputStream(item.target).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var sinceEmit = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        tracker.bytes(n.toLong())
                        sinceEmit += n
                        if (sinceEmit >= EMIT_EVERY_BYTES) {
                            sinceEmit = 0
                            emit(tracker.snapshot(item.source.name))
                        }
                    }
                }
            }
            item.target.setLastModified(item.source.lastModified())
        } catch (e: IOException) {
            throw StorageException.Io(item.source.absolutePath, e)
        }
    }

    private class Tracker(val totalItems: Int, val totalBytes: Long) {
        private var doneItems = 0
        private var doneBytes = 0L
        fun itemDone() { doneItems++ }
        fun bytes(n: Long) { doneBytes += n }
        fun snapshot(name: String) = OperationProgress(totalItems, doneItems, totalBytes, doneBytes, name)
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
        const val EMIT_EVERY_BYTES = 1L shl 20

        fun uniqueTarget(wanted: File): File {
            if (!wanted.exists()) return wanted
            val base = wanted.nameWithoutExtension
            val ext = wanted.extension.let { if (it.isEmpty()) "" else ".$it" }
            var i = 1
            while (true) {
                val candidate = File(wanted.parentFile, "$base ($i)$ext")
                if (!candidate.exists()) return candidate
                i++
            }
        }
    }
}
