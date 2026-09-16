package com.snatik.storage.core.apps

import android.os.Build
import android.os.Debug
import android.os.StatFs
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/* ---------- data ---------- */

/** One mounted filesystem from /proc/mounts, optionally with capacity/free from statfs. */
data class Mount(val device: String, val mountPoint: String, val type: String, val options: String, val totalBytes: Long = 0, val freeBytes: Long = 0)

/** A block device / partition from /proc/partitions. */
data class Partition(val name: String, val bytes: Long)

/** A swap area from /proc/swaps. */
data class SwapArea(val name: String, val type: String, val sizeKb: Long, val usedKb: Long)

/** ZRAM compressed-swap stats from /sys/block/zram0. */
data class ZramInfo(val disksizeBytes: Long, val originalBytes: Long, val compressedBytes: Long, val memUsedBytes: Long) {
    val ratio: Double get() = if (compressedBytes > 0) originalBytes.toDouble() / compressedBytes else 0.0
}

/** A rollup of this process's /proc/self/smaps. */
data class SmapsRollup(val rssKb: Long, val pssKb: Long, val privateDirtyKb: Long, val swapKb: Long, val regions: Int)

/** Device, kernel and OS identity + live load/uptime. */
data class DeviceInfo(
    val kernel: String,
    val fingerprint: String,
    val androidVersion: String,
    val securityPatch: String,
    val manufacturer: String,
    val model: String,
    val board: String,
    val hardware: String,
    val bootloader: String,
    val selinux: String,
    val uptimeSec: Long,
    val load1: Double,
    val load5: Double,
    val load15: Double,
    val procsRunning: Int,
    val procsTotal: Int,
)

data class CpuCore(val index: Int, val curKhz: Long, val minKhz: Long, val maxKhz: Long, val governor: String, val online: Boolean)
data class ThermalZone(val type: String, val celsius: Double)
data class CpuInfo(
    val model: String,
    val hardware: String,
    val cores: Int,
    val abi: String,
    val usagePercent: Double?,
    val perCore: List<CpuCore>,
    val temps: List<ThermalZone>,
)

/** /proc/meminfo (kB), with a few headline fields pulled out. */
data class MemInfo(val fields: List<Pair<String, Long>>) {
    private val map = fields.toMap()
    fun kb(key: String): Long = map[key] ?: 0
    val totalKb get() = kb("MemTotal")
    val availableKb get() = kb("MemAvailable")
    val freeKb get() = kb("MemFree")
    val cachedKb get() = kb("Cached") + kb("Buffers")
    val swapTotalKb get() = kb("SwapTotal")
    val swapFreeKb get() = kb("SwapFree")
}

/** This app's memory from Debug.MemoryInfo (kB). */
data class AppMem(val totalPssKb: Long, val javaHeapKb: Long, val nativeHeapKb: Long, val codeKb: Long, val stackKb: Long, val graphicsKb: Long, val otherKb: Long, val swapKb: Long)

data class BlockDevice(val name: String, val model: String, val sizeBytes: Long, val rotational: Boolean, val scheduler: String, val readOnly: Boolean, val removable: Boolean)
data class DiskStat(val name: String, val readBytes: Long, val writeBytes: Long)

/** One system property from getprop. */
data class Prop(val key: String, val value: String)

/** A cheap live sample: CPU busy % (since the last sample) and RAM used. */
data class CpuMemSample(val cpuPercent: Double?, val memUsedPercent: Double, val memUsedKb: Long, val memTotalKb: Long)

/** Battery health from /sys/class/power_supply/battery. */
data class BatteryInfo(
    val percent: Int,
    val status: String,
    val health: String,
    val tempC: Float,
    val technology: String,
    val voltageMv: Int,
    val cycleCount: Int,
)

