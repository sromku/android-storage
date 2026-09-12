package com.snatik.storage.app.feature.data

import kotlin.test.assertEquals
import org.junit.Test

class UriDiscoveryTest {

    private fun extract(code: String, authority: String = "com.example") =
        UriDiscovery.extractPaths(code, authority)

    @Test
    fun extractsLiteralAuthorityAndPath() {
        val code = """uriMatcher.addURI("com.example", "users", 1);"""
        assertEquals(listOf("users"), extract(code))
    }

    @Test
    fun takesPathWhenAuthorityIsAnObfuscatedConstant() {
        // Authority referenced via a (renamed) constant, path is a literal — the common obfuscated case.
        val code = """a.addURI(b.f2417a, "messages/#", 2);"""
        assertEquals(listOf("messages/#"), extract(code))
    }

    @Test
    fun keepsWildcardAndIdPatterns() {
        val code = """
            m.addURI("com.example", "users", 1);
            m.addURI("com.example", "users/#", 2);
            m.addURI("com.example", "files/*", 3);
        """.trimIndent()
        assertEquals(listOf("users", "users/#", "files/*"), extract(code))
    }

    @Test
    fun stripsLeadingSlashAndSkipsAuthorityOnlyCalls() {
        val code = """
            m.addURI("com.example", "/photos", 1);
            m.addURI("com.example", CONST_PATH, 2);
        """.trimIndent()
        // leading slash stripped; the constant-path call yields nothing (no literal to read).
        assertEquals(listOf("photos"), extract(code))
    }

    @Test
    fun ignoresCommasInsideStringArgs() {
        val code = """x.addURI("com.example", "a,b/c", 7);"""
        assertEquals(listOf("a,b/c"), extract(code))
    }

    @Test
    fun identifierHintKeepsColumnLikeLiteralsAndDropsNoise() {
        // column / key names — kept
        listOf("batteryLevel", "packageName", "raw_contacts", "_id", "consumeType").forEach {
            assert(UriDiscovery.looksLikeIdentifier(it)) { "$it should be a hint" }
        }
        // noise — dropped
        listOf(
            "contacts/#",                       // a path, not a bareword
            "com.android.contacts",             // dotted (package/authority)
            "unknown URI:",                     // message with punctuation/space
            "vnd.android.cursor.dir/contact",   // MIME
            "SUGGESTION",                       // all-caps constant (no lowercase)
            "addURI",                           // reserved
            "x",                                // too short
        ).forEach {
            assert(!UriDiscovery.looksLikeIdentifier(it)) { "$it should not be a hint" }
        }
    }
}
