package com.snatik.storage.core.apps

import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.fs.LargeFile
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.shellQuote
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** A group of files with identical content. */
data class DuplicateSet(val hash: String, val size: Long, val paths: List<String>) {
    val wasted: Long get() = size * (paths.size - 1)
}

data class InsightsReport(
    val root: String,
    val totalFiles: Int,
    val totalBytes: Long,
    val duplicateSets: List<DuplicateSet>,
    val wastedByDuplicates: Long,
    val emptyDirs: List<String>,
    val zeroByteFiles: List<String>,
    val largest: List<LargeFile>,
)

/** Leftover data for a package that is no longer installed. */
data class GhostFootprint(val packageName: String, val path: String, val bytes: Long)

sealed interface InsightsEvent {
    data class Progress(val phase: String, val filesSeen: Int) : InsightsEvent
    data class Done(val report: InsightsReport) : InsightsEvent
}

/**
 * Deep-scans a tree for space that can be reclaimed: duplicate files (grouped by size, then
 * confirmed by SHA-256), empty directories, zero-byte files, and the largest files. Content
 * hashing runs only within same-size groups, so most files are never hashed.
 */
class StorageInsights(
    private val fs: FileSystem,
    private val privilege: PrivilegeManager,
) {
    fun scan(root: String, maxDuplicateFiles: Int = 4000): Flow<InsightsEvent> = flow {
        val bySize = HashMap<Long, MutableList<String>>()
        val zeroByte = ArrayList<String>()
        val largest = ArrayList<LargeFile>()
        var files = 0
        var bytes = 0L
        var lastEmit = 0L

        fs.walk(root).collect { entry ->
            currentCoroutineContext().ensureActive()
            files++
            bytes += entry.size
            if (entry.size == 0L) {
                if (zeroByte.size < 2000) zeroByte += entry.path
            } else {
                bySize.getOrPut(entry.size) { ArrayList() }.add(entry.path)
                trackLargest(largest, entry.path, entry.size, 100)
            }
            val now = System.currentTimeMillis()
            if (now - lastEmit > 200) {
                lastEmit = now
                emit(InsightsEvent.Progress("Scanning", files))
            }
        }

        // Only size groups with more than one member can hold duplicates.
        val candidates = bySize.filterValues { it.size > 1 }
        val hashed = HashMap<String, MutableList<String>>() // hash -> paths
        val hashSize = HashMap<String, Long>()
        var hashedCount = 0
        outer@ for ((size, paths) in candidates.entries.sortedByDescending { it.key }) {
            for (path in paths) {
                currentCoroutineContext().ensureActive()
                if (hashedCount++ > maxDuplicateFiles) break@outer
                val digest = runCatching { fs.sha256(path) }.getOrNull() ?: continue
                val key = "$size:$digest"
                hashed.getOrPut(key) { ArrayList() }.add(path)
                hashSize[key] = size
                if (hashedCount % 25 == 0) emit(InsightsEvent.Progress("Hashing", hashedCount))
            }
        }
        val duplicateSets = hashed.filterValues { it.size > 1 }
            .map { (key, paths) -> DuplicateSet(key.substringAfter(':'), hashSize[key] ?: 0, paths) }
            .sortedByDescending { it.wasted }
        val wasted = duplicateSets.sumOf { it.wasted }

        val emptyDirs = findEmptyDirs(root)

        emit(
            InsightsEvent.Done(
                InsightsReport(
                    root = root,
                    totalFiles = files,
                    totalBytes = bytes,
                    duplicateSets = duplicateSets,
                    wastedByDuplicates = wasted,
                    emptyDirs = emptyDirs,
                    zeroByteFiles = zeroByte,
                    largest = largest.sortedByDescending { it.size },
                ),
            ),
        )
    }.flowOn(Dispatchers.Default)

    /**
     * Directories under the shared Android/data and Android/obb folders whose owning package is
     * no longer installed: leftover data from uninstalled apps. Needs a shell to list them and
     * their sizes. [installed] is the set of currently installed package names.
     */
    suspend fun ghosts(installed: Set<String>): List<GhostFootprint> {
        val executor = privilege.executor.value ?: return emptyList()
        val roots = listOf("/storage/emulated/0/Android/data", "/storage/emulated/0/Android/obb")
        val out = ArrayList<GhostFootprint>()
        for (base in roots) {
            val result = runCatching {
                executor.run("ls -1 ${base.shellQuote()} 2>/dev/null", timeoutMs = 30_000)
            }.getOrNull() ?: continue
            val orphans = result.out.lineSequence().map { it.trim() }
                .filter { it.isNotEmpty() && it.contains('.') && it !in installed }
                .toList()
            for (pkg in orphans) {
                val path = "$base/$pkg"
                val kb = runCatching {
                    executor.run("du -sk ${path.shellQuote()} 2>/dev/null | cut -f1", timeoutMs = 30_000).out.trim().toLongOrNull()
                }.getOrNull() ?: 0L
                out += GhostFootprint(pkg, path, kb * 1024)
            }
        }
        return out.sortedByDescending { it.bytes }
    }

    private suspend fun findEmptyDirs(root: String): List<String> {
        val executor = privilege.executor.value ?: return emptyList()
        val result = runCatching {
            executor.run("find ${root.shellQuote()} -type d -empty 2>/dev/null | head -n 500", timeoutMs = 120_000)
        }.getOrNull() ?: return emptyList()
        return result.out.lineSequence().filter { it.isNotBlank() }.map { it.trim() }.toList()
    }

    private fun trackLargest(largest: ArrayList<LargeFile>, path: String, size: Long, limit: Int) {
        if (largest.size < limit) {
            largest += LargeFile(path, size)
            return
        }
        val minIndex = largest.indices.minBy { largest[it].size }
        if (largest[minIndex].size < size) largest[minIndex] = LargeFile(path, size)
    }
}