data class SystemReport(
    val device: DeviceInfo,
    val cpu: CpuInfo,
    val mem: MemInfo,
    val appMem: AppMem?,
    val battery: BatteryInfo?,
    val zram: ZramInfo?,
    val swaps: List<SwapArea>,
    val blocks: List<BlockDevice>,
    val diskstats: List<DiskStat>,
    val mounts: List<Mount>,
    val partitions: List<Partition>,
)

/**
 * Reads the kernel's own view of the device from world-readable /proc and /sys files, `getprop` and
 * a few system APIs: device/kernel identity, live CPU frequency/usage/temperatures, the full memory
 * breakdown, block devices and their I/O, swap/ZRAM, mounts with free space, and every system
 * property. A privileged shell (uid 2000) is used to read what the app uid cannot, falling back to a
 * direct read otherwise.
 */
class SystemInspector(private val privilege: PrivilegeManager) {

    // Previous /proc/stat total/busy jiffies, so each report can compute CPU usage since the last.
    @Volatile private var prevCpu: Pair<Long, Long>? = null
    // While a report/sample runs, all the files it needs are read in one shell call and cached here,
    // so read() returns instantly instead of a shell round-trip per file.
    @Volatile private var cache: Map<String, String>? = null
    // report() and the fast sample() both prime the shared cache, so they must not overlap.
    private val cacheLock = Mutex()

    suspend fun report(): SystemReport = withContext(Dispatchers.IO) { cacheLock.withLock {
        cache = bulk(reportPaths())
        try {
            SystemReport(
                device = readDevice(),
                cpu = readCpu(),
                mem = MemInfo(parseMeminfo(read("/proc/meminfo"))),
                appMem = readAppMem(),
                battery = readBattery(),
                zram = readZram(),
                swaps = parseSwaps(read("/proc/swaps")),
                blocks = readBlocks(),
                diskstats = readDiskstats(),
                mounts = parseMounts(read("/proc/mounts")).map { it.withUsage() },
                partitions = parsePartitions(read("/proc/partitions")),
            )
        } finally { cache = null }
    } }

    // The fast sampler keeps its own /proc/stat delta so it never fights the report's CPU reading.
    @Volatile private var prevCpuSample: Pair<Long, Long>? = null

    /**
     * A fast live sample (one shell call reading /proc/stat + /proc/meminfo) to drive the ticking
     * Overview graphs. It is deliberately independent of the shared report cache/lock, so a heavy
     * full report in flight can never stall the graph from ticking.
     */
    suspend fun sample(): CpuMemSample = withContext(Dispatchers.IO) {
        val exec = privilege.executor.value
        val text = if (exec != null) {
            runCatching { exec.run("cat /proc/stat /proc/meminfo 2>/dev/null", timeoutMs = 8_000).out }.getOrDefault("")
        } else {
            runCatching { File("/proc/stat").readText() }.getOrDefault("") + "\n" +
                runCatching { File("/proc/meminfo").readText() }.getOrDefault("")
        }
        val cpu = sampleCpuUsage(text)
        val mem = MemInfo(parseMeminfo(text))  // /proc/stat lines have no ':' so they're ignored here
        val used = (mem.totalKb - mem.availableKb).coerceAtLeast(0)
        CpuMemSample(cpu, if (mem.totalKb > 0) used.toDouble() / mem.totalKb * 100 else 0.0, used, mem.totalKb)
    }

    /** CPU busy % since the previous sample, from the aggregate `cpu` line of /proc/stat. */
    private fun sampleCpuUsage(statText: String): Double? {
        val line = statText.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        val n = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (n.size < 4) return null
        val idle = n[3] + (n.getOrNull(4) ?: 0)  // idle + iowait
        val total = n.sum()
        val prev = prevCpuSample
        prevCpuSample = total to idle
        if (prev == null) return null
        val dTotal = total - prev.first
        val dIdle = idle - prev.second
        return if (dTotal > 0) ((dTotal - dIdle).toDouble() / dTotal * 100).coerceIn(0.0, 100.0) else null
    }

