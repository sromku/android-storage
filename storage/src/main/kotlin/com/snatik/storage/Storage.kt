package com.snatik.storage

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.os.StatFs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset

/**
 * Small file API over internal and external storage.
 *
 * All paths are absolute. Every operation returns a [Result] whose failure is a [StorageException].
 * Calls block the current thread; run them on an I/O dispatcher.
 *
 * When [encryption] is set, [createFile] encrypts what it writes and [readFile] decrypts what it reads.
 */
public class Storage(context: Context, public val encryption: Encryption? = null) {

    private val appContext = context.applicationContext

    // Locations

    /** Root of shared external storage, typically `/storage/emulated/0`. */
    public val externalStorageDirectory: File
        get() = Environment.getExternalStorageDirectory()

    /** A public directory such as [Environment.DIRECTORY_PICTURES]. */
    public fun externalPublicDirectory(type: String): File = Environment.getExternalStoragePublicDirectory(type)

    /** This app's external files directory, or null when external storage is unavailable. */
    public val externalFilesDirectory: File?
        get() = appContext.getExternalFilesDir(null)

    /** This app's private files directory. */
    public val internalFilesDirectory: File
        get() = appContext.filesDir

    /** This app's private cache directory. */
    public val internalCacheDirectory: File
        get() = appContext.cacheDir

    /** True when shared external storage is mounted read-write. */
    public val isExternalWritable: Boolean
        get() = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    // Queries

    public fun exists(path: String): Boolean = File(path).exists()
    public fun isDirectory(path: String): Boolean = File(path).isDirectory
    public fun isFile(path: String): Boolean = File(path).isFile

    // Directories

    /**
     * Create a directory and any missing parents.
     * Fails with [StorageException.AlreadyExists] unless [override] is true, in which case the
     * existing directory is deleted first.
     */
    public fun createDirectory(path: String, override: Boolean = false): Result<File> = storageResult(path) {
        val dir = File(path)
        if (dir.exists()) {
            if (!override) throw StorageException.AlreadyExists(path)
            if (!dir.deleteRecursively()) throw StorageException.Io(path, IOException("Could not delete existing directory"))
        }
        if (!dir.mkdirs()) throw StorageException.Io(path, IOException("mkdirs failed"))
        dir
    }

    /** Delete a directory and everything below it. */
    public fun deleteDirectory(path: String): Result<Unit> = storageResult(path) {
        val dir = File(path)
        if (!dir.exists()) throw StorageException.NotFound(path)
        if (!dir.isDirectory) throw StorageException.NotADirectory(path)
        if (!dir.deleteRecursively()) throw StorageException.Io(path, IOException("Could not delete directory"))
    }

    /** Regular files directly inside [dir], optionally filtered by [nameMatches] and sorted by [order]. */
    public fun listFiles(dir: String, nameMatches: Regex? = null, order: FileOrder? = null): Result<List<File>> =
        storageResult(dir) {
            val directory = File(dir)
            if (!directory.exists()) throw StorageException.NotFound(dir)
            if (!directory.isDirectory) throw StorageException.NotADirectory(dir)
            val entries = directory.listFiles() ?: throw StorageException.Io(dir, IOException("Could not list directory"))
            val filtered = if (nameMatches == null) entries.toList() else entries.filter { nameMatches.matches(it.name) }
            if (order == null) filtered else filtered.sortedWith(order.comparator)
        }

    /** Every regular file below [dir], at any depth. */
    public fun listFilesRecursively(dir: String): Result<List<File>> = storageResult(dir) {
        val directory = File(dir)
        if (!directory.exists()) throw StorageException.NotFound(dir)
        if (!directory.isDirectory) throw StorageException.NotADirectory(dir)
        directory.walkTopDown().filter { it.isFile }.toList()
    }

    // Files

