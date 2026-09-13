package com.snatik.storage.core.intents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentMonitorParserTest {

    @Test
    fun parsesViewToChrome() {
        val line = "09-13 12:31:50.093  1809 11864 I ActivityTaskManager: START u0 {act=android.intent.action.VIEW dat=https://example.com/... flg=0x10000000 xflg=0x4 cmp=com.android.chrome/com.google.android.apps.chrome.IntentDispatcher} with LAUNCH_MULTIPLE from uid 2000 (com.android.shell) (BAL_ALLOW_PERMISSION) result code=0"
        val i = IntentMonitorParser.parse(line, 1, 100)!!
        assertEquals("android.intent.action.VIEW", i.action)
        assertEquals("https://example.com/...", i.data)
        assertEquals("com.android.chrome", i.packageName)
        assertEquals("com.google.android.apps.chrome.IntentDispatcher", i.className)
        assertEquals(0x10000000, i.flags)
        assertEquals(2000, i.callerUid)
        assertEquals("com.android.shell", i.callerPackage)
        assertEquals(false, i.hasExtras)
    }

    @Test
    fun parsesSendWithTypeAndExtras() {
        val line = "I ActivityTaskManager: START u0 {act=android.intent.action.SEND typ=text/plain flg=0x10000000 xflg=0x4 cmp=com.snatik.storage.app/.feature.intents.IntentSinkActivity clip={text/plain {T(25)}} (has extras)} with LAUNCH_MULTIPLE from uid 10129 (com.snatik.storage.app) (sr=23484685) result code=0"
        val i = IntentMonitorParser.parse(line, 2, 200)!!
        assertEquals("android.intent.action.SEND", i.action)
        assertEquals("text/plain", i.type)
        assertEquals("com.snatik.storage.app", i.packageName)
        assertEquals("com.snatik.storage.app.feature.intents.IntentSinkActivity", i.className)
        assertTrue(i.hasExtras)
        assertEquals("com.snatik.storage.app", i.callerPackage)
    }

    @Test
    fun parsesMainWithCategory() {
        val line = "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x8000 xflg=0x4 cmp=com.android.settings/.Settings} with LAUNCH_MULTIPLE from uid 10258 (com.android.systemui) result code=0"
        val i = IntentMonitorParser.parse(line, 3, 300)!!
        assertEquals(listOf("android.intent.category.LAUNCHER"), i.categories)
        assertEquals("com.android.settings", i.packageName)
        assertEquals("com.android.settings.Settings", i.className)
    }

    @Test
    fun ignoresNonStartLines() {
        assertNull(IntentMonitorParser.parse("some unrelated logcat line", 4, 400))
    }
}
