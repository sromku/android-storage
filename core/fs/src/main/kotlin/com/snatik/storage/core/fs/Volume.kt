package com.snatik.storage.core.fs

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class VolumeKind { SHARED, SD_CARD, USB, APP_FILES, APP_CACHE, APP_EXTERNAL, SYSTEM_ROOT }

data class Volume(
    val kind: VolumeKind,
    /** Label from the system for removable media, empty otherwise so the UI can localise. */
    val systemLabel: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isAvailable: Boolean,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes).toFloat() else 0f
}

/** Discovers the storage volumes and app directories worth browsing. */
class VolumeRepository(private val context: Context, private val includeSystemRoot: Boolean = false) {

    suspend fun volumes(): List<Volume> = withContext(Dispatchers.IO) {
        buildList {
            addAll(mediaVolumes())
            // The system root only becomes browsable with shell or root access; add it back with the privilege layer.
            if (includeSystemRoot) add(volume(VolumeKind.SYSTEM_ROOT, "", File("/")))
        }
    }

    suspend fun appVolumes(): List<Volume> = withContext(Dispatchers.IO) {
        buildList {
            add(volume(VolumeKind.APP_FILES, "", context.filesDir))
            add(volume(VolumeKind.APP_CACHE, "", context.cacheDir))
            context.getExternalFilesDir(null)?.let { add(volume(VolumeKind.APP_EXTERNAL, "", it)) }
        }
    }

    private fun mediaVolumes(): List<Volume> {
        val manager = context.getSystemService(StorageManager::class.java)
        val volumes = manager.storageVolumes
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volumes.mapNotNull { v ->
                val dir = v.directory ?: return@mapNotNull null
                volume(kindOf(v), if (v.isPrimary) "" else v.getDescription(context), dir, v.state == Environment.MEDIA_MOUNTED)
            }
        } else {
            // Before Android 11 the volume path is not public; derive it from our external files dirs.
            context.getExternalFilesDirs(null).filterNotNull().mapIndexed { index, dir ->
                val root = File(dir.absolutePath.substringBefore("/Android/data/"))
                val kind = if (index == 0) VolumeKind.SHARED else VolumeKind.SD_CARD
                volume(kind, if (index == 0) "" else root.name, root, Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED)
            }
        }
    }

    private fun kindOf(v: StorageVolume): VolumeKind = when {
        v.isPrimary -> VolumeKind.SHARED
        v.getDescription(context).contains("usb", ignoreCase = true) -> VolumeKind.USB
        v.isRemovable -> VolumeKind.SD_CARD
        else -> VolumeKind.SHARED
    }

    private fun volume(kind: VolumeKind, label: String, dir: File, mounted: Boolean = true): Volume {
        val available = mounted && dir.exists()
        val stats = if (available) runCatching { StatFs(dir.absolutePath) }.getOrNull() else null
        return Volume(
            kind = kind,
            systemLabel = label,
            path = dir.absolutePath,
            totalBytes = stats?.totalBytes ?: 0,
            freeBytes = stats?.availableBytes ?: 0,
            isAvailable = available,
        )
    }
}
