package com.snatik.storage.core.apps

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream

/**
 * Offline IP -> organisation lookup, backed by the packed `ip2asn.db` asset (public-domain
 * iptoasn.com data). No network: turns a connection's remote IP into the network that owns it
 * (e.g. "GOOGLE", "CLOUDFLARENET", "ALIBABA", "OVH SAS"). Loaded lazily on first use and cached.
 *
 * The packed table stores only range starts (the source is contiguous — gaps are "Not routed"
 * rows), so a lookup is the last start <= ip via unsigned binary search.
 */
class AsnDb(private val context: Context) {

    @Volatile private var loaded = false
    private lateinit var names: Array<String>
    private lateinit var v4Start: IntArray
    private lateinit var v4Idx: IntArray
    private lateinit var v6Hi: LongArray
    private lateinit var v6Lo: LongArray
    private lateinit var v6Idx: IntArray

    /** Resolve many IPs at once, off the main thread. Only IPs with a known owner are returned. */
    suspend fun orgs(ips: List<String>): Map<String, String> = withContext(Dispatchers.Default) {
        ensureLoaded()
        ips.distinct().mapNotNull { ip -> lookup(ip)?.let { ip to it } }.toMap()
    }

    fun lookup(ip: String): String? {
        ensureLoaded()
        val addr = runCatching { InetAddress.getByName(ip).address }.getOrNull() ?: return null
        val idx = when (addr.size) {
            4 -> floorV4(beInt(addr, 0))?.let { v4Idx[it] }
            16 -> floorV6(beLong(addr, 0), beLong(addr, 8))?.let { v6Idx[it] }
            else -> null
        } ?: return null
        return names.getOrNull(idx)?.takeIf { it.isNotEmpty() }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        val raw = GZIPInputStream(context.assets.open(ASSET)).use { input ->
            val out = ByteArrayOutputStream(10 * 1024 * 1024)
            input.copyTo(out, 64 * 1024)
            out.toByteArray()
        }
        val bb = ByteBuffer.wrap(raw) // big-endian by default
        require(bb.int == MAGIC) { "bad asn db" }
        val nameCount = bb.int
        names = Array(nameCount) {
            val len = bb.short.toInt() and 0xFFFF
            val b = ByteArray(len); bb.get(b); String(b, Charsets.UTF_8)
        }
        val v4 = bb.int
        v4Start = IntArray(v4); v4Idx = IntArray(v4)
        for (i in 0 until v4) { v4Start[i] = bb.int; v4Idx[i] = bb.int }
        val v6 = bb.int
        v6Hi = LongArray(v6); v6Lo = LongArray(v6); v6Idx = IntArray(v6)
        for (i in 0 until v6) { v6Hi[i] = bb.long; v6Lo[i] = bb.long; v6Idx[i] = bb.int }
        loaded = true
    }

    private fun floorV4(key: Int): Int? {
        var lo = 0; var hi = v4Start.size - 1; var res = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (Integer.compareUnsigned(v4Start[mid], key) <= 0) { res = mid; lo = mid + 1 } else hi = mid - 1
        }
        return res.takeIf { it >= 0 }
    }

    private fun floorV6(keyHi: Long, keyLo: Long): Int? {
        var lo = 0; var hi = v6Hi.size - 1; var res = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cmpU(v6Hi[mid], v6Lo[mid], keyHi, keyLo) <= 0) { res = mid; lo = mid + 1 } else hi = mid - 1
        }
        return res.takeIf { it >= 0 }
    }

    private fun cmpU(aHi: Long, aLo: Long, bHi: Long, bLo: Long): Int {
        val c = java.lang.Long.compareUnsigned(aHi, bHi)
        return if (c != 0) c else java.lang.Long.compareUnsigned(aLo, bLo)
    }

    private fun beInt(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun beLong(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        return v
    }

    companion object {
        private const val ASSET = "ip2asn.db"
        private val MAGIC = ByteBuffer.wrap("ASN1".toByteArray(Charsets.US_ASCII)).int
    }
}
