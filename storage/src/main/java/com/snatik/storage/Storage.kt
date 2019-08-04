package com.snatik.storage

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log

import com.snatik.storage.helpers.ImmutablePair
import com.snatik.storage.helpers.SizeUnit
import com.snatik.storage.security.SecurityUtil

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.io.IOException
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.util.ArrayList
import java.util.Arrays
import java.util.LinkedList

import javax.crypto.Cipher

/**
 * Common class for internal and external storage implementations
 *
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */

class Storage(private val mContext: Context) {
    private var mConfiguration: EncryptConfiguration? = null

    val externalStorageDirectory: String
        get() = Environment.getExternalStorageDirectory().absolutePath

    val internalRootDirectory: String
        get() = Environment.getRootDirectory().absolutePath

    val internalFilesDirectory: String
        get() = mContext.filesDir.absolutePath

    val internalCacheDirectory: String
        get() = mContext.cacheDir.absolutePath

    fun setEncryptConfiguration(configuration: EncryptConfiguration) {
        mConfiguration = configuration
    }

    fun getExternalStorageDirectory(publicDirectory: String): String {
        return Environment.getExternalStoragePublicDirectory(publicDirectory).absolutePath
    }

    fun createDirectory(path: String): Boolean {
        val directory = File(path)
        if (directory.exists()) {
            Log.w(TAG, "Directory '$path' already exists")
            return false
        }
        return directory.mkdirs()
    }

    fun createDirectory(path: String, override: Boolean): Boolean {

        // Check if directory exists. If yes, then delete all directory
        if (override && isDirectoryExists(path)) {
            deleteDirectory(path)
        }

        // Create new directory
        return createDirectory(path)
    }

    fun deleteDirectory(path: String): Boolean {
        return deleteDirectoryImpl(path)
    }

    fun isDirectoryExists(path: String): Boolean {
        return File(path).exists()
    }

    fun createFile(path: String, content: String): Boolean {
        return createFile(path, content.toByteArray())
    }

    fun createFile(path: String, storable: Storable): Boolean {
        return createFile(path, storable.bytes)
    }

    fun createFile(path: String, content: ByteArray?): Boolean {
        var content = content
        try {
            val stream = FileOutputStream(File(path))

            // encrypt if needed
            if (mConfiguration != null && mConfiguration!!.isEncrypted) {
                content = encrypt(content, Cipher.ENCRYPT_MODE)
            }

            stream.write(content!!)
            stream.flush()
            stream.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed create file", e)
            return false
        }

        return true
    }

    fun createFile(path: String, bitmap: Bitmap): Boolean {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        return createFile(path, byteArray)
    }

    fun deleteFile(path: String): Boolean {
        val file = File(path)
        return file.delete()
    }

    fun isFileExist(path: String): Boolean {
        return File(path).exists()
    }

    fun readFile(path: String): ByteArray? {
        val stream: FileInputStream
        try {
            stream = FileInputStream(File(path))
            return readFile(stream)
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Failed to read file to input stream", e)
            return null
        }

    }

    fun readTextFile(path: String): String {
        val bytes = readFile(path)
        return String(bytes!!)
    }

    fun appendFile(path: String, content: String) {
        appendFile(path, content.toByteArray())
    }

    fun appendFile(path: String, bytes: ByteArray) {
        if (!isFileExist(path)) {
            Log.w(TAG, "Impossible to append content, because such file doesn't exist")
            return
        }

        try {
            val stream = FileOutputStream(File(path), true)
            stream.write(bytes)
            val separator = System.getProperty("line.separator")
            if (separator != null) {
                stream.write(separator.toByteArray())
            }
            stream.flush()
            stream.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to append content to file", e)
        }

    }

    fun getNestedFiles(path: String): List<File> {
        val file = File(path)
        val out = ArrayList<File>()
        getDirectoryFilesImpl(file, out)
        return out
    }

    @JvmOverloads
    fun getFiles(dir: String, matchRegex: String? = null): List<File>? {
        val file = File(dir)
        var files: Array<File>? = null
        files = if (matchRegex != null) {
            val filter = FilenameFilter { _, fileName -> fileName.matches(matchRegex.toRegex()) }
            file.listFiles(filter)
        } else {
            file.listFiles()
        }
        return if (files != null) Arrays.asList(*files) else null
    }

    fun getFile(path: String): File {
        return File(path)
    }

    fun rename(fromPath: String, toPath: String): Boolean {
        val file = getFile(fromPath)
        val newFile = File(toPath)
        return file.renameTo(newFile)
    }

    fun getSize(file: File, unit: SizeUnit): Double {
        val length = file.length()
        return length.toDouble() / unit.inBytes().toDouble()
    }

    fun getReadableSize(file: File): String {
        val length = file.length()
        return SizeUnit.readableSizeUnit(length)
    }