    private suspend fun reportPaths(): List<String> {
        val cpuDirs = listNames("/sys/devices/system/cpu").filter { it.matches(Regex("cpu[0-9]+")) }
        val thermal = listNames("/sys/class/thermal").filter { it.startsWith("thermal_zone") }
        val blocks = listNames("/sys/block").filter { it.isNotBlank() && !it.startsWith("loop") && !it.startsWith("ram") && !it.startsWith("dm-") }
        return buildList {
            addAll(listOf("/proc/version", "/proc/uptime", "/proc/loadavg", "/proc/cpuinfo", "/proc/meminfo", "/proc/stat", "/proc/swaps", "/proc/diskstats", "/proc/mounts", "/proc/partitions", "/sys/fs/selinux/enforce", "/sys/block/zram0/disksize", "/sys/block/zram0/mm_stat"))
            listOf("capacity", "status", "health", "temp", "technology", "voltage_now", "cycle_count").forEach { add("/sys/class/power_supply/battery/$it") }
            cpuDirs.forEach { c ->
                listOf("scaling_cur_freq", "scaling_min_freq", "scaling_max_freq", "scaling_governor").forEach { add("/sys/devices/system/cpu/$c/cpufreq/$it") }
                add("/sys/devices/system/cpu/$c/online")
            }
            thermal.forEach { z -> add("/sys/class/thermal/$z/temp"); add("/sys/class/thermal/$z/type") }
            blocks.forEach { b -> listOf("size", "queue/rotational", "queue/scheduler", "ro", "removable", "device/model", "device/name").forEach { add("/sys/block/$b/$it") } }
        }
    }

    /** Read many files in a single shell invocation, keyed by path. Empty when there's no shell.
     *  Plain `echo … ; cat …` statements — no shell variables or printf, which some executors mangle.
     *  Paths here are fixed /proc and /sys paths with no spaces or metacharacters. */
    private suspend fun bulk(paths: List<String>): Map<String, String> {
        val exec = privilege.executor.value ?: return emptyMap()
        val map = HashMap<String, String>(paths.size)
        // Chunk so the command line can never exceed the shell/binder limit, however many paths.
        paths.chunked(120).forEach { chunk ->
            // Trailing `echo` guarantees a newline after each file, so a file with no final newline
            // (e.g. /sys/fs/selinux/enforce = "1") can't run its value into the next @@@ marker.
            val cmd = chunk.joinToString(";") { p -> "echo @@@$p;cat $p 2>/dev/null;echo" }
            val out = runCatching { exec.run(cmd, timeoutMs = 15_000).out }.getOrDefault("")
            var key: String? = null
            val sb = StringBuilder()
            out.lineSequence().forEach { line ->
                if (line.startsWith("@@@")) {
                    key?.let { map[it] = sb.toString() }
                    key = line.substring(3); sb.setLength(0)
                } else { sb.append(line).append('\n') }
            }
            key?.let { map[it] = sb.toString() }
        }
        return map
    }

    /** Every system property (getprop). Lazily loaded by the Properties tab. */
    suspend fun properties(): List<Prop> = withContext(Dispatchers.IO) {
        val exec = privilege.executor.value
        val text = if (exec != null) runCatching { exec.run("getprop", timeoutMs = 15_000).out }.getOrDefault("") else ""
        if (text.isNotBlank()) parseGetprop(text)
        else buildList {
            // Fallback without a shell: a handful from Build.
            add(Prop("ro.product.model", Build.MODEL)); add(Prop("ro.product.manufacturer", Build.MANUFACTURER))
            add(Prop("ro.build.fingerprint", Build.FINGERPRINT)); add(Prop("ro.build.version.release", Build.VERSION.RELEASE))
            add(Prop("ro.build.version.sdk", Build.VERSION.SDK_INT.toString())); add(Prop("ro.build.version.security_patch", Build.VERSION.SECURITY_PATCH))
            add(Prop("ro.board.platform", Build.BOARD)); add(Prop("ro.hardware", Build.HARDWARE))
        }
    }

    /* ---------- readers ---------- */

