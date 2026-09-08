package com.snatik.storage.core.apps

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelemetryForecastTest {
    private val day = 24L * 3600 * 1000

    @Test fun noForecastFromOnePoint() {
        assertNull(TelemetryRepository.forecast(listOf(dev(0, 100))))
    }

    @Test fun projectsExhaustionWhenFilling() {
        // Free space drops 10 GB/day from 100 GB → full in ~10 days from the last point.
        val gb = 1_000_000_000L
        val series = (0..4).map { dev(it * day, (100 - it * 10) * gb) }
        val f = TelemetryRepository.forecast(series)!!
        assertNotNull(f.daysUntilFull)
        assertTrue(f.bytesPerDay > 0, "losing free space => positive bytesPerDay")
        // last point is at day 4 with 60 GB left, losing 10 GB/day => ~6 days
        assertTrue(f.daysUntilFull!! in 5.0..7.0, "got ${f.daysUntilFull}")
        assertTrue(f.confident)
    }

    @Test fun noExhaustionWhenFreeing() {
        val gb = 1_000_000_000L
        val series = (0..3).map { dev(it * day, (50 + it * 5) * gb) }
        val f = TelemetryRepository.forecast(series)!!
        assertNull(f.daysUntilFull)
    }

    private fun dev(ts: Long, free: Long) = DeviceTelemetry(ts = ts, freeBytes = free, totalBytes = 128_000_000_000L, ramFreeBytes = 0, ramTotalBytes = 0)
}
