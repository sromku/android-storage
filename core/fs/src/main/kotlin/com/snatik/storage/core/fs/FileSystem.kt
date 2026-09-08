package com.snatik.storage.core.fs

import com.snatik.storage.StorageException
import kotlinx.coroutines.flow.Flow

/**
 * A file system the explorer can browse and change. Implementations exist for the local
 * file system and, later, for shell-backed access with elevated privileges.
 *
 * Every function throws [StorageException] on failure.
 */
interface FileSystem {

    /** Metadata for one path, or null when it does not exist. */
    suspend fun stat(path: String): FsEntry?

    /** Direct children of a directory, hidden entries included. */
    suspend fun list(path: String): List<FsEntry>

    /** Read up to [maxLength] bytes starting at [offset]. */
    suspend fun readBytes(path: String, offset: Long = 0, maxLength: Int = Int.MAX_VALUE): ByteArray

    suspend fun writeBytes(path: String, bytes: ByteArray)

    suspend fun writeText(path: String, text: String) = writeBytes(path, text.toByteArray())

    suspend fun createDirectory(path: String): FsEntry

    /** Create an empty file. */
    suspend fun createFile(path: String): FsEntry

    /** Rename an entry in place. */
    suspend fun rename(path: String, newName: String): FsEntry

    /** Sum of every regular file below [path]. */
    suspend fun directorySize(path: String): Long

    suspend fun sha256(path: String): String

    /** Every regular file below [path] as (absolute path, size). Used by disk analysis. */
    fun walk(path: String): Flow<Pair<String, Long>>

    /** Copy files or directory trees into [destinationDir]. Name clashes get a numbered suffix. */
    fun copy(sources: List<String>, destinationDir: String): Flow<OperationProgress>

    /** Move files or directory trees into [destinationDir]. Renames when possible, copies otherwise. */
    fun move(sources: List<String>, destinationDir: String): Flow<OperationProgress>

    /** Delete files or whole directory trees. */
    fun delete(paths: List<String>): Flow<OperationProgress>
}
