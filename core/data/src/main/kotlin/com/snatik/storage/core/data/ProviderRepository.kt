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
) {
    val uri: String get() = "content://$authority"
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
        packages.flatMap { info ->
            val label = info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: info.packageName
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
                    )
                }
            }
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.authority }))
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