    /** Write [content] to [path], creating parent directories and replacing any existing file. */
    public fun createFile(path: String, content: ByteArray): Result<File> = storageResult(path) {
        val file = File(path)
        if (file.isDirectory) throw StorageException.NotAFile(path)
        file.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw StorageException.Io(path, IOException("Could not create parent directory")) }
        val bytes = encryption?.encrypt(content) ?: content
        file.writeBytes(bytes)
        file
    }

    public fun createFile(path: String, content: String, charset: Charset = Charsets.UTF_8): Result<File> =
        createFile(path, content.toByteArray(charset))

    public fun createFile(path: String, content: Storable): Result<File> = createFile(path, content.toBytes())

    public fun createFile(
        path: String,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100,
    ): Result<File> {
        val buffer = ByteArrayOutputStream()
        bitmap.compress(format, quality, buffer)
        return createFile(path, buffer.toByteArray())
    }

    /** Delete a regular file. */
    public fun deleteFile(path: String): Result<Unit> = storageResult(path) {
        val file = File(path)
        if (!file.exists()) throw StorageException.NotFound(path)
        if (!file.isFile) throw StorageException.NotAFile(path)
        if (!file.delete()) throw StorageException.Io(path, IOException("Could not delete file"))
    }

    /** Read the whole file, decrypting when [encryption] is set. */
    public fun readFile(path: String): Result<ByteArray> = storageResult(path) {
        val file = File(path)
        if (!file.exists()) throw StorageException.NotFound(path)
        if (!file.isFile) throw StorageException.NotAFile(path)
        val bytes = file.readBytes()
        encryption?.decrypt(bytes) ?: bytes
    }

    public fun readTextFile(path: String, charset: Charset = Charsets.UTF_8): Result<String> =
        readFile(path).map { String(it, charset) }

    /**
     * Append bytes to an existing file. Not available when [encryption] is set, because an
     * encrypted file is a single authenticated message.
     */
    public fun appendFile(path: String, content: ByteArray): Result<File> = storageResult(path) {
        if (encryption != null) throw StorageException.Unsupported("Append is not supported on encrypted storage")
        val file = File(path)
        if (!file.exists()) throw StorageException.NotFound(path)
        if (!file.isFile) throw StorageException.NotAFile(path)
        file.appendBytes(content)
        file
    }

    public fun appendFile(path: String, content: String, charset: Charset = Charsets.UTF_8): Result<File> =
        appendFile(path, content.toByteArray(charset))

    /** Append [line] followed by a newline. */
    public fun appendLine(path: String, line: String, charset: Charset = Charsets.UTF_8): Result<File> =
        appendFile(path, (line + "\n").toByteArray(charset))

    // Moving things around

    /** Rename or move within the same volume. Fails if [toPath] exists. */
    public fun rename(fromPath: String, toPath: String): Result<File> = storageResult(fromPath) {
        val from = File(fromPath)
        val to = File(toPath)
        if (!from.exists()) throw StorageException.NotFound(fromPath)
        if (to.exists()) throw StorageException.AlreadyExists(toPath)
        if (!from.renameTo(to)) throw StorageException.Io(fromPath, IOException("rename failed"))
        to
    }

    /** Copy a file or a whole directory tree. Existing files at the destination are replaced. */
    public fun copy(fromPath: String, toPath: String): Result<File> = storageResult(fromPath) {
        val from = File(fromPath)
        val to = File(toPath)
        if (!from.exists()) throw StorageException.NotFound(fromPath)
        if (from.isDirectory) {
            if (!from.copyRecursively(to, overwrite = true)) throw StorageException.Io(fromPath, IOException("copy failed"))
        } else {
            from.copyTo(to, overwrite = true)
        }
        to
    }

    /** Move a file or directory. Tries a rename first and falls back to copy then delete. */
    public fun move(fromPath: String, toPath: String): Result<File> = storageResult(fromPath) {
        val from = File(fromPath)
        val to = File(toPath)
        if (!from.exists()) throw StorageException.NotFound(fromPath)
        if (to.exists()) throw StorageException.AlreadyExists(toPath)
        to.parentFile?.mkdirs()
        if (from.renameTo(to)) return@storageResult to
        copy(fromPath, toPath).getOrThrow()
        if (!from.deleteRecursively()) throw StorageException.Io(fromPath, IOException("Could not delete source after copy"))
        to
    }

    // Sizes

    /** Size of a regular file in [unit]. */
    public fun size(path: String, unit: SizeUnit = SizeUnit.B): Double = unit.convert(File(path).length())

    /** Size of a regular file as text such as `1.5 MB`. */
    public fun readableSize(path: String): String = File(path).length().toReadableSize()

    /** Total size of every regular file below [dir]. */
    public fun directorySize(dir: String): Result<Long> = listFilesRecursively(dir).map { files -> files.sumOf { it.length() } }

    /** Bytes available to this app on the volume containing [path]. */
    public fun freeSpace(path: String, unit: SizeUnit = SizeUnit.B): Long = StatFs(path).availableBytes / unit.bytes

    /** Bytes in use on the volume containing [path]. */
    public fun usedSpace(path: String, unit: SizeUnit = SizeUnit.B): Long = StatFs(path).let { it.totalBytes - it.availableBytes } / unit.bytes

    /** Size of the volume containing [path]. */
    public fun totalSpace(path: String, unit: SizeUnit = SizeUnit.B): Long = StatFs(path).totalBytes / unit.bytes
}
