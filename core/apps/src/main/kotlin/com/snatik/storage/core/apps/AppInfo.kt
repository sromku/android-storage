package com.snatik.storage.core.apps

/** Storage taken by one package, from StorageStatsManager. Needs usage access. */
data class AppStorage(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

/** What the app list needs per package. */
data class AppSummary(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val isDebuggable: Boolean,
    val isEnabled: Boolean,
    val targetSdk: Int,
    val uid: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val apkPath: String,
    val storage: AppStorage?,
)

data class Component(
    val name: String,
    val exported: Boolean,
    val enabled: Boolean,
    val permission: String?,
    val process: String?,
)

data class ContentProvider(
    val name: String,
    val authority: String,
    val exported: Boolean,
    val readPermission: String?,
    val writePermission: String?,
    val grantUriPermissions: Boolean,
)

data class RequestedPermission(val name: String, val granted: Boolean, val isRuntime: Boolean)

data class SigningCertificate(val sha256: String, val subject: String)

data class AppDetails(
    val summary: AppSummary,
    val minSdk: Int,
    val dataDir: String,
    val deviceProtectedDataDir: String?,
    val externalDataDir: String?,
    val nativeLibraryDir: String?,
    val splitApks: List<String>,
    val installer: String?,
    val activities: List<Component>,
    val services: List<Component>,
    val receivers: List<Component>,
    val providers: List<ContentProvider>,
    val permissions: List<RequestedPermission>,
    val definedPermissions: List<String>,
    val signatures: List<SigningCertificate>,
    val launchable: Boolean,
)
