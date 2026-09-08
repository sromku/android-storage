package com.snatik.storage.core.data

/** A rectangular result: column names and rows of display strings. Nulls are SQL NULL. */
data class Tabular(
    val columns: List<String>,
    val rows: List<List<String?>>,
    /** More rows exist beyond what was fetched. */
    val truncated: Boolean = false,
    /** Where the rows came from, for the UI to explain. */
    val source: Source = Source.APP,
) {
    enum class Source { APP, SHELL, SQLITE }

    val isEmpty: Boolean get() = rows.isEmpty()

    fun toCsv(): String = buildString {
        appendLine(columns.joinToString(",") { csv(it) })
        for (row in rows) appendLine(row.joinToString(",") { csv(it ?: "") })
    }

    fun toJson(): String = buildString {
        append('[')
        rows.forEachIndexed { i, row ->
            if (i > 0) append(',')
            append('{')
            columns.forEachIndexed { c, name ->
                if (c > 0) append(',')
                append('"').append(json(name)).append("\":")
                val v = row.getOrNull(c)
                if (v == null) append("null") else append('"').append(json(v)).append('"')
            }
            append('}')
        }
        append(']')
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun json(value: String): String = buildString {
        for (c in value) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}