    private suspend fun readDevice(): DeviceInfo {
        val kernel = read("/proc/version").trim()
        val selinux = when (read("/sys/fs/selinux/enforce").trim()) { "1" -> "Enforcing"; "0" -> "Permissive"; else -> "Unknown" }
        val uptime = read("/proc/uptime").trim().split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull()?.toLong() ?: 0
        val load = read("/proc/loadavg").trim().split(Regex("\\s+"))
        val procs = load.getOrNull(3)?.split('/') ?: emptyList()
        return DeviceInfo(
            kernel = kernel,
            fingerprint = Build.FINGERPRINT,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            securityPatch = Build.VERSION.SECURITY_PATCH,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            bootloader = Build.BOOTLOADER,
            selinux = selinux,
            uptimeSec = uptime,
            load1 = load.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
            load5 = load.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
            load15 = load.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
            procsRunning = procs.getOrNull(0)?.toIntOrNull() ?: 0,
            procsTotal = procs.getOrNull(1)?.toIntOrNull() ?: 0,
        )
    }

    private suspend fun readCpu(): CpuInfo {
        val cpuinfo = read("/proc/cpuinfo")
        val model = cpuinfo.lineSequence().firstOrNull { it.startsWith("model name") || it.startsWith("Processor") }?.substringAfter(':')?.trim().orEmpty()
        val hardware = cpuinfo.lineSequence().firstOrNull { it.startsWith("Hardware") }?.substringAfter(':')?.trim().orEmpty()
        val coreDirs = listNames("/sys/devices/system/cpu").filter { it.matches(Regex("cpu[0-9]+")) }.sortedBy { it.removePrefix("cpu").toIntOrNull() ?: 0 }
        val cores = coreDirs.ifEmpty { (0 until Runtime.getRuntime().availableProcessors()).map { "cpu$it" } }
        val perCore = cores.map { name ->
            val i = name.removePrefix("cpu").toIntOrNull() ?: 0
            val base = "/sys/devices/system/cpu/$name/cpufreq"
            val online = if (i == 0) true else read("/sys/devices/system/cpu/$name/online").trim() != "0"
            CpuCore(
                index = i,
                curKhz = read("$base/scaling_cur_freq").trim().toLongOrNull() ?: 0,
                minKhz = read("$base/scaling_min_freq").trim().toLongOrNull() ?: 0,
                maxKhz = read("$base/scaling_max_freq").trim().toLongOrNull() ?: 0,
                governor = read("$base/scaling_governor").trim(),
                online = online,
            )
        }
        return CpuInfo(model, hardware, perCore.size, Build.SUPPORTED_ABIS.firstOrNull().orEmpty(), readCpuUsage(), perCore, readTemps())
    }


    private suspend fun readCpuUsage(): Double? {
        val line = read("/proc/stat").lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        val n = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (n.size < 4) return null
        val idle = n[3] + (n.getOrNull(4) ?: 0)  // idle + iowait
        val total = n.sum()
        val prev = prevCpu
        prevCpu = total to idle
        if (prev == null) return null
        val dTotal = total - prev.first
        val dIdle = idle - prev.second
        return if (dTotal > 0) ((dTotal - dIdle).toDouble() / dTotal * 100).coerceIn(0.0, 100.0) else null
    }

    private suspend fun readTemps(): List<ThermalZone> {
        return listNames("/sys/class/thermal").filter { it.startsWith("thermal_zone") }.mapNotNull { z ->
            val milli = read("/sys/class/thermal/$z/temp").trim().toLongOrNull() ?: return@mapNotNull null
            val type = read("/sys/class/thermal/$z/type").trim().ifEmpty { z }
            val c = milli / 1000.0
            if (c in 1.0..150.0) ThermalZone(type, c) else null
        }.sortedByDescending { it.celsius }
    }

