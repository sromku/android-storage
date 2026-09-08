package com.snatik.storage.core.apps

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Installed packages, their storage footprint and everything the manifest declares. */
class AppRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    /** Whether the user granted usage access, which StorageStatsManager needs for other packages. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    suspend fun list(): List<AppSummary> = withContext(Dispatchers.IO) {
        val usage = hasUsageAccess()
        installedPackages(0).mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            summary(info, app, if (usage) storageOf(app) else null)
        }.sortedBy { it.label.lowercase() }
    }

    suspend fun details(packageName: String): AppDetails? = withContext(Dispatchers.IO) {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES or
            PackageManager.MATCH_DISABLED_COMPONENTS
        val info = packageInfo(packageName, flags) ?: return@withContext null
        val app = info.applicationInfo ?: return@withContext null
        val summary = summary(info, app, if (hasUsageAccess()) storageOf(app) else null)

        val requested = info.requestedPermissions.orEmpty()
        val requestedFlags = info.requestedPermissionsFlags ?: IntArray(requested.size)
        val permissions = requested.mapIndexed { i, name ->
            val granted = requestedFlags.getOrElse(i) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            val runtime = runCatching { pm.getPermissionInfo(name, 0).protection == PermissionInfo.PROTECTION_DANGEROUS }.getOrDefault(false)
            RequestedPermission(name, granted, runtime)
        }

        val signatures = info.signingInfo?.let { signing ->
            val signers = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            signers.orEmpty().map { sig ->
                val bytes = sig.toByteArray()
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }
                val subject = runCatching {
                    (CertificateFactory.getInstance("X.509").generateCertificate(bytes.inputStream()) as X509Certificate).subjectX500Principal.name
                }.getOrDefault("")
                SigningCertificate(digest, subject)
            }
        }.orEmpty()

        AppDetails(
            summary = summary,
            minSdk = app.minSdkVersion,
            dataDir = app.dataDir,
            deviceProtectedDataDir = app.deviceProtectedDataDir,
            externalDataDir = context.getExternalFilesDir(null)?.parentFile?.parentFile?.let { "${it.absolutePath}/$packageName" },
            nativeLibraryDir = app.nativeLibraryDir,
            splitApks = app.splitPublicSourceDirs.orEmpty().toList(),
            installer = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(packageName).installingPackageName
                else @Suppress("DEPRECATION") pm.getInstallerPackageName(packageName)
            }.getOrNull(),
            activities = info.activities.orEmpty().map { Component(it.name, it.exported, it.enabled, it.permission, it.processName) },
            services = info.services.orEmpty().map { Component(it.name, it.exported, it.enabled, it.permission, it.processName) },
            receivers = info.receivers.orEmpty().map { Component(it.name, it.exported, it.enabled, it.permission, it.processName) },
            providers = info.providers.orEmpty().map {
                ContentProvider(it.name, it.authority ?: "", it.exported, it.readPermission, it.writePermission, it.grantUriPermissions)
            },
            permissions = permissions,
            definedPermissions = info.permissions.orEmpty().map { it.name },
            signatures = signatures,
            launchable = pm.getLaunchIntentForPackage(packageName) != null,
        )
    }

    fun launchIntent(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)

    fun appSettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null))

    fun applicationInfo(packageName: String): ApplicationInfo? =
        runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()

    private fun storageOf(app: ApplicationInfo): AppStorage? = runCatching {
        val manager = context.getSystemService(StorageStatsManager::class.java)
        val uuid = app.storageUuid ?: StorageManager.UUID_DEFAULT
        val stats = manager.queryStatsForPackage(uuid, app.packageName, UserHandle.getUserHandleForUid(app.uid))
        AppStorage(stats.appBytes, stats.dataBytes, stats.cacheBytes)
    }.getOrNull()

    private fun summary(info: PackageInfo, app: ApplicationInfo, storage: AppStorage?): AppSummary = AppSummary(
        packageName = info.packageName,
        label = pm.getApplicationLabel(app).toString(),
        versionName = info.versionName,
        versionCode = info.longVersionCode,
        isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
        isDebuggable = app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        isEnabled = app.enabled,
        targetSdk = app.targetSdkVersion,
        uid = app.uid,
        firstInstallTime = info.firstInstallTime,
        lastUpdateTime = info.lastUpdateTime,
        apkPath = app.publicSourceDir ?: app.sourceDir ?: "",
        storage = storage,
    )

    /** Needs QUERY_ALL_PACKAGES in the app manifest to see every package. */
    @SuppressLint("QueryPermissionsNeeded")
    @Suppress("DEPRECATION")
    private fun installedPackages(flags: Int): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledPackages(flags)
        }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String, flags: Int): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }
    }.getOrNull()
}
