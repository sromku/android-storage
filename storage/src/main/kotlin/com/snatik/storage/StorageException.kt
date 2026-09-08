package com.snatik.storage

import java.io.IOException

/**
 * Every failure a [Storage] operation can report. Operations return [Result], so a caller
 * either unwraps the value or inspects one of these.
 */
public sealed class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The path does not exist. */
    public class NotFound(public val path: String) : StorageException("Not found: $path")

    /** The path exists and the operation refuses to overwrite it. */
    public class AlreadyExists(public val path: String) : StorageException("Already exists: $path")

    /** A directory was required but the path is something else. */
    public class NotADirectory(public val path: String) : StorageException("Not a directory: $path")

    /** A regular file was required but the path is something else. */
    public class NotAFile(public val path: String) : StorageException("Not a file: $path")

    /** The underlying file system refused the operation. */
    public class Io(public val path: String, cause: Throwable) :
        StorageException("I/O failure on $path: ${cause.message ?: cause::class.simpleName}", cause)

    /** Encryption or decryption failed, typically a wrong key or corrupt data. */
    public class Crypto(cause: Throwable) :
        StorageException("Encryption failure: ${cause.message ?: cause::class.simpleName}", cause)

    /** The operation is not available in the current configuration. */
    public class Unsupported(message: String) : StorageException(message)
}

internal inline fun <T> storageResult(path: String, block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: StorageException) {
    Result.failure(e)
} catch (e: IOException) {
    Result.failure(StorageException.Io(path, e))
} catch (e: SecurityException) {
    Result.failure(StorageException.Io(path, e))
}
