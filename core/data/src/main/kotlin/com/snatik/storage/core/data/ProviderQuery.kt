package com.snatik.storage.core.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import com.snatik.storage.core.shell.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QueryRequest(
    val uri: String,
    val projection: List<String> = emptyList(),
    val selection: String? = null,
    val selectionArgs: List<String> = emptyList(),
    val sortOrder: String? = null,
    val limit: Int = 200,
    val offset: Int = 0,
)

/**
 * Queries content providers as this app, and falls back to `content query` through the shell
 * when a provider refuses us but would accept the shell user.
 */
class ProviderQuery(private val context: Context, private val privilege: PrivilegeManager) {

    class QueryException(message: String, val permissionDenied: Boolean) : Exception(message)

    suspend fun query(request: QueryRequest): Tabular = withContext(Dispatchers.IO) {
        try {
            queryAsApp(request)
        } catch (e: SecurityException) {
            val shell = privilege.executor.value ?: throw QueryException(e.message ?: "Permission denied", permissionDenied = true)
            queryViaShell(request, shell)
        } catch (e: QueryException) {
            throw e
        } catch (e: Exception) {
            throw QueryException(e.message ?: e.toString(), permissionDenied = false)
        }
    }

    suspend fun type(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.getType(Uri.parse(uri)) }.getOrNull()
    }

    suspend fun delete(uri: String, where: String?, args: List<String>): Int = withContext(Dispatchers.IO) {
        context.contentResolver.delete(Uri.parse(uri), where, args.takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    suspend fun update(uri: String, values: Map<String, String?>, where: String?, args: List<String>): Int = withContext(Dispatchers.IO) {
        context.contentResolver.update(Uri.parse(uri), contentValues(values), where, args.takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    suspend fun insert(uri: String, values: Map<String, String?>): String? = withContext(Dispatchers.IO) {
        context.contentResolver.insert(Uri.parse(uri), contentValues(values))?.toString()
    }

    private fun contentValues(values: Map<String, String?>) = ContentValues().apply {
        for ((k, v) in values) if (v == null) putNull(k) else put(k, v)
    }

    private fun queryAsApp(request: QueryRequest): Tabular {
        val args = Bundle().apply {
            request.selection?.let { putString(ContentResolver.QUERY_ARG_SQL_SELECTION, it) }
            if (request.selectionArgs.isNotEmpty()) putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, request.selectionArgs.toTypedArray())
            request.sortOrder?.let { putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, it) }
            putInt(ContentResolver.QUERY_ARG_LIMIT, request.limit + 1)
            putInt(ContentResolver.QUERY_ARG_OFFSET, request.offset)
        }
        val projection = request.projection.takeIf { it.isNotEmpty() }?.toTypedArray()
        val cursor = context.contentResolver.query(Uri.parse(request.uri), projection, args, null)
            ?: throw QueryException("Provider returned no cursor", permissionDenied = false)
        cursor.use { c ->
            val columns = c.columnNames.toList()
            val rows = ArrayList<List<String?>>()
            var honouredLimit = true
            while (c.moveToNext()) {
                rows += columns.indices.map { i -> cell(c, i) }
                if (rows.size > request.limit) {
                    honouredLimit = false
                    break
                }
            }
            // Providers that ignore the limit bundle hand back everything; offset then has to be applied here.
            val extras = c.extras
            val ignoredOffset = request.offset > 0 && !extras.containsKey(ContentResolver.QUERY_ARG_OFFSET) &&
                extras.getStringArray(ContentResolver.EXTRA_HONORED_ARGS)?.contains(ContentResolver.QUERY_ARG_OFFSET) != true
            val page = if (ignoredOffset) rows.drop(request.offset) else rows
            return Tabular(columns, page.take(request.limit), truncated = !honouredLimit || page.size > request.limit)
        }
    }

    private fun cell(c: Cursor, i: Int): String? = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toString()
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).toString()
        Cursor.FIELD_TYPE_BLOB -> "<blob ${c.getBlob(i).size} bytes>"
        else -> c.getString(i)
    }

    private suspend fun queryViaShell(request: QueryRequest, shell: com.snatik.storage.core.shell.ShellExecutor): Tabular {
        val command = buildString {
            append("content query --uri ").append(request.uri.shellQuote())
            if (request.projection.isNotEmpty()) append(" --projection ").append(request.projection.joinToString(":").shellQuote())
            request.selection?.let { append(" --where ").append(bindArgs(it, request.selectionArgs).shellQuote()) }
            request.sortOrder?.let { append(" --sort ").append(it.shellQuote()) }
        }
        val result = shell.run(command, timeoutMs = 2 * 60_000)
        // `content query` reports provider failures (permission denials, unknown/unsupported URIs) on
        // STDERR and still exits 0, so a blocked provider would otherwise look like an empty result.
        // Inspect both streams and surface the failure instead of showing a misleading "No rows".
        val lines = (result.err + "\n" + result.out).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val permLine = lines.firstOrNull { it.contains("Permission Denial") || it.contains("SecurityException") }
        val errLine = permLine ?: lines.firstOrNull {
            it.contains("Error while accessing provider") || it.contains("IllegalArgumentException") || it.contains("Exception")
        }
        if (!result.ok || errLine != null) {
            throw QueryException(errLine ?: "content query failed (${result.exitCode})", permissionDenied = permLine != null)
        }
        val parsed = parseContentOutput(result.out)
        val page = parsed.rows.drop(request.offset)
        return Tabular(parsed.columns, page.take(request.limit), truncated = page.size > request.limit, source = Tabular.Source.SHELL)
    }

    private fun bindArgs(selection: String, args: List<String>): String {
        var out = selection
        for (arg in args) out = out.replaceFirst("?", "'" + arg.replace("'", "''") + "'")
        return out
    }

    companion object {
        /**
         * Parse the `Row: N a=1, b=hello` lines that `content query` prints. Values containing ", "
         * cannot be split unambiguously; the parser only splits at ", " followed by `name=`.
         */
        fun parseContentOutput(text: String): Tabular {
            val columns = LinkedHashMap<String, Int>()
            val rows = ArrayList<Map<String, String?>>()
            val pair = Regex("(?:^|, )([A-Za-z_][A-Za-z0-9_.:]*)=")
            for (line in text.lineSequence()) {
                if (!line.startsWith("Row: ")) continue
                val body = line.substringAfter(' ').substringAfter(' ', "")
                val matches = pair.findAll(body).toList()
                val row = LinkedHashMap<String, String?>()
                for ((index, m) in matches.withIndex()) {
                    val name = m.groupValues[1]
                    val start = m.range.last + 1
                    val end = if (index + 1 < matches.size) matches[index + 1].range.first else body.length
                    val raw = body.substring(start, end)
                    row[name] = if (raw == "NULL") null else raw
                    columns.getOrPut(name) { columns.size }
                }
                rows += row
            }
            val names = columns.keys.toList()
            return Tabular(names, rows.map { r -> names.map { r[it] } }, source = Tabular.Source.SHELL)
        }
    }
}
