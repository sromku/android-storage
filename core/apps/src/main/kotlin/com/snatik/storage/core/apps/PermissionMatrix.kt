package com.snatik.storage.core.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MatrixApp(val packageName: String, val label: String, val system: Boolean, val granted: Set<String>, val requested: Set<String>)
data class PermissionMatrix(val permissions: List<String>, val apps: List<MatrixApp>) {
    /** How many listed apps were granted each permission, for a column heat count. */
    fun grantedCount(permission: String): Int = apps.count { permission in it.granted }
}

/** All apps against the dangerous permissions, as a grid. One PackageManager pass. */
class PermissionMatrixRepository(private val context: Context) {

    private val dangerous = listOf(
        "ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION", "CAMERA", "RECORD_AUDIO", "READ_CONTACTS",
        "READ_SMS", "READ_CALL_LOG", "READ_CALENDAR", "READ_PHONE_STATE", "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT",
        "NEARBY_WIFI_DEVICES", "POST_NOTIFICATIONS", "BODY_SENSORS", "ACTIVITY_RECOGNITION",
        "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE",
        "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES", "QUERY_ALL_PACKAGES", "GET_ACCOUNTS", "INTERNET",
    )

    @Suppress("DEPRECATION")
    suspend fun matrix(includeSystem: Boolean = false): PermissionMatrix = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledPackages(flags)
        }
        val short = { p: String -> p.substringAfterLast('.') }
        val apps = packages.mapNotNull { info: PackageInfo ->
            val app = info.applicationInfo ?: return@mapNotNull null
            val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (system && !includeSystem) return@mapNotNull null
            val requested = info.requestedPermissions?.map { short(it) }?.toSet().orEmpty()
            val flagsArr = info.requestedPermissionsFlags ?: IntArray(0)
            val granted = info.requestedPermissions?.mapIndexedNotNull { i, p ->
                if (flagsArr.getOrElse(i) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0) short(p) else null
            }?.toSet().orEmpty()
            val relevant = requested.intersect(dangerous.toSet())
            if (relevant.isEmpty()) return@mapNotNull null
            MatrixApp(info.packageName, runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(info.packageName), system, granted.intersect(dangerous.toSet()), relevant)
        }.sortedByDescending { it.granted.size }
        // Only show columns some app actually uses, in the fixed order.
        val usedCols = dangerous.filter { col -> apps.any { col in it.requested } }
        PermissionMatrix(usedCols, apps)
    }
}
