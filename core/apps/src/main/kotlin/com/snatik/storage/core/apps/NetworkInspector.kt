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

/**
 * Live network activity per app from `/proc/net/tcp`, `/proc/net/udp` and their v6 forms, plus
 * per-uid byte totals from `dumpsys netstats`. No VPN, no root; reads what the shell may see.
 */
class NetworkInspector(private val context: Context, private val privilege: PrivilegeManager) {

    private val labelCache = HashMap<Int, Pair<String, String>>()

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

    companion object {
        fun parseProcNet(text: String): List<Connection> {
            val out = ArrayList<Connection>()
            for (line in text.lineSequence()) {
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("sl")) continue
                val cols = t.split(Regex("\\s+"))
                if (cols.size < 10) continue
                val local = cols[1].split(':')
                val remote = cols[2].split(':')
                if (local.size != 2 || remote.size != 2) continue
                val ipv6 = local[0].length > 8
                val uid = cols[7].toIntOrNull() ?: continue
                val state = cols[3]
                // udp rows have the same shape; distinguish by the absence of a connected state is unreliable,
                // so mark protocol by address length only for display and treat st as hex TCP state when applicable.
                out += Connection(
                    protocol = Protocol.TCP,
                    ipv6 = ipv6,
                    localAddress = hexToIp(local[0], ipv6),
                    localPort = local[1].toInt(16),
                    remoteAddress = hexToIp(remote[0], ipv6),
                    remotePort = remote[1].toInt(16),
                    state = tcpState(state),
                    uid = uid,
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
    }
}