    fun getFreeSpace(dir: String, sizeUnit: SizeUnit): Long {
        val statFs = StatFs(dir)
        val availableBlocks: Long
        val blockSize: Long
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            availableBlocks = statFs.availableBlocks.toLong()
            blockSize = statFs.blockSize.toLong()
        } else {
            availableBlocks = statFs.availableBlocksLong
            blockSize = statFs.blockSizeLong
        }
        val freeBytes = availableBlocks * blockSize
        return freeBytes / sizeUnit.inBytes()
    }

    fun getUsedSpace(dir: String, sizeUnit: SizeUnit): Long {
        val statFs = StatFs(dir)
        val availableBlocks: Long
        val blockSize: Long
        val totalBlocks: Long
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            availableBlocks = statFs.availableBlocks.toLong()
            blockSize = statFs.blockSize.toLong()
            totalBlocks = statFs.blockCount.toLong()
        } else {
            availableBlocks = statFs.availableBlocksLong
            blockSize = statFs.blockSizeLong
            totalBlocks = statFs.blockCountLong
        }
        val usedBytes = totalBlocks * blockSize - availableBlocks * blockSize
        return usedBytes / sizeUnit.inBytes()
    }

    fun copy(fromPath: String, toPath: String): Boolean {
        val file = getFile(fromPath)
        if (!file.isFile) {
            return false
        }

        var inStream: FileInputStream? = null
        var outStream: FileOutputStream? = null
        try {
            inStream = FileInputStream(file)
            outStream = FileOutputStream(File(toPath))
            val inChannel = inStream.channel
            val outChannel = outStream.channel
            inChannel.transferTo(0, inChannel.size(), outChannel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed copy", e)
            return false
        } finally {
            closeSilently(inStream)
            closeSilently(outStream)
        }
        return true
    }

    fun move(fromPath: String, toPath: String): Boolean {
        return if (copy(fromPath, toPath)) {
            getFile(fromPath).delete()
        } else false
    }

    protected fun readFile(stream: FileInputStream): ByteArray? {
        open class Reader : Thread() {
            var array: ByteArray? = null
        }

        val reader = object : Reader() {
            override fun run() {
                val chunks = LinkedList<ImmutablePair<ByteArray, Int>>()

                // read the file and build chunks
                var size = 0
                var globalSize = 0
                do {
                    try {
                        val chunkSize = if (mConfiguration != null) mConfiguration!!.chuckSize else 8192
                        // read chunk
                        val buffer = ByteArray(chunkSize)
                        size = stream.read(buffer, 0, chunkSize)
                        if (size > 0) {
                            globalSize += size

                            // add chunk to list
                            chunks.add(ImmutablePair(buffer, size))
                        }
                    } catch (e: Exception) {
                        // very bad
                    }

                } while (size > 0)

                try {
                    stream.close()
                } catch (e: Exception) {
                    // very bad
                }

                array = ByteArray(globalSize)

                // append all chunks to one array
                var offset = 0
                for (chunk in chunks) {
                    // flush chunk to array
                    System.arraycopy(chunk.element1!!, 0, array!!, offset, chunk.element2!!)
                    offset += chunk.element2
                }
            }
        }

        reader.start()
        try {
            reader.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Failed on reading file from storage while the locking Thread", e)
            return null
        }

        return if (mConfiguration != null && mConfiguration!!.isEncrypted) {
            encrypt(reader.array, Cipher.DECRYPT_MODE)
        } else {
            reader.array
        }
    }

    @Synchronized
    private fun encrypt(content: ByteArray?, encryptionMode: Int): ByteArray? {
        val secretKey = mConfiguration!!.secretKey
        val ivx = mConfiguration!!.ivParameter
        return SecurityUtil.encrypt(content, encryptionMode, secretKey!!, ivx!!)
    }

    private fun deleteDirectoryImpl(path: String): Boolean {
        val directory = File(path)

        // If the directory exists then delete
        if (directory.exists()) {
            val files = directory.listFiles() ?: return true
// Run on all sub files and folders and delete them
            for (i in files.indices) {
                if (files[i].isDirectory) {
                    deleteDirectoryImpl(files[i].absolutePath)
                } else {
                    files[i].delete()
                }
            }
        }
        return directory.delete()
    }

    private fun getDirectoryFilesImpl(directory: File, out: MutableList<File>) {
        if (directory.exists()) {
            val files = directory.listFiles()
            if (files == null) {
                return
            } else {
                for (i in files.indices) {
                    if (files[i].isDirectory) {
                        getDirectoryFilesImpl(files[i], out)
                    } else {
                        out.add(files[i])
                    }
                }
            }
        }
    }

    private fun closeSilently(closeable: Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (e: IOException) {
            }

        }
    }

    companion object {

        private val TAG = "Storage"

        val isExternalWritable: Boolean
            get() {
                val state = Environment.getExternalStorageState()
                return if (Environment.MEDIA_MOUNTED == state) {
                    true
                } else false
            }
    }
}
