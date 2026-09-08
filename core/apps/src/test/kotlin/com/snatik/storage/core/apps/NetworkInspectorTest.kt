package com.snatik.storage.core.apps

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkInspectorTest {

    @Test
    fun decodesLittleEndianIpv4() {
        // 2404A8C0 -> 192.168.4.36, port A5B0 -> 42416
        assertEquals("192.168.4.36", NetworkInspector.hexToIp("2404A8C0", ipv6 = false))
        assertEquals(0xA5B0, "A5B0".toInt(16))
    }

    @Test
    fun mapsV4MappedV6ToDotted() {
        // ::ffff:63.86.117.31 style stored as ...FFFF then v4 bytes
        // v4-mapped v6 as /proc stores it: word2 = FFFF0000, word3 = v4 little-endian per word
        val mapped = "00000000" + "00000000" + "FFFF0000" + "1F758641"
        val ip = NetworkInspector.hexToIp(mapped, ipv6 = true)
        assertEquals("65.134.117.31", ip)
    }

    @Test
    fun compressesZeroGroups() {
        // 2001:4860:4845:0400:0:0:0:0 stored little-endian per word
        val hex = "60480120" + "00044548" + "00000000" + "00000000"
        assertEquals("2001:4860:4845:400::", NetworkInspector.hexToIp(hex, ipv6 = true))
    }

    @Test
    fun parsesProcNetLinesWithUid() {
        val text = """
              sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
               0: 2404A8C0:A5B0 1744753F:01BB 08 00000000:00000000 00:00000000 00000000 10194        0 5451206 1
               1: 2404A8C0:C848 133ACE41:01BB 01 00000000:00000000 00:00000000 00000000 10155        0 5440325 1
        """.trimIndent()
        val conns = NetworkInspector.parseProcNet(text)
        assertEquals(2, conns.size)
        assertEquals("192.168.4.36", conns[0].localAddress)
        assertEquals(443, conns[0].remotePort)
        assertEquals(10194, conns[0].uid)
        assertEquals("CLOSE_WAIT", conns[0].state)
        assertEquals("ESTABLISHED", conns[1].state)
        assertEquals(10155, conns[1].uid)
    }

    @Test
    fun sumsNetstatsPerUid() {
        val text = "{uid=10155,package=com.google.android.gms}=11598\n{uid=10155,package=com.google.android.gms}=200\n{uid=1000,package=android}=3436"
        val usage = NetworkInspector.parseNetstats(text)
        assertEquals(11798L, usage[10155]!!.first)
        assertTrue(usage.containsKey(1000))
    }
}
