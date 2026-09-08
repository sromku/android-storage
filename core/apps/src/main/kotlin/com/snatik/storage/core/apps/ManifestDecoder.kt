package com.snatik.storage.core.apps

import android.content.Context
import android.content.res.Resources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/** Reads the manifest out of an installed package's APK and decodes it to text. */
class ManifestDecoder(private val context: Context) {

    suspend fun decode(packageName: String, apkPath: String): String = withContext(Dispatchers.IO) {
        val bytes = ZipFile(File(apkPath)).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: error("No AndroidManifest.xml in $apkPath")
            zip.getInputStream(entry).use { it.readBytes() }
        }
        val resources = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull()
        val android = Resources.getSystem()
        BinaryXml(bytes) { id ->
            val res = if (id ushr 24 == 0x01) android else resources
            runCatching { res?.getResourceName(id) }.getOrNull()?.let { name ->
                // "pkg:type/name" -> "type/name", keep "android:" for framework resources.
                val (pkg, rest) = name.split(':', limit = 2).let { if (it.size == 2) it[0] to it[1] else "" to it[0] }
                if (pkg == "android") "android:$rest" else rest
            }
        }.decode()
    }
}
