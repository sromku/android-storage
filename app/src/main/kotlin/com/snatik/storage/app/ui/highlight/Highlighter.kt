package com.snatik.storage.app.ui.highlight

/** What a token is, mapped to a colour by the Compose layer. */
enum class TokenType { PLAIN, KEYWORD, STRING, COMMENT, NUMBER, PUNCTUATION, TAG, ATTRIBUTE, ANNOTATION, KEY, LITERAL }

enum class HlLanguage { KOTLIN, JAVA, XML, JSON, SQL, SHELL, PROPERTIES, PLAIN }

data class Token(val start: Int, val end: Int, val type: TokenType)

/**
 * A small, dependency-free syntax scanner. It returns non-overlapping tokens in order over the
 * whole text so a viewer can colour multi-line strings and block comments correctly. Anything not
 * covered by a token is plain text.
 */
object Highlighter {

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
        "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when",
        "while", "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import", "init",
        "param", "property", "receiver", "set", "setparam", "value", "where", "abstract", "actual", "annotation", "companion",
        "const", "crossinline", "data", "enum", "expect", "external", "final", "infix", "inline", "inner", "internal",
        "lateinit", "noinline", "open", "operator", "out", "override", "private", "protected", "public", "reified", "sealed",
        "suspend", "tailrec", "vararg",
    )
    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default",
        "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import",
        "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return",
        "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
        "void", "volatile", "while", "true", "false", "null", "var", "record", "sealed", "yield",
    )
    private val SQL_KEYWORDS = setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create", "table", "index", "view",
        "drop", "alter", "add", "primary", "key", "foreign", "references", "not", "null", "and", "or", "order", "by", "group",
        "having", "limit", "offset", "join", "left", "right", "inner", "outer", "on", "as", "distinct", "count", "sum", "avg",
        "min", "max", "like", "in", "between", "is", "case", "when", "then", "else", "end", "union", "all", "exists", "default",
        "integer", "text", "real", "blob", "autoincrement", "unique", "pragma", "explain",
    )
    private val SHELL_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac", "function", "in", "return", "exit",
        "echo", "cd", "export", "local", "read", "set", "unset", "test",
    )

    fun languageForFile(name: String): HlLanguage = when (name.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> HlLanguage.KOTLIN
        "java" -> HlLanguage.JAVA
        "xml", "html", "htm", "svg" -> HlLanguage.XML
        "json" -> HlLanguage.JSON
        "sql" -> HlLanguage.SQL
        "sh", "bash", "zsh" -> HlLanguage.SHELL
        "properties", "prop", "ini", "conf", "cfg", "env", "toml" -> HlLanguage.PROPERTIES
        else -> HlLanguage.PLAIN
    }

    fun tokenize(text: String, lang: HlLanguage): List<Token> = when (lang) {
        HlLanguage.XML -> xml(text)
        HlLanguage.JSON -> json(text)
        HlLanguage.PROPERTIES -> properties(text)
        HlLanguage.KOTLIN -> code(text, KOTLIN_KEYWORDS, lineComment = "//", annotations = true)
        HlLanguage.JAVA -> code(text, JAVA_KEYWORDS, lineComment = "//", annotations = true)
        HlLanguage.SQL -> code(text, SQL_KEYWORDS, lineComment = "--", annotations = false, caseInsensitive = true)
        HlLanguage.SHELL -> code(text, SHELL_KEYWORDS, lineComment = "#", annotations = false)
        HlLanguage.PLAIN -> emptyList()
    }

    // C-family / SQL / shell scanner
    private fun code(text: String, keywords: Set<String>, lineComment: String, annotations: Boolean, caseInsensitive: Boolean = false): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                text.startsWith("/*", i) -> { val e = text.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }; out += Token(i, e, TokenType.COMMENT); i = e }
                lineComment.isNotEmpty() && text.startsWith(lineComment, i) -> { val e = text.indexOf('\n', i).let { if (it < 0) n else it }; out += Token(i, e, TokenType.COMMENT); i = e }
                c == '"' || c == '\'' -> { val e = endOfString(text, i, c); out += Token(i, e, TokenType.STRING); i = e }
                annotations && c == '@' && i + 1 < n && text[i + 1].isLetter() -> { var e = i + 1; while (e < n && (text[e].isLetterOrDigit() || text[e] == '.')) e++; out += Token(i, e, TokenType.ANNOTATION); i = e }
                c.isDigit() -> { var e = i; while (e < n && (text[e].isLetterOrDigit() || text[e] == '.' || text[e] == 'x' || text[e] == '_')) e++; out += Token(i, e, TokenType.NUMBER); i = e }
                c.isLetter() || c == '_' -> {
                    var e = i; while (e < n && (text[e].isLetterOrDigit() || text[e] == '_')) e++
                    val word = text.substring(i, e)
                    if ((if (caseInsensitive) word.lowercase() else word) in keywords) out += Token(i, e, TokenType.KEYWORD)
                    i = e
                }
                c in "{}()[];,.:=<>+-*/&|!?" -> { out += Token(i, i + 1, TokenType.PUNCTUATION); i++ }
                else -> i++
            }
        }
        return out
    }

    private fun endOfString(text: String, start: Int, quote: Char): Int {
        var i = start + 1
        val n = text.length
        while (i < n) {
            if (text[i] == '\\') { i += 2; continue }
            if (text[i] == quote) return i + 1
            i++
        }
        return n
    }

    private fun xml(text: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        val n = text.length
        while (i < n) {
            when {
                text.startsWith("<!--", i) -> { val e = text.indexOf("-->", i + 4).let { if (it < 0) n else it + 3 }; out += Token(i, e, TokenType.COMMENT); i = e }
                text[i] == '<' -> {
                    var e = i + 1
                    if (e < n && (text[e] == '/' || text[e] == '?' || text[e] == '!')) e++
                    val nameStart = e
                    while (e < n && (text[e].isLetterOrDigit() || text[e] in "._:-")) e++
                    out += Token(i, e.coerceAtLeast(nameStart), TokenType.TAG)
                    // attributes until '>'
                    while (e < n && text[e] != '>') {
                        val ch = text[e]
                        when {
                            ch == '"' || ch == '\'' -> { val se = endOfString(text, e, ch); out += Token(e, se, TokenType.STRING); e = se }
                            ch.isLetter() || ch == '_' -> { val as_ = e; while (e < n && (text[e].isLetterOrDigit() || text[e] in "._:-")) e++; out += Token(as_, e, TokenType.ATTRIBUTE) }
                            else -> e++
                        }
                    }
                    if (e < n) e++ // consume '>'
                    i = e
                }
                else -> i++
            }
        }
        return out
    }

    private fun json(text: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '"' -> {
                    val e = endOfString(text, i, '"')
                    var j = e
                    while (j < n && text[j].isWhitespace()) j++
                    out += Token(i, e, if (j < n && text[j] == ':') TokenType.KEY else TokenType.STRING)
                    i = e
                }
                c.isDigit() || (c == '-' && i + 1 < n && text[i + 1].isDigit()) -> { var e = i + 1; while (e < n && (text[e].isDigit() || text[e] in ".eE+-")) e++; out += Token(i, e, TokenType.NUMBER); i = e }
                c.isLetter() -> { var e = i; while (e < n && text[e].isLetter()) e++; if (text.substring(i, e) in setOf("true", "false", "null")) out += Token(i, e, TokenType.LITERAL); i = e }
                c in "{}[],:" -> { out += Token(i, i + 1, TokenType.PUNCTUATION); i++ }
                else -> i++
            }
        }
        return out
    }

    private fun properties(text: String): List<Token> {
        val out = ArrayList<Token>()
        var lineStart = 0
        val n = text.length
        while (lineStart <= n) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) n else it }
            val line = text.substring(lineStart, lineEnd)
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            if (trimmed.startsWith("#") || trimmed.startsWith(";")) {
                out += Token(lineStart, lineEnd, TokenType.COMMENT)
            } else {
                val eq = line.indexOfFirst { it == '=' || it == ':' }
                if (eq > 0) {
                    out += Token(lineStart + indent, lineStart + eq, TokenType.KEY)
                    out += Token(lineStart + eq, lineStart + eq + 1, TokenType.PUNCTUATION)
                    if (eq + 1 < line.length) out += Token(lineStart + eq + 1, lineEnd, TokenType.STRING)
                }
            }
            if (lineEnd == n) break
            lineStart = lineEnd + 1
        }
        return out
    }
}
