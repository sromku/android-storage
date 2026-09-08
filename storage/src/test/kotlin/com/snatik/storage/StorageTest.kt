package com.snatik.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class StorageTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var storage: Storage
    private lateinit var root: String

    @Before
    fun setUp() {
        storage = Storage(ApplicationProvider.getApplicationContext<Context>())
        root = temp.root.absolutePath
    }

    private fun path(vararg parts: String) = (listOf(root) + parts).joinToString(File.separator)

    @Test
    fun locationsResolve() {
        assertTrue(storage.internalFilesDirectory.absolutePath.isNotEmpty())
        assertTrue(storage.internalCacheDirectory.absolutePath.isNotEmpty())
        assertTrue(storage.externalStorageDirectory.absolutePath.isNotEmpty())
    }

    @Test
    fun createDirectoryCreatesParents() {
        val dir = storage.createDirectory(path("a", "b", "c")).getOrThrow()
        assertTrue(dir.isDirectory)
        assertTrue(storage.isDirectory(dir.absolutePath))
    }

    @Test
    fun createDirectoryFailsWhenExists() {
        storage.createDirectory(path("dir")).getOrThrow()
        val failure = storage.createDirectory(path("dir")).exceptionOrNull()
        assertIs<StorageException.AlreadyExists>(failure)
    }

    @Test
    fun createDirectoryWithOverrideWipesContent() {
        storage.createDirectory(path("dir")).getOrThrow()
        storage.createFile(path("dir", "f.txt"), "x").getOrThrow()
        storage.createDirectory(path("dir"), override = true).getOrThrow()
        assertFalse(storage.exists(path("dir", "f.txt")))
    }

    @Test
    fun deleteDirectoryRemovesTree() {
        storage.createFile(path("dir", "sub", "f.txt"), "x").getOrThrow()
        storage.deleteDirectory(path("dir")).getOrThrow()
        assertFalse(storage.exists(path("dir")))
        assertIs<StorageException.NotFound>(storage.deleteDirectory(path("dir")).exceptionOrNull())
    }

    @Test
    fun deleteDirectoryRejectsFile() {
        storage.createFile(path("f.txt"), "x").getOrThrow()
        assertIs<StorageException.NotADirectory>(storage.deleteDirectory(path("f.txt")).exceptionOrNull())
    }

    @Test
    fun writeAndReadText() {
        storage.createFile(path("hello.txt"), "hello world").getOrThrow()
        assertEquals("hello world", storage.readTextFile(path("hello.txt")).getOrThrow())
    }

    @Test
    fun writeAndReadBytes() {
        val bytes = ByteArray(1000) { it.toByte() }
        storage.createFile(path("bin"), bytes).getOrThrow()
        assertContentEquals(bytes, storage.readFile(path("bin")).getOrThrow())
    }

    @Test
    fun writeStorable() {
        storage.createFile(path("s.txt"), Storable { "storable".toByteArray() }).getOrThrow()
        assertEquals("storable", storage.readTextFile(path("s.txt")).getOrThrow())
    }

    @Test
    fun readMissingFileIsNotFound() {
        assertIs<StorageException.NotFound>(storage.readFile(path("nope")).exceptionOrNull())
    }

    @Test
    fun readDirectoryIsNotAFile() {
        storage.createDirectory(path("dir")).getOrThrow()
        assertIs<StorageException.NotAFile>(storage.readFile(path("dir")).exceptionOrNull())
    }

    @Test
    fun appendAndAppendLine() {
        storage.createFile(path("log.txt"), "one").getOrThrow()
        storage.appendFile(path("log.txt"), " two").getOrThrow()
        storage.appendLine(path("log.txt"), " three").getOrThrow()
        assertEquals("one two three\n", storage.readTextFile(path("log.txt")).getOrThrow())
    }

    @Test
    fun appendToMissingFileFails() {
        assertIs<StorageException.NotFound>(storage.appendFile(path("missing"), "x").exceptionOrNull())
    }

    @Test
    fun deleteFile() {
        storage.createFile(path("f.txt"), "x").getOrThrow()
        storage.deleteFile(path("f.txt")).getOrThrow()
        assertFalse(storage.exists(path("f.txt")))
        assertIs<StorageException.NotFound>(storage.deleteFile(path("f.txt")).exceptionOrNull())
    }

    @Test
    fun listFilesFiltersAndOrders() {
        storage.createFile(path("dir", "b.txt"), "bb").getOrThrow()
        storage.createFile(path("dir", "a.txt"), "a").getOrThrow()
        storage.createFile(path("dir", "c.log"), "ccc").getOrThrow()
        storage.createDirectory(path("dir", "sub")).getOrThrow()

        val all = storage.listFiles(path("dir"), order = FileOrder.NAME).getOrThrow().map { it.name }
        assertEquals(listOf("a.txt", "b.txt", "c.log", "sub"), all)

        val txt = storage.listFiles(path("dir"), nameMatches = Regex(".*\\.txt"), order = FileOrder.NAME).getOrThrow().map { it.name }
        assertEquals(listOf("a.txt", "b.txt"), txt)

        val bySize = storage.listFiles(path("dir"), nameMatches = Regex(".*\\..*"), order = FileOrder.LARGEST_FIRST).getOrThrow().map { it.name }
        assertEquals(listOf("c.log", "b.txt", "a.txt"), bySize)

        val dirsFirst = storage.listFiles(path("dir"), order = FileOrder.DIRECTORIES_FIRST).getOrThrow().map { it.name }
        assertEquals("sub", dirsFirst.first())
    }

    @Test
    fun listFilesOnMissingOrFilePath() {
        assertIs<StorageException.NotFound>(storage.listFiles(path("nope")).exceptionOrNull())
        storage.createFile(path("f.txt"), "x").getOrThrow()
        assertIs<StorageException.NotADirectory>(storage.listFiles(path("f.txt")).exceptionOrNull())
    }

    @Test
    fun listFilesRecursivelyReturnsOnlyFiles() {
        storage.createFile(path("dir", "a.txt"), "a").getOrThrow()
        storage.createFile(path("dir", "x", "y", "b.txt"), "b").getOrThrow()
        storage.createDirectory(path("dir", "empty")).getOrThrow()
        val names = storage.listFilesRecursively(path("dir")).getOrThrow().map { it.name }.sorted()
        assertEquals(listOf("a.txt", "b.txt"), names)
    }

    @Test
    fun renameMovesAndRefusesToOverwrite() {
        storage.createFile(path("a.txt"), "a").getOrThrow()
        storage.createFile(path("b.txt"), "b").getOrThrow()
        storage.rename(path("a.txt"), path("c.txt")).getOrThrow()
        assertFalse(storage.exists(path("a.txt")))
        assertEquals("a", storage.readTextFile(path("c.txt")).getOrThrow())
        assertIs<StorageException.AlreadyExists>(storage.rename(path("c.txt"), path("b.txt")).exceptionOrNull())
    }

    @Test
    fun copyFileAndDirectory() {
        storage.createFile(path("src", "deep", "f.txt"), "content").getOrThrow()
        storage.copy(path("src", "deep", "f.txt"), path("copy.txt")).getOrThrow()
        assertEquals("content", storage.readTextFile(path("copy.txt")).getOrThrow())

        storage.copy(path("src"), path("dst")).getOrThrow()
        assertEquals("content", storage.readTextFile(path("dst", "deep", "f.txt")).getOrThrow())
        assertTrue(storage.exists(path("src", "deep", "f.txt")))
    }

    @Test
    fun moveFileAndDirectory() {
        storage.createFile(path("src", "f.txt"), "content").getOrThrow()
        storage.move(path("src"), path("moved")).getOrThrow()
        assertFalse(storage.exists(path("src")))
        assertEquals("content", storage.readTextFile(path("moved", "f.txt")).getOrThrow())
        assertIs<StorageException.NotFound>(storage.move(path("src"), path("x")).exceptionOrNull())
    }

    @Test
    fun sizes() {
        storage.createFile(path("dir", "a"), ByteArray(1024)).getOrThrow()
        storage.createFile(path("dir", "b"), ByteArray(2048)).getOrThrow()
        assertEquals(1.0, storage.size(path("dir", "a"), SizeUnit.KB))
        assertEquals("2.0 KB", storage.readableSize(path("dir", "b")))
        assertEquals(3072L, storage.directorySize(path("dir")).getOrThrow())
    }

    @Test
    fun volumeSpace() {
        // Robolectric's StatFs reports nothing unless a volume is registered. Block size is 4096.
        ShadowStatFs.registerStats(root, 1000, 400, 300)
        assertEquals(1000L * 4096, storage.totalSpace(root))
        assertEquals(300L * 4096, storage.freeSpace(root))
        assertEquals(700L * 4096, storage.usedSpace(root))
        assertEquals(700L * 4, storage.usedSpace(root, SizeUnit.KB))
    }

    @Test
    fun encryptedStorageRoundTrips() {
        val key = Encryption.generateKey()
        val secure = Storage(ApplicationProvider.getApplicationContext(), Encryption.fromKey(key))
        secure.createFile(path("secret.txt"), "top secret").getOrThrow()

        val raw = storage.readTextFile(path("secret.txt")).getOrThrow()
        assertFalse(raw.contains("top secret"))
        assertEquals("top secret", secure.readTextFile(path("secret.txt")).getOrThrow())

        val otherKey = Storage(ApplicationProvider.getApplicationContext(), Encryption.fromKey(Encryption.generateKey()))
        assertIs<StorageException.Crypto>(otherKey.readFile(path("secret.txt")).exceptionOrNull())
        assertIs<StorageException.Unsupported>(secure.appendFile(path("secret.txt"), "more").exceptionOrNull())
    }
}
