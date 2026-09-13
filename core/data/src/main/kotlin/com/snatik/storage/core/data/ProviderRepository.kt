package com.snatik.storage.core.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProviderEntry(
    val authority: String,
    val packageName: String,
    val appLabel: String,
    val className: String,
    val exported: Boolean,
    val readPermission: String?,
    val writePermission: String?,
    val grantUriPermissions: Boolean,
    val pathPermissions: List<String>,
    val isSystem: Boolean,
    /** Whether this app can read the provider right now (own provider, or exported with no/held read perm). */
    val queryableNow: Boolean,
) {
    val uri: String get() = "content://$authority"
    val hasPermission: Boolean get() = readPermission != null || writePermission != null
}

data class ProviderShortcut(val title: String, val uri: String, val group: String)

/** Every content provider declared on the device, plus the well-known system URIs. */
class ProviderRepository(private val context: Context) {

    @SuppressLint("QueryPermissionsNeeded")
    @Suppress("DEPRECATION")
    suspend fun list(): List<ProviderEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PROVIDERS.toLong()))
        } else {
            pm.getInstalledPackages(PackageManager.GET_PROVIDERS)
        }
        val ownPkg = context.packageName
        packages.flatMap { info ->
            val appInfo = info.applicationInfo
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: info.packageName
            val system = appInfo != null &&
                (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            info.providers.orEmpty().flatMap { p ->
                val authorities = p.authority?.split(';')?.filter { it.isNotBlank() } ?: emptyList()
                authorities.map { authority ->
                    ProviderEntry(
                        authority = authority,
                        packageName = info.packageName,
                        appLabel = label,
                        className = p.name,
                        exported = p.exported,
                        readPermission = p.readPermission,
                        writePermission = p.writePermission,
                        grantUriPermissions = p.grantUriPermissions,
                        pathPermissions = p.pathPermissions.orEmpty().map { pp ->
                            listOfNotNull(pp.path, pp.readPermission?.let { "r:$it" }, pp.writePermission?.let { "w:$it" }).joinToString(" ")
                        },
                        isSystem = system,
                        queryableNow = canRead(info.packageName == ownPkg, p.exported, p.readPermission),
                    )
                }
            }
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.authority }))
    }

    /** The single provider that owns [authority], or null if no installed app declares it. */
    @Suppress("DEPRECATION")
    suspend fun resolve(authority: String): ProviderEntry? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val p = pm.resolveContentProvider(authority, 0) ?: return@withContext null
        val appInfo = p.applicationInfo
        val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: p.packageName
        val system = appInfo != null &&
            (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        ProviderEntry(
            authority = authority,
            packageName = p.packageName,
            appLabel = label,
            className = p.name,
            exported = p.exported,
            readPermission = p.readPermission,
            writePermission = p.writePermission,
            grantUriPermissions = p.grantUriPermissions,
            pathPermissions = p.pathPermissions.orEmpty().map { pp ->
                listOfNotNull(pp.path, pp.readPermission?.let { "r:$it" }, pp.writePermission?.let { "w:$it" }).joinToString(" ")
            },
            isSystem = system,
            queryableNow = canRead(p.packageName == context.packageName, p.exported, p.readPermission),
        )
    }

    /** App-side readability heuristic (ignores the shell fallback): own provider, or exported with a read perm we hold. */
    private fun canRead(own: Boolean, exported: Boolean, readPermission: String?): Boolean {
        if (own) return true
        if (!exported) return false
        if (readPermission == null) return true
        return context.checkSelfPermission(readPermission) == PackageManager.PERMISSION_GRANTED
    }

    fun shortcuts(): List<ProviderShortcut> = listOfNotNull(
        ProviderShortcut("Files", MediaStore.Files.getContentUri("external").toString(), "MediaStore"),
        ProviderShortcut("Images", MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString(), "MediaStore"),
        ProviderShortcut("Video", MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString(), "MediaStore"),
        ProviderShortcut("Audio", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString(), "MediaStore"),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ProviderShortcut("Downloads", MediaStore.Downloads.EXTERNAL_CONTENT_URI.toString(), "MediaStore") else null,
        ProviderShortcut("Contacts", ContactsContract.Contacts.CONTENT_URI.toString(), "People"),
        ProviderShortcut("Phone numbers", ContactsContract.CommonDataKinds.Phone.CONTENT_URI.toString(), "People"),
        ProviderShortcut("Call log", CallLog.Calls.CONTENT_URI.toString(), "People"),
        ProviderShortcut("SMS", Telephony.Sms.CONTENT_URI.toString(), "People"),
        ProviderShortcut("Calendars", CalendarContract.Calendars.CONTENT_URI.toString(), "Calendar"),
        ProviderShortcut("Events", CalendarContract.Events.CONTENT_URI.toString(), "Calendar"),
        ProviderShortcut("Settings · system", Settings.System.CONTENT_URI.toString(), "Settings"),
        ProviderShortcut("Settings · secure", Settings.Secure.CONTENT_URI.toString(), "Settings"),
        ProviderShortcut("Settings · global", Settings.Global.CONTENT_URI.toString(), "Settings"),
    )
}
