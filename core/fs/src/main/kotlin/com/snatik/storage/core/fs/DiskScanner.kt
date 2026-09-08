package com.snatik.storage.core.fs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/** A directory tree with sizes, the result of a scan. Children are sorted largest first. */
class DirNode(val path: String, val name: String) {
    var size: Long = 0
        internal set
    var fileCount: Int = 0
        internal set
    internal val childMap = HashMap<String, DirNode>()
    val children: List<DirNode> get() = childMap.values.sortedByDescending { it.size }
    val isEmpty: Boolean get() = childMap.isEmpty() && fileCount == 0
}

data class LargeFile(val path: String, val size: Long)

data class ScanProgress(val filesSeen: Int, val bytesSeen: Long, val currentPath: String)

sealed interface ScanEvent {
    data class Progress(val progress: ScanProgress) : ScanEvent
    data class Done(val root: DirNode, val largest: List<LargeFile>) : ScanEvent
}

/** Walks a tree adding up file sizes. Works on the local file system or through a shell. */
class DiskScanner(private val fs: FileSystem) {

    fun scan(path: String, largestCount: Int = 100): Flow<ScanEvent> = flow {
        val root = DirNode(path.trimEnd('/').ifEmpty { "/" }, path.trimEnd('/').substringAfterLast('/').ifEmpty { "/" })
        val largest = ArrayList<LargeFile>(largestCount + 1)
        var files = 0
        var bytes = 0L
        var lastEmit = 0L
        fs.walk(path).collect { (filePath, size) ->
            currentCoroutineContext().ensureActive()
            files++
            bytes += size
            addFile(root, filePath, size)
            track(largest, filePath, size, largestCount)
            val now = System.currentTimeMillis()
            if (now - lastEmit > 200) {
                lastEmit = now
                emit(ScanEvent.Progress(ScanProgress(files, bytes, filePath)))
            }
        }
        emit(ScanEvent.Done(root, largest.sortedByDescending { it.size }))
    }.flowOn(Dispatchers.Default)

    private fun addFile(root: DirNode, filePath: String, size: Long) {
        val relative = filePath.removePrefix(root.path).trimStart('/')
        val parts = relative.split('/')
        var node = root
        node.size += size
        for (i in 0 until parts.size - 1) {
            val name = parts[i]
            node = node.childMap.getOrPut(name) { DirNode(node.path.trimEnd('/') + "/" + name, name) }
            node.size += size
        }
        node.fileCount++
    }

    private fun track(largest: ArrayList<LargeFile>, path: String, size: Long, limit: Int) {
        if (largest.size < limit) {
            largest += LargeFile(path, size)
            return
        }
        val minIndex = largest.indices.minBy { largest[it].size }
        if (largest[minIndex].size < size) largest[minIndex] = LargeFile(path, size)
    }
}

/** Every regular file below [path] with its size, from `java.io.File`. */
internal fun walkLocal(path: String): Flow<Pair<String, Long>> = flow {
    val root = File(path)
    root.walkTopDown().onFail { _, _ -> }.forEach { f ->
        if (f.isFile) emit(f.absolutePath to f.length())
    }
}.flowOn(Dispatchers.IO)
