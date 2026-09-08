package com.snatik.storage.core.apps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemInspectorTest {

    @Test fun parsesMounts() {
        val text = """
            /dev/block/dm-0 / ext4 ro,seclabel,relatime 0 0
            tmpfs /dev/shm tmpfs rw,seclabel,nosuid 0 0
        """.trimIndent()
        val mounts = SystemInspector.parseMounts(text)
        assertEquals(2, mounts.size)
        assertEquals("/", mounts[0].mountPoint)
        assertEquals("ext4", mounts[0].type)
        assertTrue(mounts[0].options.contains("ro"))
    }

    @Test fun parsesPartitionsToBytesAndSorts() {
        val text = """
            major minor  #blocks  name
             253        0    1024 dm-0
             253        1     512 dm-1
        """.trimIndent()
        val parts = SystemInspector.parsePartitions(text)
        assertEquals("dm-0", parts[0].name)
        assertEquals(1024L * 1024, parts[0].bytes)
        assertTrue(parts[0].bytes > parts[1].bytes)
    }

    @Test fun parsesSwaps() {
        val text = """
            Filename				Type		Size	Used	Priority
            /dev/block/zram0                        partition	2097148	100	-2
        """.trimIndent()
        val swaps = SystemInspector.parseSwaps(text)
        assertEquals(1, swaps.size)
        assertEquals("partition", swaps[0].type)
        assertEquals(2097148L, swaps[0].sizeKb)
        assertEquals(100L, swaps[0].usedKb)
    }

    @Test fun rollsUpSmaps() {
        val text = """
            7f8a2b4000-7f8a2b6000 r-xp 00000000 fd:00 1234 /system/lib64/libc.so
            Rss:                   8 kB
            Pss:                   4 kB
            Private_Dirty:         2 kB
            Swap:                  1 kB
            7f8a2c0000-7f8a2c2000 rw-p 00000000 00:00 0
            Rss:                  16 kB
            Pss:                  16 kB
            Private_Dirty:        16 kB
            Swap:                  0 kB
        """.trimIndent()
        val r = SystemInspector.parseSmaps(text)!!
        assertEquals(2, r.regions)
        assertEquals(24, r.rssKb)
        assertEquals(20, r.pssKb)
        assertEquals(18, r.privateDirtyKb)
        assertEquals(1, r.swapKb)
    }

    @Test fun entropyOfUniformIsHigh() {
        val bytes = ByteArray(256) { it.toByte() }
        assertEquals(8.0, ElfInspector.entropyOf(bytes), 0.01)
        val zeros = ByteArray(256)
        assertEquals(0.0, ElfInspector.entropyOf(zeros), 0.01)
    }
}
