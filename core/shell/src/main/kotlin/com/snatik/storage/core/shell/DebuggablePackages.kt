package com.snatik.storage.core.shell

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DebuggableApp(val packageName: String, val label: String, val dataDir: String)

/**
 * Packages built with `debuggable=true`. The shell user may `run-as` these, which opens their
 * private data to us without root.
 */
class DebuggablePackages(private val context: Context) {

    val ownPackageName: String get() = context.packageName

    @Volatile
    private var cache: Map<String, DebuggableApp> = emptyMap()

    /** Needs QUERY_ALL_PACKAGES in the app manifest to see every package. */
    @SuppressLint("QueryPermissionsNeeded")
    suspend fun list(): List<DebuggableApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        val apps = installed
            .filter { it.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && it.packageName != context.packageName }
            .map { DebuggableApp(it.packageName, pm.getApplicationLabel(it).toString(), it.dataDir) }
            .sortedBy { it.label.lowercase() }
        cache = apps.associateBy { it.packageName }
        apps
    }

    /** The debuggable package owning [path], when it lies inside a package data directory. */
    fun ownerOf(path: String): String? {
        val match = DATA_DIR.find(path) ?: return null
        val pkg = match.groupValues[2]
        if (cache.containsKey(pkg)) return pkg
        if (cache.isEmpty()) {
            val info = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
            return pkg.takeIf { info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && pkg != context.packageName }
        }
        return null
    }

    private companion object {
        val DATA_DIR = Regex("^/data/(data|user/\\d+|user_de/\\d+)/([^/]+)")
    }
}
