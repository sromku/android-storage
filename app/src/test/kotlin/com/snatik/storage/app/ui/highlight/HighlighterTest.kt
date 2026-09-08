package com.snatik.storage.app.ui.highlight

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HighlighterTest {

    private fun typesAt(text: String, lang: HlLanguage) = Highlighter.tokenize(text, lang).map { text.substring(it.start, it.end) to it.type }

    @Test
    fun languageByExtension() {
        assertEquals(HlLanguage.KOTLIN, Highlighter.languageForFile("Main.kt"))
        assertEquals(HlLanguage.JSON, Highlighter.languageForFile("config.JSON"))
        assertEquals(HlLanguage.XML, Highlighter.languageForFile("AndroidManifest.xml"))
        assertEquals(HlLanguage.PLAIN, Highlighter.languageForFile("README"))
    }

    @Test
    fun kotlinKeywordsStringsCommentsAnnotations() {
        val t = typesAt("""@Test fun x() { val s = "hi" // note""", HlLanguage.KOTLIN)
        assertTrue(t.contains("@Test" to TokenType.ANNOTATION))
        assertTrue(t.contains("fun" to TokenType.KEYWORD))
        assertTrue(t.contains("val" to TokenType.KEYWORD))
        assertTrue(t.contains("\"hi\"" to TokenType.STRING))
        assertTrue(t.any { it.second == TokenType.COMMENT && it.first.contains("note") })
    }

    @Test
    fun blockCommentSpansLines() {
        val text = "a\n/* c1\nc2 */ b"
        val comment = Highlighter.tokenize(text, HlLanguage.KOTLIN).single { it.type == TokenType.COMMENT }
        assertEquals("/* c1\nc2 */", text.substring(comment.start, comment.end))
    }

    @Test
    fun jsonKeysVsStrings() {
        val t = typesAt("""{"name": "Roman", "n": 42, "ok": true}""", HlLanguage.JSON)
        assertTrue(t.contains("\"name\"" to TokenType.KEY))
        assertTrue(t.contains("\"Roman\"" to TokenType.STRING))
        assertTrue(t.contains("42" to TokenType.NUMBER))
        assertTrue(t.contains("true" to TokenType.LITERAL))
    }

    @Test
    fun xmlTagsAttributesStrings() {
        val t = typesAt("""<a href="x">t</a><!-- c -->""", HlLanguage.XML)
        assertTrue(t.any { it.second == TokenType.TAG && it.first.contains("a") })
        assertTrue(t.contains("href" to TokenType.ATTRIBUTE))
        assertTrue(t.contains("\"x\"" to TokenType.STRING))
        assertTrue(t.any { it.second == TokenType.COMMENT })
    }

    @Test
    fun tokensAreOrderedAndNonOverlapping() {
        val text = """fun f() { "s" /* c */ 1 }"""
        val tokens = Highlighter.tokenize(text, HlLanguage.KOTLIN)
        var last = 0
        for (tk in tokens) { assertTrue(tk.start >= last); assertTrue(tk.end > tk.start); last = tk.end }
    }

    @Test
    fun propertiesKeyValueAndComment() {
        val t = typesAt("# c\nkey = value", HlLanguage.PROPERTIES)
        assertTrue(t.any { it.second == TokenType.COMMENT })
        assertTrue(t.contains("key " to TokenType.KEY))
    }
}
