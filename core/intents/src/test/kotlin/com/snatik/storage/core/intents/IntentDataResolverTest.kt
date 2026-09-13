package com.snatik.storage.core.intents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentDataResolverTest {

    private val resolver = IntentDataResolver()

    private fun target(data: String?, pkg: String? = "com.android.chrome", action: String? = "android.intent.action.VIEW") =
        MonitoredIntent(1, 0, action, data, null, emptyList(), 0, pkg, null, null, null, false)

    @Test
    fun resolvesSingleConfidentMatch() {
        val dump = """
            ACTIVITY com.android.chrome/...ChromeTabbedActivity
              Intent { act=android.intent.action.VIEW dat=https://example.com/very/long/path?q=1 flg=0x1 cmp=com.android.chrome/org.chromium.chrome.browser.ChromeTabbedActivity }
        """.trimIndent()
        val r = resolver.match(dump, target("https://example.com/..."))
        assertEquals(FullDataResult.Resolved("https://example.com/very/long/path?q=1"), r)
    }

    @Test
    fun declinesWhenTwoDifferentUrisMatch() {
        val dump = """
            Intent { act=android.intent.action.VIEW dat=https://example.com/pageA cmp=com.android.chrome/a.B }
            Intent { act=android.intent.action.VIEW dat=https://example.com/pageB cmp=com.android.chrome/a.C }
        """.trimIndent()
        assertEquals(FullDataResult.Ambiguous, resolver.match(dump, target("https://example.com/...")))
    }

    @Test
    fun notFoundWhenNoRecordMatches() {
        val dump = "Intent { act=android.intent.action.VIEW dat=https://other.com/x cmp=com.android.chrome/a.B }"
        assertEquals(FullDataResult.NotFound, resolver.match(dump, target("https://example.com/...")))
    }

    @Test
    fun doesNotMatchLookalikeHost() {
        val dump = "Intent { act=android.intent.action.VIEW dat=https://example.com.evil.com/x cmp=com.android.chrome/a.B }"
        assertEquals(FullDataResult.NotFound, resolver.match(dump, target("https://example.com/...")))
    }

    @Test
    fun differentPackageIsNotamatch() {
        val dump = "Intent { act=android.intent.action.VIEW dat=https://example.com/p cmp=com.other.app/a.B }"
        assertEquals(FullDataResult.NotFound, resolver.match(dump, target("https://example.com/...")))
    }

    @Test
    fun passesThroughWhenNotTruncated() {
        val r = resolver.match("", target("https://example.com/full"))
        assertTrue(r is FullDataResult.Resolved && r.data == "https://example.com/full")
    }
}
