package com.snatik.storage.core.apps

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppWatchTest {

    @Test
    fun parsesRelativeTimes() {
        assertEquals(1_792L, AppWatchRepository.parseRelativeMs("1s792ms"))
        assertEquals((1L * 3600 + 55 * 60 + 35) * 1000 + 972, AppWatchRepository.parseRelativeMs("1h55m35s972ms"))
        assertEquals(27L * 86400_000 + 16 * 3600_000 + 58 * 60_000 + 19_000 + 930, AppWatchRepository.parseRelativeMs("27d16h58m19s930ms"))
        assertNull(AppWatchRepository.parseRelativeMs("nonsense"))
    }

    @Test
    fun parsesAppOpsWithAndWithoutAccess() {
        val text = """
            Uid mode: COARSE_LOCATION: ignore
            FINE_LOCATION: allow; time=+1s792ms ago
            WIFI_SCAN: allow; time=+1s801ms ago
            SYSTEM_ALERT_WINDOW: default; rejectTime=+27d16h58m19s930ms ago
            MONITOR_LOCATION: allow; time=+12h22m36s190ms ago (running)
            CAMERA: foreground
        """.trimIndent()
        val ops = AppWatchRepository.parseAppOps(text).associateBy { it.op }
        assertEquals(OpMode.IGNORE, ops["COARSE_LOCATION"]!!.mode)
        assertTrue(ops["FINE_LOCATION"]!!.everAccessed)
        assertEquals(1792L, ops["FINE_LOCATION"]!!.lastAccessAgoMs)
        assertNull(ops["SYSTEM_ALERT_WINDOW"]!!.lastAccessAgoMs) // rejectTime is not an access
        assertTrue(ops["MONITOR_LOCATION"]!!.running)
        assertEquals(OpMode.FOREGROUND, ops["CAMERA"]!!.mode)
        assertNull(ops["CAMERA"]!!.lastAccessAgoMs)
    }

    @Test
    fun parsesServices() {
        val text = """
              * ServiceRecord{d25f4ed u0 com.x/.nearby.LifeCycleService c:com.x}
                app=ProcessRecord{3f91158 31453:com.x.persistent/u0a155}
                lastActivity=-13h47m57s288ms restartTime=-13h47m57s288ms createdFromFg=false
              * ServiceRecord{93cbf02 u0 com.x/.PersistentApiService c:com.x}
                app=ProcessRecord{3f91158 31453:com.x.persistent/u0a155}
                lastActivity=-13h6m2s210ms createdFromFg=true
        """.trimIndent()
        val services = AppWatchRepository.parseServices(text)
        assertEquals(2, services.size)
        assertEquals(".nearby.LifeCycleService", services[0].name)
        assertEquals("com.x.persistent", services[0].process)
        assertEquals(31453, services[0].pid)
        assertTrue(!services[0].foreground)
        assertTrue(services[1].foreground)
    }

    @Test
    fun parsesProcessesForPackage() {
        val text = " 8913 257624 com.google.android.gms\n27347 297472 com.google.android.gms.ui\n999 100 com.other.app"
        val procs = AppWatchRepository.parseProcesses(text, "com.google.android.gms")
        assertEquals(2, procs.size)
        assertEquals(257624L, procs[0].rssKb)
        assertTrue(procs.none { it.name == "com.other.app" })
    }

    @Test
    fun scoresSuspiciousBehavior() {
        val ops = AppWatchRepository.parseAppOps(
            """
            FINE_LOCATION: allow; time=+1s ago
            BLUETOOTH_SCAN: allow; time=+37m14s700ms ago
            WIFI_SCAN: allow; time=+2s ago
            READ_CONTACTS: ignore
            """.trimIndent(),
        )
        val signals = AppWatchRepository.signalsFrom(ops, emptyList())
        assertTrue(signals.any { it.title.contains("Bluetooth") })
        assertTrue(signals.any { it.title.contains("Wi-Fi") })
        assertTrue(signals.none { it.title.contains("contacts") }) // ignored op, not a signal
        assertTrue(AppWatchRepository.score(signals) > 0)
    }
}
