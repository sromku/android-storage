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

// Flag and action catalogs live in IntentCatalog.kt.
