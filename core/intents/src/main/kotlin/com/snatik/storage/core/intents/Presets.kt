package com.snatik.storage.core.intents

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class IntentPreset(val id: Long, val name: String, val spec: IntentSpec)

/** Saved intents, stored as one JSON file. */
class IntentPresets(context: Context) {

    private val file = File(context.filesDir, "intent-presets.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val serializer = ListSerializer(IntentPreset.serializer())

    private val _presets = MutableStateFlow<List<IntentPreset>>(emptyList())
    val presets: StateFlow<List<IntentPreset>> = _presets.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (file.exists()) _presets.value = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    suspend fun save(name: String, spec: IntentSpec, id: Long? = null): IntentPreset = withContext(Dispatchers.IO) {
        val preset = IntentPreset(id ?: System.currentTimeMillis(), name, spec)
        _presets.value = _presets.value.filterNot { it.id == preset.id } + preset
        persist()
        preset
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        _presets.value = _presets.value.filterNot { it.id == id }
        persist()
    }

    private fun persist() = file.writeText(json.encodeToString(serializer, _presets.value))
}
