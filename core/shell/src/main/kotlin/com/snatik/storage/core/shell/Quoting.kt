package com.snatik.storage.core.shell

/** Single-quote for POSIX sh so the value survives any character. */
fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

fun shellJoin(vararg parts: String): String = parts.joinToString(" ") { it.shellQuote() }