    private fun readAppMem(): AppMem? = runCatching {
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        fun stat(k: String) = mi.getMemoryStat(k)?.toLongOrNull() ?: 0L
        AppMem(
            totalPssKb = stat("summary.total-pss"),
            javaHeapKb = stat("summary.java-heap"),
            nativeHeapKb = stat("summary.native-heap"),
            codeKb = stat("summary.code"),
            stackKb = stat("summary.stack"),
            graphicsKb = stat("summary.graphics"),
            otherKb = stat("summary.private-other"),
            swapKb = stat("summary.total-swap"),
        )
    }.getOrNull()

    private suspend fun readBlocks(): List<BlockDevice> {
        return listNames("/sys/block").filter { it.isNotBlank() && !it.startsWith("loop") && !it.startsWith("ram") && !it.startsWith("dm-") }.mapNotNull { name ->
            val base = "/sys/block/$name"
            val sectors = read("$base/size").trim().toLongOrNull() ?: return@mapNotNull null
            if (sectors <= 0) return@mapNotNull null
            val model = read("$base/device/model").trim().ifEmpty { read("$base/device/name").trim() }
            val scheduler = read("$base/queue/scheduler").trim().let { s -> Regex("\\[(.+?)]").find(s)?.groupValues?.get(1) ?: s.substringBefore(' ') }
            BlockDevice(
                name = name,
                model = model,
                sizeBytes = sectors * 512,
                rotational = read("$base/queue/rotational").trim() == "1",
                scheduler = scheduler,
                readOnly = read("$base/ro").trim() == "1",
                removable = read("$base/removable").trim() == "1",
            )
        }.sortedByDescending { it.sizeBytes }
    }

    private suspend fun readDiskstats(): List<DiskStat> = parseDiskstats(read("/proc/diskstats"))

    private suspend fun readBattery(): BatteryInfo? {
        val pct = read("/sys/class/power_supply/battery/capacity").trim().toIntOrNull() ?: return null
        val rawTemp = read("/sys/class/power_supply/battery/temp").trim().toIntOrNull()
        return BatteryInfo(
            percent = pct,
            status = read("/sys/class/power_supply/battery/status").trim().ifBlank { "Unknown" },
            health = read("/sys/class/power_supply/battery/health").trim().ifBlank { "Unknown" },
            tempC = rawTemp?.let { it / 10f } ?: 0f,
            technology = read("/sys/class/power_supply/battery/technology").trim(),
            voltageMv = read("/sys/class/power_supply/battery/voltage_now").trim().toLongOrNull()?.let { (it / 1000).toInt() } ?: 0,
            cycleCount = read("/sys/class/power_supply/battery/cycle_count").trim().toIntOrNull() ?: 0,
        )
    }

    /** Top wake locks from `dumpsys batterystats` — heavy, so loaded on demand rather than per report. */
    suspend fun wakelocks(): List<Wakelock> = withContext(Dispatchers.IO) {
        val exec = privilege.executor.value ?: return@withContext emptyList()
        val text = runCatching { exec.run("dumpsys batterystats", timeoutMs = 30_000).out }.getOrDefault("")
        runCatching { DeviceStatsRepository.parseWakelocks(text) }.getOrDefault(emptyList())
    }

    private fun Mount.withUsage(): Mount = runCatching {
        val f = File(mountPoint)
        if (!f.isDirectory) return this
        val st = StatFs(mountPoint)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        if (total > 0) copy(totalBytes = total, freeBytes = free) else this
    }.getOrDefault(this)

    /* ---------- shell/file read ---------- */

    private suspend fun read(path: String): String {
        // A blank batched value (e.g. a file the batch couldn't read) falls through to a direct read.
        cache?.get(path)?.takeIf { it.isNotBlank() }?.let { return it }
        val exec = privilege.executor.value
        if (exec != null) {
            val out = runCatching { exec.run("cat $path 2>/dev/null", timeoutMs = 15_000).out }.getOrDefault("")
            if (out.isNotBlank()) return out
        }
        return runCatching { File(path).readText() }.getOrDefault("")
    }

