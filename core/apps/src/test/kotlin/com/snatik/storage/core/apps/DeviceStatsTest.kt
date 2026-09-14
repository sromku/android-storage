package com.snatik.storage.core.apps

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceStatsTest {

    @Test
    fun parsesAppOpsTimeline() {
        val text = """
            AppOps Uid Op State
              Uid 1000:
                Package com.android.dynsystem:
                  CAMERA (allow):
                      Access: [pers-s] 2026-09-02 17:04:18.663 (-5d21h52m11s12ms)
                Package android:
                  FINE_LOCATION (allow):
                      Access: [pers-s] 2026-09-08 14:35:06.984 (-21m22s691ms)
                      Access: [pers-s] 2026-09-07 11:27:43.539 (-1d3h28m46s136ms)
                  WRITE_SETTINGS (allow):
                      Access: [pers-s] 2026-09-08 14:53:14.297 (-3m15s378ms)
                Package com.example.tracker:
                  RECORD_AUDIO (allow):
                      Access: [bg-s] 2026-09-08 14:50:00.000 (-6m0s0ms) duration=+1m30s0ms
                  CAMERA (allow):
                      Access: [top-s] 2026-09-08 14:52:00.000 (-4m0s0ms)
        """.trimIndent()
        val entries = AppOpsTimeline.parse(text)
        assertEquals(6, entries.size)
        val cam = entries.first { it.op == "CAMERA" && it.packageName == "com.android.dynsystem" }
        assertEquals("com.android.dynsystem", cam.packageName)
        assertTrue(cam.sensitive)
        assertEquals(AppOpState.PERSISTENT, cam.state)
        val loc = entries.filter { it.op == "FINE_LOCATION" }
        assertEquals(2, loc.size)
        assertEquals("android", loc[0].packageName)
        assertTrue(entries.first { it.op == "WRITE_SETTINGS" }.let { !it.sensitive })
        // Background access with duration is flagged and measured.
        val mic = entries.first { it.op == "RECORD_AUDIO" }
        assertEquals(AppOpState.BACKGROUND, mic.state)
        assertTrue(mic.background)
        assertEquals(90_000L, mic.durationMs)
        // Foreground (top) access is not flagged as background.
        val trackerCam = entries.first { it.op == "CAMERA" && it.packageName == "com.example.tracker" }
        assertEquals(AppOpState.FOREGROUND, trackerCam.state)
        assertTrue(!trackerCam.background)
    }

    @Test
    fun parsesWakelocks() {
        val text = """
              Total partial wakelock time: 53s 824ms
              All kernel wake locks:
              Kernel Wake lock PowerManagerService.WakeLocks: 54s 143ms (46 times) realtime
              Kernel Wake lock wlan_rx_wake: 14s 281ms (110 times) realtime

              Statistics since last charge:
        """.trimIndent()
        val wl = DeviceStatsRepository.parseWakelocks(text)
        assertEquals(2, wl.size)
        assertEquals("PowerManagerService.WakeLocks", wl[0].name)
        assertEquals(46, wl[0].count)
        assertTrue(wl[0].heldMs > wl[1].heldMs)
    }

    @Test
    fun parsesZram() {
        val (total, used) = DeviceStatsRepository.zram("4096000 1200000 1310720 0 1310720 0 0\n---\n4096000")
        assertEquals(4096000L, total)
        assertEquals(1310720L, used)
    }
}
