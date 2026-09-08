package com.snatik.storage.core.intents

import org.junit.Test
import kotlin.test.assertEquals

class BroadcastHistoryTest {

    @Test
    fun parsesDumpsysHistory() {
        val dump = """
            ACTIVITY MANAGER BROADCAST STATE (dumpsys activity broadcasts)
              Historical broadcasts [foreground]:
              Historical Broadcast foreground #0:
                BroadcastRecord{5d1f3c u0 android.intent.action.SCREEN_ON} to user 0
                Intent { act=android.intent.action.SCREEN_ON flg=0x50200010 }
                caller=null android.os.BinderProxy@2 pid=1782 uid=1000
                enqueueClockTime=2026-09-08 03:40:11.123 dispatchClockTime=2026-09-08 03:40:11.130
              Historical Broadcast foreground #1:
                Intent { act=android.intent.action.USER_PRESENT flg=0x24000010 }
                enqueueClockTime=2026-09-08 03:40:12.000 dispatchClockTime=2026-09-08 03:40:12.001
              Historical broadcasts [background]:
              Historical Broadcast background #0:
                Intent { act=android.intent.action.TIME_TICK flg=0x50000014 (has extras) }
                enqueueClockTime=2026-09-08 03:41:00.002 dispatchClockTime=2026-09-08 03:41:00.004
        """.trimIndent()
        val history = BroadcastHistory.parse(dump)
        assertEquals(3, history.size)
        assertEquals("foreground", history[0].queue)
        assertEquals("android.intent.action.SCREEN_ON", history[0].action)
        assertEquals("2026-09-08 03:40:11.123", history[0].enqueued)
        assertEquals("background", history[2].queue)
        assertEquals("android.intent.action.TIME_TICK", history[2].action)
    }
}
