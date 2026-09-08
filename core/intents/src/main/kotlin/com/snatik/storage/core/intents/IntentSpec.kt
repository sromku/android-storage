package com.snatik.storage.core.intents

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ExtraType { STRING, INT, LONG, FLOAT, DOUBLE, BOOLEAN, URI, STRING_ARRAY }

@Serializable
data class Extra(val key: String, val type: ExtraType, val value: String)

enum class SendAs { ACTIVITY, BROADCAST, SERVICE }

/** A plain description of an Intent that survives JSON, for presets and logs. */
@Serializable
data class IntentSpec(
    val action: String? = null,
    val data: String? = null,
    val type: String? = null,
    val categories: List<String> = emptyList(),
    val packageName: String? = null,
    val className: String? = null,
    val flags: Int = 0,
    val extras: List<Extra> = emptyList(),
    val sendAs: SendAs = SendAs.ACTIVITY,
) {
    fun toIntent(): Intent {
        val intent = Intent()
        action?.takeIf { it.isNotBlank() }?.let { intent.action = it.trim() }
        val uri = data?.takeIf { it.isNotBlank() }?.let { Uri.parse(it.trim()) }
        val mime = type?.takeIf { it.isNotBlank() }?.trim()
        when {
            uri != null && mime != null -> intent.setDataAndType(uri, mime)
            uri != null -> intent.data = uri
            mime != null -> intent.type = mime
        }
        categories.filter { it.isNotBlank() }.forEach { intent.addCategory(it.trim()) }
        val pkg = packageName?.takeIf { it.isNotBlank() }?.trim()
        val cls = className?.takeIf { it.isNotBlank() }?.trim()
        when {
            pkg != null && cls != null -> intent.component = ComponentName(pkg, if (cls.startsWith(".")) pkg + cls else cls)
            pkg != null -> intent.setPackage(pkg)
        }
        intent.addFlags(flags)
        for (extra in extras) {
            val key = extra.key
            when (extra.type) {
                ExtraType.STRING -> intent.putExtra(key, extra.value)
                ExtraType.INT -> intent.putExtra(key, extra.value.trim().toIntOrNull() ?: 0)
                ExtraType.LONG -> intent.putExtra(key, extra.value.trim().toLongOrNull() ?: 0L)
                ExtraType.FLOAT -> intent.putExtra(key, extra.value.trim().toFloatOrNull() ?: 0f)
                ExtraType.DOUBLE -> intent.putExtra(key, extra.value.trim().toDoubleOrNull() ?: 0.0)
                ExtraType.BOOLEAN -> intent.putExtra(key, extra.value.trim().equals("true", ignoreCase = true))
                ExtraType.URI -> intent.putExtra(key, Uri.parse(extra.value.trim()))
                ExtraType.STRING_ARRAY -> intent.putExtra(key, extra.value.split('\n').toTypedArray())
            }
        }
        return intent
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(text: String): IntentSpec = json.decodeFromString(serializer(), text)

        /** Describe a received intent. Extras are rendered to strings since their types are open ended. */
        fun describe(intent: Intent): IntentSpec = IntentSpec(
            action = intent.action,
            data = intent.dataString,
            type = intent.type,
            categories = intent.categories?.toList().orEmpty(),
            packageName = intent.component?.packageName ?: intent.`package`,
            className = intent.component?.className,
            flags = intent.flags,
            extras = describeExtras(intent.extras),
        )

        fun describeExtras(bundle: Bundle?): List<Extra> {
            if (bundle == null) return emptyList()
            return bundle.keySet().sorted().map { key ->
                @Suppress("DEPRECATION")
                val value = bundle.get(key)
                val type = when (value) {
                    is Int -> ExtraType.INT
                    is Long -> ExtraType.LONG
                    is Float -> ExtraType.FLOAT
                    is Double -> ExtraType.DOUBLE
                    is Boolean -> ExtraType.BOOLEAN
                    is Uri -> ExtraType.URI
                    is Array<*> -> ExtraType.STRING_ARRAY
                    else -> ExtraType.STRING
                }
                val text = when (value) {
                    null -> "null"
                    is Array<*> -> value.joinToString("\n") { it.toString() }
                    is Bundle -> describeExtras(value).joinToString("\n") { "${it.key}=${it.value}" }
                    else -> value.toString()
                }
                Extra(key, type, text)
            }
        }
    }
}

/** Human readable flag names for the bits set in [flags]. */
fun intentFlagNames(flags: Int): List<String> = KNOWN_FLAGS.filter { flags and it.second != 0 }.map { it.first }

val KNOWN_FLAGS: List<Pair<String, Int>> = listOf(
    "NEW_TASK" to Intent.FLAG_ACTIVITY_NEW_TASK,
    "CLEAR_TOP" to Intent.FLAG_ACTIVITY_CLEAR_TOP,
    "SINGLE_TOP" to Intent.FLAG_ACTIVITY_SINGLE_TOP,
    "CLEAR_TASK" to Intent.FLAG_ACTIVITY_CLEAR_TASK,
    "NO_HISTORY" to Intent.FLAG_ACTIVITY_NO_HISTORY,
    "EXCLUDE_FROM_RECENTS" to Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
    "MULTIPLE_TASK" to Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
    "GRANT_READ_URI" to Intent.FLAG_GRANT_READ_URI_PERMISSION,
    "GRANT_WRITE_URI" to Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    "INCLUDE_STOPPED_PACKAGES" to Intent.FLAG_INCLUDE_STOPPED_PACKAGES,
    "DEBUG_LOG_RESOLUTION" to Intent.FLAG_DEBUG_LOG_RESOLUTION,
)

/** Actions offered as suggestions in the builder. */
val COMMON_ACTIONS: List<String> = listOf(
    Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_MAIN, Intent.ACTION_EDIT,
    Intent.ACTION_PICK, Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_CREATE_DOCUMENT,
    Intent.ACTION_DIAL, Intent.ACTION_CALL, Intent.ACTION_SENDTO, Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH,
    Intent.ACTION_INSERT, Intent.ACTION_DELETE, Intent.ACTION_CHOOSER, Intent.ACTION_APPLICATION_PREFERENCES,
    "android.settings.SETTINGS", "android.settings.APPLICATION_DETAILS_SETTINGS", "android.settings.WIFI_SETTINGS",
    "android.settings.BLUETOOTH_SETTINGS", "android.settings.DEVELOPMENT_SETTINGS", "android.settings.LOCALE_SETTINGS",
    "android.media.action.IMAGE_CAPTURE", "android.media.action.VIDEO_CAPTURE",
    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_TIME_TICK,
)
