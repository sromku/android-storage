package com.snatik.storage.core.apps

import android.os.Build
import android.os.Debug
import android.os.StatFs
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
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

data class SystemReport(
    val device: DeviceInfo,
    val cpu: CpuInfo,
    val mem: MemInfo,
    val appMem: AppMem?,
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

    suspend fun report(): SystemReport = withContext(Dispatchers.IO) {
        SystemReport(
            device = readDevice(),
            cpu = readCpu(),
            mem = MemInfo(parseMeminfo(read("/proc/meminfo"))),
            appMem = readAppMem(),
            zram = readZram(),
            swaps = parseSwaps(read("/proc/swaps")),
            blocks = readBlocks(),
            diskstats = readDiskstats(),
            mounts = parseMounts(read("/proc/mounts")).map { it.withUsage() },
            partitions = parsePartitions(read("/proc/partitions")),
        )
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
        return listNames("/sys/block").filter { it.isNotBlank() && !it.startsWith("loop") && !it.startsWith("ram") }.mapNotNull { name ->
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
