package com.snatik.storage.core.apps

import android.content.Context
import android.content.pm.PackageManager
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

enum class Protocol { TCP, UDP }

data class Connection(
    val protocol: Protocol,
    val ipv6: Boolean,
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String,
    val remotePort: Int,
    val state: String,
    val uid: Int,
    val txQueue: Long = 0,
    val rxQueue: Long = 0,
    val retransmits: Long = 0,
    val inode: Long = 0,
)

data class AppConnections(
    val uid: Int,
    val packageName: String,
    val label: String,
    val connections: List<Connection>,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
    val remoteHosts: List<String> get() = connections.map { it.remoteAddress }.filter { it.isNotEmpty() }.distinct()
}

/** One time slice of an app's data usage, as recorded by netstats. */
data class UsageBucket(
    val startMs: Long,
    val durationMs: Long,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

/**
 * Everything netstats knows about one app's data usage, plus any live sockets the shell can see.
 * Bytes are split by direction (rx/tx), transport (wifi/mobile) and app state (foreground/background),
 * and the per-bucket [buckets] drive the usage-over-time chart.
 */
data class AppNetworkUsage(
    val uid: Int,
    val packageName: String,
    val label: String,
    val rxBytes: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val txPackets: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val foregroundBytes: Long,
    val backgroundBytes: Long,
    val firstMs: Long,
    val lastMs: Long,
    val buckets: List<UsageBucket>,
    val connections: List<Connection>,
) {
    val totalBytes: Long get() = rxBytes + txBytes
    val totalPackets: Long get() = rxPackets + txPackets
    val remoteHosts: List<String> get() = connections.map { it.remoteAddress }.filter { it.isNotEmpty() }.distinct()
}

/**
 * Per-app network activity. Two sources, both via the shell (no VPN, no extra root):
 * live sockets from `/proc/net/{tcp,tcp6,udp,udp6}` (only visible with root; a uid-2000 shell sees
 * an empty table), and the durable usage history from `dumpsys netstats detail` — per-uid bytes and
 * packets bucketed by hour, split by transport and foreground/background state.
 */
class NetworkInspector(private val context: Context, private val privilege: PrivilegeManager) {

    private val labelCache = HashMap<Int, Pair<String, String>>()

    /** Legacy live-only view kept for the headless API. */
    suspend fun connections(): List<AppConnections> = withContext(Dispatchers.IO) {
        val shell = privilege.executor.value ?: return@withContext emptyList()
        val raw = shell.run(
            "cat /proc/net/tcp /proc/net/tcp6 /proc/net/udp /proc/net/udp6 2>/dev/null",
            timeoutMs = 15_000,
        ).out
        val conns = parseProcNet(raw).filter { it.remotePort != 0 && !it.remoteAddress.startsWith("127.") && it.remoteAddress != "::" }
        val usage = runCatching { parseNetstats(shell.run("dumpsys netstats detail", timeoutMs = 30_000).out) }.getOrDefault(emptyMap())
        val byUid = conns.groupBy { it.uid }
        val uids = (byUid.keys + usage.keys).toSet()
        uids.mapNotNull { uid ->
            if (uid < 10000) return@mapNotNull null // system/root uids
            val (pkg, label) = labelFor(uid)
            val u = usage[uid] ?: (0L to 0L)
            AppConnections(uid, pkg, label, byUid[uid].orEmpty().sortedBy { it.remoteAddress }, u.first, u.second)
        }.filter { it.connections.isNotEmpty() || it.totalBytes > 0 }
            .sortedByDescending { it.connections.size * 1_000_000L + it.totalBytes }
    }

    /**
     * Full per-app usage history from netstats, folded together with any live sockets. Sorted by
     * total bytes so the busiest apps lead. Never empty as long as any app has ever used the network.
     */
    suspend fun usage(): List<AppNetworkUsage> = withContext(Dispatchers.IO) {
        val shell = privilege.executor.value ?: return@withContext emptyList()
        val detail = runCatching { shell.run("dumpsys netstats detail", timeoutMs = 30_000).out }.getOrDefault("")
        val aggs = parseUsage(detail)
        val liveByUid = runCatching {
            parseProcNet(shell.run("cat /proc/net/tcp /proc/net/tcp6 /proc/net/udp /proc/net/udp6 2>/dev/null", timeoutMs = 15_000).out)
                .filter { it.remotePort != 0 && !it.remoteAddress.startsWith("127.") && it.remoteAddress != "::" }
                .groupBy { it.uid }
        }.getOrDefault(emptyMap())
        val uids = (aggs.keys + liveByUid.keys).toSet()
        uids.mapNotNull { uid ->
            if (uid < 10000) return@mapNotNull null
            val (pkg, label) = labelFor(uid)
            val a = aggs[uid]
            val live = liveByUid[uid].orEmpty().sortedWith(compareByDescending<Connection> { it.state == "ESTABLISHED" }.thenBy { it.remoteAddress })
            if (a == null && live.isEmpty()) return@mapNotNull null
            AppNetworkUsage(
                uid = uid,
                packageName = pkg,
                label = label,
                rxBytes = a?.rxBytes ?: 0, txBytes = a?.txBytes ?: 0,
                rxPackets = a?.rxPackets ?: 0, txPackets = a?.txPackets ?: 0,
                wifiBytes = a?.wifiBytes ?: 0, mobileBytes = a?.mobileBytes ?: 0,
                foregroundBytes = a?.foregroundBytes ?: 0, backgroundBytes = a?.backgroundBytes ?: 0,
                firstMs = a?.firstMs ?: 0, lastMs = a?.lastMs ?: 0,
                buckets = a?.buckets().orEmpty(),
                connections = live,
            )
        }.sortedByDescending { it.totalBytes }
    }

    /** Resolve remote IPs to host names, best effort, off the main thread. */
    suspend fun resolve(addresses: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        addresses.distinct().associateWith { addr ->
            runCatching { InetAddress.getByName(addr).canonicalHostName }.getOrNull()?.takeIf { it != addr } ?: ""
        }.filterValues { it.isNotEmpty() }
    }

    private fun labelFor(uid: Int): Pair<String, String> = labelCache.getOrPut(uid) {
        val pm = context.packageManager
        val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: "uid $uid"
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        pkg to label
    }

    /** Mutable accumulator used while folding netstats buckets for one uid. */
    class UsageAgg {
        var rxBytes = 0L; var txBytes = 0L; var rxPackets = 0L; var txPackets = 0L
        var wifiBytes = 0L; var mobileBytes = 0L
        var foregroundBytes = 0L; var backgroundBytes = 0L
        var firstMs = Long.MAX_VALUE; var lastMs = 0L
        val byBucket = HashMap<Long, LongArray>() // startMs -> [rx, tx, durationMs]

        fun buckets(): List<UsageBucket> = byBucket.entries
            .sortedBy { it.key }
            .map { (start, v) -> UsageBucket(start, v[2], v[0], v[1]) }
    }

    companion object {
        fun parseProcNet(text: String): List<Connection> {
            val out = ArrayList<Connection>()
            var protocol = Protocol.TCP
            var headers = 0
            for (line in text.lineSequence()) {
                val t = line.trim()
                if (t.isEmpty()) continue
                if (t.startsWith("sl")) {
                    // cat concatenates tcp, tcp6, udp, udp6 in order; the first two are TCP.
                    protocol = if (headers < 2) Protocol.TCP else Protocol.UDP
                    headers++
                    continue
                }
                val cols = t.split(Regex("\\s+"))
                if (cols.size < 10) continue
                val local = cols[1].split(':')
                val remote = cols[2].split(':')
                if (local.size != 2 || remote.size != 2) continue
                val ipv6 = local[0].length > 8
                val uid = cols[7].toIntOrNull() ?: continue
                val queues = cols[4].split(':')
                out += Connection(
                    protocol = protocol,
                    ipv6 = ipv6,
                    localAddress = hexToIp(local[0], ipv6),
                    localPort = local[1].toInt(16),
                    remoteAddress = hexToIp(remote[0], ipv6),
                    remotePort = remote[1].toInt(16),
                    state = if (protocol == Protocol.TCP) tcpState(cols[3]) else "—",
                    uid = uid,
                    txQueue = queues.getOrNull(0)?.toLongOrNull(16) ?: 0,
                    rxQueue = queues.getOrNull(1)?.toLongOrNull(16) ?: 0,
                    retransmits = cols[6].toLongOrNull(16) ?: 0,
                    inode = cols[9].toLongOrNull() ?: 0,
                )
            }
            return out
        }

        private fun tcpState(hex: String): String = when (hex) {
            "01" -> "ESTABLISHED"; "02" -> "SYN_SENT"; "03" -> "SYN_RECV"; "04" -> "FIN_WAIT1"; "05" -> "FIN_WAIT2"
            "06" -> "TIME_WAIT"; "07" -> "CLOSE"; "08" -> "CLOSE_WAIT"; "09" -> "LAST_ACK"; "0A" -> "LISTEN"; "0B" -> "CLOSING"
            else -> "?"
        }

        /** /proc/net stores v4 as little-endian hex; v6 as four little-endian 32-bit words. */
        fun hexToIp(hex: String, ipv6: Boolean): String {
            if (!ipv6) {
                if (hex.length != 8) return hex
                val b = (0..3).map { hex.substring(it * 2, it * 2 + 2).toInt(16) }
                return "${b[3]}.${b[2]}.${b[1]}.${b[0]}"
            }
            if (hex.length != 32) return hex
            val words = (0..3).map { hex.substring(it * 8, it * 8 + 8) }
            val bytes = ArrayList<Int>(16)
            for (w in words) for (i in 3 downTo 0) bytes += w.substring(i * 2, i * 2 + 2).toInt(16)
            // Map ::ffff:a.b.c.d to the v4 form for readability.
            if (bytes.take(10).all { it == 0 } && bytes[10] == 0xFF && bytes[11] == 0xFF) {
                return "${bytes[12]}.${bytes[13]}.${bytes[14]}.${bytes[15]}"
            }
            val groups = (0..7).map { (bytes[it * 2] shl 8) or bytes[it * 2 + 1] }
            return compressV6(groups)
        }

        /** Collapse the longest run of zero groups into "::", per RFC 5952. */
        private fun compressV6(groups: List<Int>): String {
            var bestStart = -1; var bestLen = 0; var curStart = -1; var curLen = 0
            for (i in groups.indices) {
                if (groups[i] == 0) { if (curStart < 0) curStart = i; curLen++; if (curLen > bestLen) { bestLen = curLen; bestStart = curStart } }
                else { curStart = -1; curLen = 0 }
            }
            if (bestLen < 2) return groups.joinToString(":") { "%x".format(it) }
            val head = (0 until bestStart).joinToString(":") { "%x".format(groups[it]) }
            val tail = (bestStart + bestLen until 8).joinToString(":") { "%x".format(groups[it]) }
            return "$head::$tail".let { if (it == "::") "::" else it }
        }

        /** From `dumpsys netstats detail`: lines `{uid=NNNN,package=x}=BYTES`; here BYTES is one direction. */
        fun parseNetstats(text: String): Map<Int, Pair<Long, Long>> {
            val rx = HashMap<Int, Long>()
            val re = Regex("""\{uid=(\d+),package=[^}]*}=(\d+)""")
            for (m in re.findAll(text)) {
                val uid = m.groupValues[1].toInt()
                val bytes = m.groupValues[2].toLong()
                rx[uid] = (rx[uid] ?: 0) + bytes
            }
            return rx.mapValues { it.value to 0L }
        }

        private val UID_RE = Regex("""uid=(-?\d+)""")
        private val SET_RE = Regex("""set=(\w+)""")
        private val TAG_RE = Regex("""tag=(0x[0-9a-fA-F]+)""")
        private val TRANSPORTS_RE = Regex("""transports=\{([\d, ]*)\}""")
        private val DURATION_RE = Regex("""bucketDuration=(\d+)""")
        private val BUCKET_RE = Regex("""st=(\d+)\s+rb=(\d+)\s+rp=(\d+)\s+tb=(\d+)\s+tp=(\d+)""")

        /**
         * Parse the "UID stats" history from `dumpsys netstats detail`. Each block is headed by an
         * `ident=[{...}] uid=N set=X tag=0xY` line and followed by `st=.. rb=.. rp=.. tb=.. tp=..`
         * buckets (st and bucketDuration are epoch seconds). Only real apps (uid >= 10000) and the
         * untagged rollup (tag=0x0) are summed, so tagged sub-totals aren't double counted.
         */
        fun parseUsage(text: String): Map<Int, UsageAgg> {
            val out = HashMap<Int, UsageAgg>()
            var uid = -1
            var wifi = true
            var foreground = false
            var include = false
            var durationMs = 3_600_000L
            for (line in text.lineSequence()) {
                val t = line.trim()
                if (t.startsWith("ident=")) {
                    uid = UID_RE.find(t)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                    val tag = TAG_RE.find(t)?.groupValues?.get(1)
                    val transports = TRANSPORTS_RE.find(t)?.groupValues?.get(1)
                    wifi = transports?.split(",")?.map { it.trim() }?.contains("1") ?: true
                    foreground = SET_RE.find(t)?.groupValues?.get(1) == "FOREGROUND"
                    include = uid >= 10000 && (tag == null || tag == "0x0")
                    continue
                }
                if (t.startsWith("NetworkStatsHistory")) {
                    durationMs = (DURATION_RE.find(t)?.groupValues?.get(1)?.toLongOrNull() ?: 3600L) * 1000L
                    continue
                }
                if (!include) continue
                val m = BUCKET_RE.find(t) ?: continue
                val st = m.groupValues[1].toLong() * 1000L
                val rb = m.groupValues[2].toLong()
                val rp = m.groupValues[3].toLong()
                val tb = m.groupValues[4].toLong()
                val tp = m.groupValues[5].toLong()
                if (rb == 0L && tb == 0L) continue
                val agg = out.getOrPut(uid) { UsageAgg() }
                agg.rxBytes += rb; agg.txBytes += tb; agg.rxPackets += rp; agg.txPackets += tp
                if (wifi) agg.wifiBytes += rb + tb else agg.mobileBytes += rb + tb
                if (foreground) agg.foregroundBytes += rb + tb else agg.backgroundBytes += rb + tb
                if (st < agg.firstMs) agg.firstMs = st
                if (st + durationMs > agg.lastMs) agg.lastMs = st + durationMs
                val slot = agg.byBucket.getOrPut(st) { longArrayOf(0, 0, durationMs) }
                slot[0] += rb; slot[1] += tb
            }
            return out
        }
    }
}
