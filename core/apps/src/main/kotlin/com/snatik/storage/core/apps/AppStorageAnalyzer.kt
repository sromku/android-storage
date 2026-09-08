package com.snatik.storage.core.apps

import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.shellQuote
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One component of an app's on-device footprint. */
data class StorageSlice(val label: String, val bytes: Long, val path: String)

data class AppFootprint(val packageName: String, val slices: List<StorageSlice>) {
    val total: Long get() = slices.sumOf { it.bytes }
    val nonEmpty: List<StorageSlice> get() = slices.filter { it.bytes > 0 }
}

/**
 * Decomposes an installed app's disk use into its parts: the base APK, split APKs, the compiled
 * OAT/ART artifacts, bundled native libraries, and its private data and cache. APK and split sizes
 * come from the readable public paths; OAT, libs and data need a shell to size, so those fall back
 * to StorageStatsManager numbers where a shell is not connected.
 */
class AppStorageAnalyzer(
    private val apps: AppRepository,
    private val privilege: PrivilegeManager,
) {
    suspend fun analyze(packageName: String): AppFootprint? = withContext(Dispatchers.IO) {
        val details = apps.details(packageName) ?: return@withContext null
        val executor = privilege.executor.value

        suspend fun du(path: String?): Long {
            if (path.isNullOrEmpty()) return 0
            executor?.let { exec ->
                val kb = runCatching {
                    exec.run("du -sk ${path.shellQuote()} 2>/dev/null | cut -f1", timeoutMs = 30_000).out.trim().toLongOrNull()
                }.getOrNull()
                if (kb != null) return kb * 1024
            }
            val f = File(path)
            return if (f.exists()) if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length() else 0
        }

        val apkPath = details.summary.apkPath
        val baseApk = if (apkPath.isNotEmpty()) File(apkPath).length().coerceAtLeast(0) else 0
        val splits = details.splitApks.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
        val oatDir = if (apkPath.isNotEmpty()) File(apkPath).parent?.let { "$it/oat" } else null
        val oat = du(oatDir)
        val libs = du(details.nativeLibraryDir)

        val storage = details.summary.storage
        // Prefer StorageStats for data/cache (exact and permission-clean); shell du as fallback.
        val cache = storage?.cacheBytes ?: du("${details.dataDir}/cache")
        val dataTotal = storage?.dataBytes ?: du(details.dataDir)
        val data = (dataTotal - cache).coerceAtLeast(0)
        val external = du(details.externalDataDir)

        AppFootprint(
            packageName,
            listOf(
                StorageSlice("Base APK", baseApk, apkPath),
                StorageSlice("Split APKs", splits, details.splitApks.firstOrNull() ?: ""),
                StorageSlice("Compiled (OAT)", oat, oatDir ?: ""),
                StorageSlice("Native libs", libs, details.nativeLibraryDir ?: ""),
                StorageSlice("Data", data, details.dataDir),
                StorageSlice("Cache", cache, "${details.dataDir}/cache"),
                StorageSlice("External data", external, details.externalDataDir ?: ""),
            ),
        )
    }
}
