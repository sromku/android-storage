package com.snatik.storage.core.fs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.snatik.storage.Storage
import com.snatik.storage.StorageException
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LocalFileSystemTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var fs: LocalFileSystem
    private lateinit var root: File

    @Before
    fun setUp() {
        fs = LocalFileSystem(Storage(ApplicationProvider.getApplicationContext<Context>()))
        root = temp.root
    }

    private fun file(vararg parts: String, content: String = "x"): File =
        File(root, parts.joinToString(File.separator)).apply { parentFile?.mkdirs(); writeText(content) }

    @Test
    fun statAndList() = runTest {
        file("dir", "a.txt", content = "hello")
        file("dir", ".hidden")
        File(root, "dir/sub").mkdirs()

        val dir = fs.stat(File(root, "dir").absolutePath)
        assertNotNull(dir)
        assertTrue(dir.isDirectory)
        assertEquals(3, dir.childCount)
        assertNull(fs.stat(File(root, "missing").absolutePath))

        val entries = fs.list(dir.path).sortedBy { it.name }
        assertEquals(listOf(".hidden", "a.txt", "sub"), entries.map { it.name })
        assertTrue(entries[0].isHidden)
        assertEquals(5, entries[1].size)
        assertEquals(FileKind.TEXT, entries[1].kind)
        assertEquals("text/plain", entries[1].mimeType)
        assertEquals(FileKind.DIRECTORY, entries[2].kind)
    }

    @Test
    fun listErrors() = runTest {
        assertFailsWith<StorageException.NotFound> { fs.list(File(root, "nope").absolutePath) }
        val f = file("f.txt")
        assertFailsWith<StorageException.NotADirectory> { fs.list(f.absolutePath) }
    }

    @Test
    fun readBytesWithRange() = runTest {
        val f = file("bin", content = "0123456789")
        assertContentEquals("0123456789".toByteArray(), fs.readBytes(f.absolutePath))
        assertContentEquals("345".toByteArray(), fs.readBytes(f.absolutePath, offset = 3, maxLength = 3))
        assertContentEquals("89".toByteArray(), fs.readBytes(f.absolutePath, offset = 8, maxLength = 100))
        assertContentEquals(ByteArray(0), fs.readBytes(f.absolutePath, offset = 50))
    }

    @Test
    fun createRenameWrite() = runTest {
        val dir = fs.createDirectory(File(root, "new/deep").absolutePath)
        assertTrue(dir.isDirectory)
        val created = fs.createFile(File(root, "new/deep/empty.txt").absolutePath)
        assertEquals(0, created.size)
        assertFailsWith<StorageException.AlreadyExists> { fs.createFile(created.path) }
        val renamed = fs.rename(created.path, "renamed.txt")
        assertEquals("renamed.txt", renamed.name)
        assertFalse(File(created.path).exists())
        fs.writeText(renamed.path, "text")
        assertEquals("text", File(renamed.path).readText())
        assertFailsWith<IllegalArgumentException> { fs.rename(renamed.path, "a/b") }
    }

    @Test
    fun directorySizeAndHash() = runTest {
        file("d", "a", content = "aaaa")
        file("d", "x", "b", content = "bb")
        assertEquals(6, fs.directorySize(File(root, "d").absolutePath))
        val f = file("h.txt", content = "abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", fs.sha256(f.absolutePath))
    }

    @Test
    fun copyTreeReportsProgressAndAvoidsClash() = runTest {
        file("src", "one.bin", content = "1".repeat(3000))
        file("src", "nested", "two.bin", content = "2".repeat(2000))
        File(root, "dst").mkdirs()
        file("dst", "src", "keep", content = "keep")

        val progress = fs.copy(listOf(File(root, "src").absolutePath), File(root, "dst").absolutePath).toList()
        val last = progress.last()
        assertEquals(5000, last.totalBytes)
        assertEquals(5000, last.doneBytes)
        assertEquals(last.totalItems, last.doneItems)
        assertEquals(1f, last.fraction)

        // The existing dst/src is untouched and the copy landed in "src (1)".
        assertEquals("keep", File(root, "dst/src/keep").readText())
        assertEquals("2".repeat(2000), File(root, "dst/src (1)/nested/two.bin").readText())
        assertTrue(File(root, "src/one.bin").exists())
    }

    @Test
    fun copyIntoItselfIsRefused() = runTest {
        File(root, "a/b").mkdirs()
        assertFailsWith<StorageException.Unsupported> {
            fs.copy(listOf(File(root, "a").absolutePath), File(root, "a/b").absolutePath).last()
        }
    }

    @Test
    fun moveAndDelete() = runTest {
        file("m", "f.txt", content = "moved")
        File(root, "target").mkdirs()
        fs.move(listOf(File(root, "m").absolutePath), File(root, "target").absolutePath).last()
        assertFalse(File(root, "m").exists())
        assertEquals("moved", File(root, "target/m/f.txt").readText())

        file("del", "x", "y", content = "y")
        val f = file("single.txt")
        val last = fs.delete(listOf(File(root, "del").absolutePath, f.absolutePath)).last()
        assertEquals(last.totalItems, last.doneItems)
        assertFalse(File(root, "del").exists())
        assertFalse(f.exists())
        assertFailsWith<StorageException.NotFound> { fs.delete(listOf(f.absolutePath)).last() }
    }
}
