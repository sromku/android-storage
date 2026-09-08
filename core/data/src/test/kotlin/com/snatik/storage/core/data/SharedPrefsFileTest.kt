package com.snatik.storage.core.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedPrefsFileTest {

    private val sample = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="name">Roman &amp; co</string>
    <int name="count" value="42" />
    <boolean name="enabled" value="true" />
    <long name="ts" value="1700000000000" />
    <float name="ratio" value="0.5" />
    <set name="tags">
        <string>a</string>
        <string>b &lt; c</string>
    </set>
</map>
"""

    @Test
    fun parsesEveryType() {
        val entries = SharedPrefsFile.parse(sample)
        assertEquals(6, entries.size)
        assertEquals(PrefEntry("name", PrefType.STRING, "Roman & co"), entries[0])
        assertEquals(PrefEntry("count", PrefType.INT, "42"), entries[1])
        assertEquals(PrefEntry("enabled", PrefType.BOOLEAN, "true"), entries[2])
        assertEquals(PrefEntry("ts", PrefType.LONG, "1700000000000"), entries[3])
        assertEquals(PrefEntry("ratio", PrefType.FLOAT, "0.5"), entries[4])
        assertEquals(PrefEntry("tags", PrefType.SET, "", listOf("a", "b < c")), entries[5])
    }

    @Test
    fun roundTrips() {
        val entries = SharedPrefsFile.parse(sample)
        val again = SharedPrefsFile.parse(SharedPrefsFile.serialize(entries))
        assertEquals(entries, again)
    }

    @Test
    fun validates() {
        assertNull(SharedPrefsFile.validate(PrefType.INT, "5"))
        assertNotNull(SharedPrefsFile.validate(PrefType.INT, "x"))
        assertNotNull(SharedPrefsFile.validate(PrefType.BOOLEAN, "yes"))
        assertNull(SharedPrefsFile.validate(PrefType.STRING, "anything"))
    }
}
