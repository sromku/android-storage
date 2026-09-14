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

    @Test
    fun parsesModernPerProcessQueue() {
        // The modern queue has no [foreground]/[background] header; each record is a BroadcastRecord
        // block, foreground/background comes from FLAG_RECEIVER_FOREGROUND, and later reason: lines
        // that also mention Intent {…} must not be counted as extra broadcasts.
        val dump = """
            ACTIVITY MANAGER BROADCAST STATE (dumpsys activity broadcasts)
                Pending  broadcast #113:
                    BroadcastRecord{5f48b89 android.intent.action.PACKAGE_REPLACED/u0/0x1081} to user 0
                    Intent { act=android.intent.action.PACKAGE_REPLACED dat=package:com.snatik.storage.app flg=0x4000010 (has extras) }
                        extras: Bundle[{android.intent.extra.UID=10129}]
                    enqueueClockTime=2026-09-13 19:56:40.932 dispatchClockTime=1969-12-31 19:00:00.000
                    DELIVERED scheduled +3ms terminal 0 (-1) #0: BroadcastFilter{31ce594}
                        reason: skipped by policy: Skipping delivery of Intent { act=android.intent.action.PACKAGE_REPLACED flg=0x4000010 } to ProcessRecord{abc}
                        queue idx: 0
                    Pending  broadcast #112:
                    BroadcastRecord{19e2356 android.intent.action.HEADSET_PLUG/u-1/0x1481} to user -1
                    Intent { act=android.intent.action.HEADSET_PLUG flg=0x50000010 (has extras) }
                    enqueueClockTime=2026-09-13 19:55:10.100 dispatchClockTime=1969-12-31 19:00:00.000
        """.trimIndent()
        val history = BroadcastHistory.parse(dump)
        assertEquals(2, history.size)
        assertEquals("android.intent.action.PACKAGE_REPLACED", history[0].action)
        assertEquals("background", history[0].queue) // flg=0x4000010, no FLAG_RECEIVER_FOREGROUND
        assertEquals("2026-09-13 19:56:40.932", history[0].enqueued)
        assertEquals("android.intent.action.HEADSET_PLUG", history[1].action)
        assertEquals("foreground", history[1].queue) // flg=0x50000010 has FLAG_RECEIVER_FOREGROUND
    }
}
