package com.snatik.storage

import org.junit.Test
import kotlin.test.assertEquals

class SizeUnitTest {

    @Test
    fun readableSizes() {
        assertEquals("0 B", 0L.toReadableSize())
        assertEquals("512 B", 512L.toReadableSize())
        assertEquals("1.0 KB", 1024L.toReadableSize())
        assertEquals("1.5 KB", 1536L.toReadableSize())
        assertEquals("20.3 MB", (20.3 * 1024 * 1024).toLong().toReadableSize())
        assertEquals("2.0 GB", (2L shl 30).toReadableSize())
        assertEquals("1.0 TB", (1L shl 40).toReadableSize())
    }

    @Test
    fun convert() {
        assertEquals(2.0, SizeUnit.MB.convert(2L shl 20))
        assertEquals(0.5, SizeUnit.KB.convert(512))
    }
}
