package com.snatik.storage.app.feature.data

import com.snatik.storage.core.data.SqliteInspector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps one open handle per database path so the table screens share it with the database screen. */
class DatabaseSessions(private val inspector: SqliteInspector) {

    private val open = HashMap<String, SqliteInspector.OpenDatabase>()
    private val mutex = Mutex()

    suspend fun get(path: String): SqliteInspector.OpenDatabase = mutex.withLock {
        open[path] ?: inspector.open(path, writable = true).also { open[path] = it }
    }

    suspend fun close(path: String) = mutex.withLock {
        open.remove(path)?.close()
    }
}
