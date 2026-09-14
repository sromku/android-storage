package com.snatik.storage.core.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The kind of protection a permission carries — the primary badge. */
enum class PermCategory { DANGEROUS, SPECIAL, SIGNATURE, NORMAL, UNKNOWN }

/** One permission, enriched from PackageManager metadata. */
data class PermInfo(
    val name: String,          // full permission string
    val short: String,         // last segment, e.g. CAMERA
    val label: String?,        // human label, when the platform provides one
    val group: String?,        // group id last segment, e.g. LOCATION
    val category: PermCategory,
    val privileged: Boolean,   // signature|privileged
    val restricted: Boolean,   // hard/soft restricted (SMS, call log …)
    val custom: Boolean,       // defined by an app rather than the platform
    val definingLabel: String?,// label of the defining app, when custom
    val requestedBy: Int = 0,
    val grantedBy: Int = 0,
)

/** One app and the permissions it declares, with grant state. */
data class PermApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val requested: Set<String>,   // full names
    val granted: Set<String>,
)

/** Legacy grid shape (dangerous-only, short names) kept for the API op and the footprint view. */
data class MatrixApp(val packageName: String, val label: String, val system: Boolean, val granted: Set<String>, val requested: Set<String>)
data class PermissionMatrix(val permissions: List<String>, val apps: List<MatrixApp>) {
    fun grantedCount(permission: String): Int = apps.count { permission in it.granted }
}

/** Everything the permission tools work from: apps and the enriched permission table. */
data class PermissionData(
    val apps: List<PermApp>,
    val perms: Map<String, PermInfo>,
) {
    fun info(name: String): PermInfo? = perms[name]
    fun categoryCount(app: PermApp, category: PermCategory): Int = app.requested.count { perms[it]?.category == category }
    fun customCount(app: PermApp): Int = app.requested.count { perms[it]?.custom == true }
}

/**
 * Reads every permission every app declares — not just a fixed dangerous set — and enriches each
 * with its protection level, group and defining package, so the UI can slice it many ways.
 */
class PermissionMatrixRepository(private val context: Context) {

