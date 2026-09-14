package com.snatik.storage.core.capture

import android.content.Context
import com.snatik.storage.core.fs.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

data class SnapshotOptions(val hash: Boolean = false, val keepCopies: Boolean = false)

sealed interface SnapshotEvent {
    data class Progress(val files: Int, val bytes: Long, val current: String) : SnapshotEvent
    data class Done(val id: Long) : SnapshotEvent
}

enum class ChangeKind { ADDED, REMOVED, MODIFIED, MOVED }

data class FileChange(val kind: ChangeKind, val path: String, val before: SnapshotFileEntity?, val after: SnapshotFileEntity?) {
    val sizeDelta: Long get() = (after?.size ?: 0) - (before?.size ?: 0)
}

data class SnapshotDiff(val a: SnapshotEntity, val b: SnapshotEntity, val changes: List<FileChange>, val unchanged: Int) {
    fun count(kind: ChangeKind): Int = changes.count { it.kind == kind }
}

/** Freezes a directory tree into the database, optionally with hashes and copies, and diffs them. */
class SnapshotRepository(private val context: Context, private val fs: FileSystem, private val db: CaptureDatabase) {

    val snapshots: Flow<List<SnapshotEntity>> = db.snapshots().snapshots()

    private fun copyDir(id: Long) = File(context.filesDir, "snapshots/$id")

    fun create(rootPath: String, label: String, options: SnapshotOptions): Flow<SnapshotEvent> = flow {
        val root = rootPath.trimEnd('/').ifEmpty { "/" }
        val files = ArrayList<SnapshotFileEntity>()
        var bytes = 0L
        var lastEmit = 0L
        fs.walk(root).collect { entry ->
            currentCoroutineContext().ensureActive()
            val relative = entry.path.removePrefix(root).trimStart('/')
            files += SnapshotFileEntity(0, relative, entry.size, entry.lastModified, null)
            bytes += entry.size
            val now = System.currentTimeMillis()
            if (now - lastEmit > 200) {
                lastEmit = now
                emit(SnapshotEvent.Progress(files.size, bytes, relative))
            }
        }
        val id = db.snapshots().insert(
            SnapshotEntity(label = label, rootPath = root, createdAt = System.currentTimeMillis(), fileCount = files.size, totalBytes = bytes, hashed = options.hash, copied = options.keepCopies),
        )
        val dir = copyDir(id)
        val finished = ArrayList<SnapshotFileEntity>(files.size)
        files.forEachIndexed { index, f ->
            currentCoroutineContext().ensureActive()
            val absolute = if (root == "/") "/" + f.path else "$root/${f.path}"
            val hash = if (options.hash) runCatching { fs.sha256(absolute) }.getOrNull() else null
            if (options.keepCopies) {
                runCatching {
                    val target = File(dir, f.path)
                    target.parentFile?.mkdirs()
                    target.writeBytes(fs.readBytes(absolute))
                }
            }
            finished += f.copy(snapshotId = id, hash = hash)
            if (index % 50 == 0) emit(SnapshotEvent.Progress(index, bytes, f.path))
        }
        finished.chunked(500).forEach { db.snapshots().insertFiles(it) }
        emit(SnapshotEvent.Done(id))
    }.flowOn(Dispatchers.IO)

    suspend fun snapshot(id: Long): SnapshotEntity? = db.snapshots().snapshot(id)
    suspend fun rename(id: Long, label: String) = withContext(Dispatchers.IO) { db.snapshots().rename(id, label) }
    suspend fun files(id: Long): List<SnapshotFileEntity> = db.snapshots().files(id)

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.snapshots().deleteFiles(id)
        db.snapshots().delete(id)
        copyDir(id).deleteRecursively()
    }

    /** The stored copy of a file, when the snapshot kept copies. */
    fun copyOf(snapshotId: Long, relativePath: String): File? = File(copyDir(snapshotId), relativePath).takeIf { it.isFile }

    suspend fun diff(aId: Long, bId: Long): SnapshotDiff = withContext(Dispatchers.Default) {
        val a = db.snapshots().snapshot(aId) ?: error("Snapshot $aId is gone")
        val b = db.snapshots().snapshot(bId) ?: error("Snapshot $bId is gone")
        val before = db.snapshots().files(aId).associateBy { it.path }
        val after = db.snapshots().files(bId).associateBy { it.path }
        diff(a, b, before, after)
    }

    companion object {
        fun diff(a: SnapshotEntity, b: SnapshotEntity, before: Map<String, SnapshotFileEntity>, after: Map<String, SnapshotFileEntity>): SnapshotDiff {
            val changes = ArrayList<FileChange>()
            var unchanged = 0
            val removed = ArrayList<SnapshotFileEntity>()
            val added = ArrayList<SnapshotFileEntity>()
            for ((path, old) in before) {
                val new = after[path]
                when {
                    new == null -> removed += old
                    same(old, new) -> unchanged++
                    else -> changes += FileChange(ChangeKind.MODIFIED, path, old, new)
                }
            }
            for ((path, new) in after) if (path !in before) added += new
            // Renames: a removed and an added file with the same hash are one move.
            val addedByHash = added.filter { it.hash != null }.groupBy { it.hash!! }.mapValues { it.value.toMutableList() }
            val movedAdded = HashSet<SnapshotFileEntity>()
            for (old in removed) {
                val candidates = old.hash?.let { addedByHash[it] }
                val match = candidates?.firstOrNull { it.size == old.size && it !in movedAdded }
                if (match != null) {
                    movedAdded += match
                    changes += FileChange(ChangeKind.MOVED, match.path, old, match)
                } else {
                    changes += FileChange(ChangeKind.REMOVED, old.path, old, null)
                }
            }
            for (new in added) if (new !in movedAdded) changes += FileChange(ChangeKind.ADDED, new.path, null, new)
            return SnapshotDiff(a, b, changes.sortedWith(compareBy({ it.kind.ordinal }, { it.path })), unchanged)
        }

        private fun same(old: SnapshotFileEntity, new: SnapshotFileEntity): Boolean {
            if (old.hash != null && new.hash != null) return old.hash == new.hash
            return old.size == new.size && old.lastModified == new.lastModified
        }
    }
}
