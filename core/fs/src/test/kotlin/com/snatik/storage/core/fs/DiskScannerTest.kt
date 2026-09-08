package com.snatik.storage.core.fs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.snatik.storage.Storage
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class DiskScannerTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun buildsTreeWithSizesAndLargestFiles() = runTest {
        val root = temp.root
        File(root, "a/b").mkdirs()
        File(root, "a/one.bin").writeBytes(ByteArray(300))
        File(root, "a/b/two.bin").writeBytes(ByteArray(500))
        File(root, "top.bin").writeBytes(ByteArray(100))

        val fs = LocalFileSystem(Storage(ApplicationProvider.getApplicationContext<Context>()))
        val done = DiskScanner(fs).scan(root.absolutePath, largestCount = 2).last()
        assertIs<ScanEvent.Done>(done)
        assertEquals(900, done.root.size)
        assertEquals(1, done.root.fileCount)
        val a = done.root.children.single()
        assertEquals("a", a.name)
        assertEquals(800, a.size)
        assertEquals("b", a.children.single().name)
        assertEquals(500, a.children.single().size)
        assertEquals(listOf(500L, 300L), done.largest.map { it.size })
    }
}
