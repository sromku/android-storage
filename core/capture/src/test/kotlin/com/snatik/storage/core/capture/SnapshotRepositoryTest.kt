package com.snatik.storage.core.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.snatik.storage.Storage
import com.snatik.storage.core.fs.LocalFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class SnapshotRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var db: CaptureDatabase
    private lateinit var repo: SnapshotRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = CaptureDatabase.inMemory(context)
        repo = SnapshotRepository(context, LocalFileSystem(Storage(context)), db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun snapshotsAndDiffs() = runTest {
        val root = temp.root
        File(root, "keep.txt").writeText("same")
        File(root, "change.txt").writeText("v1")
        File(root, "gone.txt").writeText("bye")
        File(root, "moved/old.bin").apply { parentFile!!.mkdirs() }.writeBytes(ByteArray(64) { 7 })

        val first = repo.create(root.absolutePath, "one", SnapshotOptions(hash = true, keepCopies = true)).last()
        assertIs<SnapshotEvent.Done>(first)
        val a = repo.snapshot(first.id)!!
        assertEquals(4, a.fileCount)
        assertNotNull(repo.copyOf(first.id, "change.txt"))
        assertEquals("v1", repo.copyOf(first.id, "change.txt")!!.readText())

        File(root, "change.txt").writeText("version two")
        File(root, "gone.txt").delete()
        File(root, "new.txt").writeText("hello")
        File(root, "moved/old.bin").renameTo(File(root, "renamed.bin"))

        val second = repo.create(root.absolutePath, "two", SnapshotOptions(hash = true)).last() as SnapshotEvent.Done
        val diff = repo.diff(first.id, second.id)
        assertEquals(1, diff.count(ChangeKind.ADDED))
        assertEquals(1, diff.count(ChangeKind.REMOVED))
        assertEquals(1, diff.count(ChangeKind.MODIFIED))
        assertEquals(1, diff.count(ChangeKind.MOVED))
        assertEquals(1, diff.unchanged)
        assertEquals("renamed.bin", diff.changes.first { it.kind == ChangeKind.MOVED }.path)
        assertEquals("moved/old.bin", diff.changes.first { it.kind == ChangeKind.MOVED }.before!!.path)
        assertEquals(9, diff.changes.first { it.kind == ChangeKind.MODIFIED }.sizeDelta)

        assertEquals(2, repo.snapshots.first().size)
        repo.delete(first.id)
        assertNull(repo.snapshot(first.id))
        assertNull(repo.copyOf(first.id, "change.txt"))
    }
}
