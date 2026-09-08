package com.snatik.storage.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.snatik.storage.Storage
import com.snatik.storage.core.fs.LocalFileSystem
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SqliteInspectorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun makeDb(): File {
        val file = File(temp.root, "test.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT NOT NULL, score REAL DEFAULT 0)")
            db.execSQL("INSERT INTO users (name, score) VALUES ('a', 1.5), ('b', 2.5), ('c', 3.5)")
            db.execSQL("CREATE VIEW top AS SELECT name FROM users WHERE score > 2")
        }
        return file
    }

    @Test
    fun readsSchemaAndRows() = runTest {
        val file = makeDb()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inspector = SqliteInspector(context, LocalFileSystem(Storage(context)))
        inspector.open(file.absolutePath).use { db ->
            assertFalse(db.isCopy)
            val tables = db.tables()
            assertEquals(listOf("users" to 3L, "top" to null), tables.map { it.name to it.rowCount })
            val cols = db.columns("users")
            assertEquals(listOf("id", "name", "score"), cols.map { it.name })
            assertTrue(cols[0].primaryKey)
            assertTrue(cols[1].notNull)
            assertEquals("0", cols[2].defaultValue)
            val page = db.rows("users", limit = 2, offset = 0)
            assertEquals(2, page.rows.size)
            assertEquals("a", page.rows[0][1])
            assertTrue(page.truncated)
            val tail = db.rows("users", limit = 2, offset = 1)
            assertEquals(listOf("b", "c"), tail.rows.map { it[1] })
            assertFalse(tail.truncated)
            val q = db.query("SELECT count(*) AS n FROM users")
            assertEquals("3", q.rows[0][0])
            assertTrue(db.createSql("users")!!.startsWith("CREATE TABLE users"))
        }
    }

    @Test
    fun copiesUnreadableDatabases() = runTest {
        val file = makeDb()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inspector = SqliteInspector(context, LocalFileSystem(Storage(context)))
        // Ask for a writable handle on a read-only file: forces the copy path.
        file.setWritable(false)
        inspector.open(file.absolutePath, writable = true).use { db ->
            assertTrue(db.isCopy)
            db.query("UPDATE users SET name = 'z' WHERE id = 1")
            assertEquals("z", db.rows("users", 10, 0).rows[0][1])
        }
    }
}