    private suspend fun listNames(dir: String): List<String> {
        runCatching { File(dir).list()?.toList() }.getOrNull()?.let { if (it.isNotEmpty()) return it }
        val exec = privilege.executor.value ?: return emptyList()
        return runCatching { exec.run("ls -1 $dir 2>/dev/null", timeoutMs = 10_000).out.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList() }.getOrDefault(emptyList())
    }

    private suspend fun readZram(): ZramInfo? {
        val disksize = read("/sys/block/zram0/disksize").trim().toLongOrNull() ?: return null
        val mm = read("/sys/block/zram0/mm_stat").trim().split(Regex("\\s+"))
        return ZramInfo(disksize, mm.getOrNull(0)?.toLongOrNull() ?: 0, mm.getOrNull(1)?.toLongOrNull() ?: 0, mm.getOrNull(2)?.toLongOrNull() ?: 0)
    }

    companion object {
        fun parseMounts(text: String): List<Mount> = text.lineSequence().mapNotNull { line ->
            val p = line.trim().split(' ')
            if (p.size >= 4) Mount(p[0], p[1], p[2], p[3]) else null
        }.toList()

        fun parsePartitions(text: String): List<Partition> = text.lineSequence().drop(1).mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size >= 4) p[3].let { name -> (p[2].toLongOrNull())?.let { Partition(name, it * 1024) } } else null
        }.filter { it.bytes > 0 }.sortedByDescending { it.bytes }.toList()

        fun parseSwaps(text: String): List<SwapArea> = text.lineSequence().drop(1).mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size >= 4) SwapArea(p[0], p[1], p[2].toLongOrNull() ?: 0, p[3].toLongOrNull() ?: 0) else null
        }.toList()

        fun parseMeminfo(text: String): List<Pair<String, Long>> = text.lineSequence().mapNotNull { line ->
            val i = line.indexOf(':'); if (i < 0) return@mapNotNull null
            val key = line.substring(0, i).trim()
            val kb = line.substring(i + 1).trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: return@mapNotNull null
            key to kb
        }.toList()

        /** /proc/diskstats: fields 3=name, 6=sectors read, 10=sectors written (512-byte sectors). */
        fun parseDiskstats(text: String): List<DiskStat> = text.lineSequence().mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size < 10) return@mapNotNull null
            val name = p[2]
            if (name.startsWith("loop") || name.startsWith("ram")) return@mapNotNull null
            val readSectors = p.getOrNull(5)?.toLongOrNull() ?: 0
            val writeSectors = p.getOrNull(9)?.toLongOrNull() ?: 0
            if (readSectors == 0L && writeSectors == 0L) null else DiskStat(name, readSectors * 512, writeSectors * 512)
        }.sortedByDescending { it.readBytes + it.writeBytes }.toList()

        fun parseGetprop(text: String): List<Prop> = text.lineSequence().mapNotNull { line ->
            // Format: [key]: [value]
            val m = Regex("^\\[(.*?)]:\\s*\\[(.*)]$").find(line.trim()) ?: return@mapNotNull null
            Prop(m.groupValues[1], m.groupValues[2])
        }.sortedBy { it.key }.toList()

        private val REGION = Regex("^[0-9a-f]+-[0-9a-f]+ ")

        fun parseSmaps(text: String): SmapsRollup? {
            if (text.isBlank()) return null
            var rss = 0L; var pss = 0L; var pd = 0L; var swap = 0L; var regions = 0
            text.lineSequence().forEach { line ->
                when {
                    REGION.containsMatchIn(line) -> regions++
                    line.startsWith("Rss:") -> rss += kb(line)
                    line.startsWith("Pss:") -> pss += kb(line)
                    line.startsWith("Private_Dirty:") -> pd += kb(line)
                    line.startsWith("Swap:") -> swap += kb(line)
                }
            }
            return SmapsRollup(rss, pss, pd, swap, regions)
        }

        private fun kb(line: String): Long = line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: 0
    }
}
