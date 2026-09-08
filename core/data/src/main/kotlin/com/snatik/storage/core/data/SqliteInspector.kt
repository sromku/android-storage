package com.snatik.storage.core.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.snatik.storage.core.fs.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class TableInfo(val name: String, val type: String, val rowCount: Long?)

data class ColumnInfo(val name: String, val type: String, val notNull: Boolean, val primaryKey: Boolean, val defaultValue: String?)

/**
 * Opens SQLite files from anywhere the routed file system can read. Files this uid cannot open
 * directly are copied into the cache, so changes made there need [OpenDatabase.saveBack].
 */
class SqliteInspector(private val context: Context, private val fs: FileSystem) {

    class OpenDatabase internal constructor(
        val sourcePath: String,
        internal val workingFile: File,
        val isCopy: Boolean,
        private val db: SQLiteDatabase,
        private val fs: FileSystem,
    ) : AutoCloseable {

        val readOnly: Boolean get() = db.isReadOnly

        fun tables(): List<TableInfo> = db.rawQuery(
            "SELECT name, type FROM sqlite_master WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY type, name",
            null,
        ).use { c ->
            val out = ArrayList<TableInfo>()
            while (c.moveToNext()) {
                val name = c.getString(0)
                val type = c.getString(1)
                val count = if (type == "table") runCatching { count(name) }.getOrNull() else null
                out += TableInfo(name, type, count)
            }
            out
        }

        fun count(table: String): Long = db.rawQuery("SELECT count(*) FROM ${quote(table)}", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0 }

        fun columns(table: String): List<ColumnInfo> = db.rawQuery("PRAGMA table_info(${quote(table)})", null).use { c ->
            val out = ArrayList<ColumnInfo>()
            while (c.moveToNext()) {
                out += ColumnInfo(
                    name = c.getString(1),
                    type = c.getString(2).orEmpty(),
                    notNull = c.getInt(3) != 0,
                    primaryKey = c.getInt(5) != 0,
                    defaultValue = c.getString(4),
                )
            }
            out
        }

        fun createSql(table: String): String? = db.rawQuery("SELECT sql FROM sqlite_master WHERE name = ?", arrayOf(table)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

        fun rows(table: String, limit: Int, offset: Int, orderBy: String? = null): Tabular {
            val order = orderBy?.let { " ORDER BY $it" } ?: ""
            return query("SELECT * FROM ${quote(table)}$order LIMIT ${limit + 1} OFFSET $offset", limit)
        }

        /** Run any statement. SELECT-like statements return rows, others run and return an empty table. */
        fun query(sql: String, limit: Int = 500): Tabular {
            val head = sql.trimStart().take(7).uppercase()
            return if (head.startsWith("SELECT") || head.startsWith("PRAGMA") || head.startsWith("WITH") || head.startsWith("EXPLAIN")) {
                db.rawQuery(sql, null).use { c -> read(c, limit) }
            } else {
                db.execSQL(sql)
                Tabular(emptyList(), emptyList(), source = Tabular.Source.SQLITE)
            }
        }

        private fun read(c: Cursor, limit: Int): Tabular {
            val columns = c.columnNames.toList()
            val rows = ArrayList<List<String?>>()
            while (c.moveToNext() && rows.size <= limit) {
                rows += columns.indices.map { i ->
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toString()
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).toString()
                        Cursor.FIELD_TYPE_BLOB -> "<blob ${c.getBlob(i).size} bytes>"
                        else -> c.getString(i)
                    }
                }
            }
            return Tabular(columns, rows.take(limit), truncated = rows.size > limit, source = Tabular.Source.SQLITE)
        }

        /** Write the working copy back over the original. Only meaningful when [isCopy]. */
        suspend fun saveBack() {
            if (!isCopy) return
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            val bytes = withContext(Dispatchers.IO) { workingFile.readBytes() }
            fs.writeBytes(sourcePath, bytes)
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                val side = sourcePath + suffix
                if (fs.stat(side) != null) runCatching { fs.delete(listOf(side)).collect { } }
            }
        }

        override fun close() = db.close()

        private fun quote(identifier: String) = "\"" + identifier.replace("\"", "\"\"") + "\""
    }

    suspend fun open(path: String, writable: Boolean = false): OpenDatabase = withContext(Dispatchers.IO) {
        val direct = File(path)
        if (direct.canRead() && (!writable || direct.canWrite())) {
            val flags = if (writable) SQLiteDatabase.OPEN_READWRITE else SQLiteDatabase.OPEN_READONLY
            val db = runCatching { SQLiteDatabase.openDatabase(path, null, flags) }.getOrNull()
            if (db != null) return@withContext OpenDatabase(path, direct, isCopy = false, db = db, fs = fs)
        }
        // Copy the database and its journal files into our cache and open that.
        val dir = File(context.cacheDir, "db/" + MessageDigest.getInstance("MD5").digest(path.toByteArray()).joinToString("") { "%02x".format(it) })
        dir.mkdirs()
        val working = File(dir, File(path).name)
        working.writeBytes(fs.readBytes(path))
        for (suffix in listOf("-wal", "-shm")) {
            val side = File(dir, working.name + suffix)
            if (fs.stat(path + suffix) != null) side.writeBytes(fs.readBytes(path + suffix)) else side.delete()
        }
        val db = SQLiteDatabase.openDatabase(working.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        OpenDatabase(path, working, isCopy = true, db = db, fs = fs)
    }
}
