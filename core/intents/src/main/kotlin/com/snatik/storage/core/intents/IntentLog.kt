package com.snatik.storage.core.intents

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CapturedIntent(
    val id: Long,
    val time: Long,
    val referrer: String?,
    val clipDescription: String?,
    val spec: IntentSpec,
)

/** Intents the sink activity received, newest first, kept on disk as JSON lines. */
class IntentLog(context: Context) {

    private val file = File(context.filesDir, "intent-log.jsonl")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _entries = MutableStateFlow<List<CapturedIntent>>(emptyList())
    val entries: StateFlow<List<CapturedIntent>> = _entries.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        val loaded = file.readLines().mapNotNull { line -> runCatching { json.decodeFromString(CapturedIntent.serializer(), line) }.getOrNull() }
        _entries.value = loaded.sortedByDescending { it.time }
    }

    suspend fun add(spec: IntentSpec, referrer: String?, clipDescription: String?): CapturedIntent = withContext(Dispatchers.IO) {
        val entry = CapturedIntent(id = System.nanoTime(), time = System.currentTimeMillis(), referrer = referrer, clipDescription = clipDescription, spec = spec)
        file.appendText(json.encodeToString(CapturedIntent.serializer(), entry) + "\n")
        _entries.update { (listOf(entry) + it).take(MAX) }
        entry
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        _entries.value = emptyList()
    }

    private companion object {
        const val MAX = 500
    }
}