    @Suppress("DEPRECATION")
    suspend fun load(includeSystem: Boolean = false): PermissionData = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledPackages(flags)
        }

        val apps = packages.mapNotNull { info: PackageInfo ->
            val app = info.applicationInfo ?: return@mapNotNull null
            val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (system && !includeSystem) return@mapNotNull null
            val requested = info.requestedPermissions?.toSet().orEmpty()
            if (requested.isEmpty()) return@mapNotNull null
            val flagsArr = info.requestedPermissionsFlags ?: IntArray(0)
            val granted = info.requestedPermissions?.mapIndexedNotNull { i, p ->
                if (flagsArr.getOrElse(i) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0) p else null
            }?.toSet().orEmpty()
            val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(info.packageName)
            PermApp(info.packageName, label, system, requested, granted)
        }

        val names = apps.flatMapTo(HashSet()) { it.requested }
        val labelCache = HashMap<String, String?>()
        val perms = names.associateWith { name ->
            val requestedBy = apps.count { name in it.requested }
            val grantedBy = apps.count { name in it.granted }
            describe(pm, name, labelCache).copy(requestedBy = requestedBy, grantedBy = grantedBy)
        }

        val sortedApps = apps.sortedWith(
            compareByDescending<PermApp> { app -> app.requested.count { perms[it]?.category == PermCategory.DANGEROUS } }
                .thenByDescending { app -> app.requested.count { perms[it]?.category == PermCategory.SPECIAL } }
                .thenBy { it.label.lowercase() },
        )
        PermissionData(sortedApps, perms)
    }

    /** Dangerous-only grid, derived from [load], for the API op and footprint ranking. */
    suspend fun matrix(includeSystem: Boolean = false): PermissionMatrix {
        val data = load(includeSystem)
        fun dangerous(names: Set<String>) = names.filter { data.perms[it]?.category == PermCategory.DANGEROUS }.map { it.substringAfterLast('.') }.toSet()
        val apps = data.apps.mapNotNull { app ->
            val req = dangerous(app.requested)
            if (req.isEmpty()) null else MatrixApp(app.packageName, app.label, app.system, dangerous(app.granted), req)
        }.sortedByDescending { it.granted.size }
        val cols = data.perms.values.filter { it.category == PermCategory.DANGEROUS }.map { it.short }.distinct().filter { c -> apps.any { c in it.requested } }
        return PermissionMatrix(cols, apps)
    }

    private fun describe(pm: PackageManager, name: String, labelCache: HashMap<String, String?>): PermInfo {
        val short = name.substringAfterLast('.')
        val pi = runCatching { pm.getPermissionInfo(name, 0) }.getOrNull()
        if (pi == null) {
            // Not resolvable (defined by an app that isn't installed, or a bespoke string).
            val custom = !name.startsWith("android.permission.")
            return PermInfo(name, short, null, GROUP_BY_PERM[short], if (short in SPECIAL) PermCategory.SPECIAL else PermCategory.UNKNOWN, false, false, custom, null)
        }
        val base = pi.protection
        val protFlags = pi.protectionFlags
        val appop = protFlags and PermissionInfo.PROTECTION_FLAG_APPOP != 0 || short in SPECIAL
        val privileged = protFlags and PermissionInfo.PROTECTION_FLAG_PRIVILEGED != 0
        val restricted = pi.flags and (PermissionInfo.FLAG_HARD_RESTRICTED or PermissionInfo.FLAG_SOFT_RESTRICTED) != 0
        val definingPkg = pi.packageName
        val custom = definingPkg != "android" && definingPkg != null
        val category = when {
            appop -> PermCategory.SPECIAL
            base == PermissionInfo.PROTECTION_DANGEROUS -> PermCategory.DANGEROUS
            base == PermissionInfo.PROTECTION_SIGNATURE -> PermCategory.SIGNATURE
            base == PermissionInfo.PROTECTION_NORMAL -> PermCategory.NORMAL
            else -> PermCategory.UNKNOWN
        }
        // pi.group is "…UNDEFINED" for most runtime perms on recent Android, so fall back to a known map.
        val group = pi.group?.substringAfterLast('.')?.takeIf { it != "UNDEFINED" } ?: GROUP_BY_PERM[short]
        val label = runCatching { pi.loadLabel(pm)?.toString()?.takeIf { it != short && it.isNotBlank() } }.getOrNull()
        val definingLabel = if (custom && definingPkg != null) {
            labelCache.getOrPut(definingPkg) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(definingPkg, 0)).toString() }.getOrNull() }
        } else null
        return PermInfo(name, short, label, group, category, privileged, restricted, custom, definingLabel)
    }

    companion object {
        /** Runtime permission -> user-facing group, since PackageManager reports UNDEFINED for most now. */
        val GROUP_BY_PERM: Map<String, String> = buildMap {
            fun g(group: String, vararg perms: String) = perms.forEach { put(it, group) }
            g("LOCATION", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION")
            g("CAMERA", "CAMERA")
            g("MICROPHONE", "RECORD_AUDIO")
            g("CONTACTS", "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS")
            g("SMS", "SEND_SMS", "RECEIVE_SMS", "READ_SMS", "RECEIVE_MMS", "RECEIVE_WAP_PUSH")
            g("PHONE", "READ_PHONE_STATE", "READ_PHONE_NUMBERS", "CALL_PHONE", "ANSWER_PHONE_CALLS", "ADD_VOICEMAIL", "USE_SIP", "ACCEPT_HANDOVER", "MANAGE_OWN_CALLS")
            g("CALL_LOG", "READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS")
            g("CALENDAR", "READ_CALENDAR", "WRITE_CALENDAR")
            g("STORAGE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE")
            g("MEDIA", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO", "READ_MEDIA_VISUAL_USER_SELECTED", "ACCESS_MEDIA_LOCATION")
            g("SENSORS", "BODY_SENSORS", "BODY_SENSORS_BACKGROUND")
            g("ACTIVITY_RECOGNITION", "ACTIVITY_RECOGNITION")
            g("NEARBY_DEVICES", "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE", "NEARBY_WIFI_DEVICES", "UWB_RANGING")
            g("NOTIFICATIONS", "POST_NOTIFICATIONS")
        }

        /** Special-access / app-op permissions granted from a settings screen, not the runtime prompt. */
        val SPECIAL = setOf(
            "SYSTEM_ALERT_WINDOW", "MANAGE_EXTERNAL_STORAGE", "REQUEST_INSTALL_PACKAGES", "WRITE_SETTINGS",
            "PACKAGE_USAGE_STATS", "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM", "ACCESS_NOTIFICATION_POLICY",
            "MANAGE_MEDIA", "LOADER_USAGE_STATS", "MANAGE_ONGOING_CALLS", "BIND_ACCESSIBILITY_SERVICE",
            "BIND_NOTIFICATION_LISTENER_SERVICE", "BIND_DEVICE_ADMIN", "MANAGE_APP_ALL_SERVICES",
            "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "ACCESS_MEDIA_LOCATION",
        )
    }
}
