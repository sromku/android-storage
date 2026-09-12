package com.snatik.storage.app.feature.data

import android.content.Context
import com.snatik.storage.core.data.ProviderShortcut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

/**
 * User-saved content-provider URIs, shown on the Data → Quick queries tab so a URI worth returning
 * to (a discovered path, a hand-built query) doesn't have to be researched again. Backed by a small
 * JSON file; the built-in shortcuts live in [com.snatik.storage.core.data.ProviderRepository].
 */
class SavedQueryStore(context: Context, private val scope: CoroutineScope) {

    @Serializable
    data class SavedQuery(val title: String, val uri: String, val group: String = DEFAULT_GROUP)

    private val file = File(context.filesDir, "saved_queries.json")

    private val _saved = MutableStateFlow(load())
    val saved: StateFlow<List<SavedQuery>> = _saved.asStateFlow()

    fun isSaved(uri: String): Boolean = _saved.value.any { it.uri == uri }

    fun save(title: String, uri: String, group: String = DEFAULT_GROUP) {
        if (isSaved(uri)) return
        _saved.update { it + SavedQuery(title, uri, group) }
        persist()
    }

    fun remove(uri: String) {
        _saved.update { list -> list.filterNot { it.uri == uri } }
        persist()
    }

    fun asShortcuts(): List<ProviderShortcut> = _saved.value.map { ProviderShortcut(it.title, it.uri, it.group) }

    private fun load(): List<SavedQuery> = runCatching {
        if (file.exists()) json.decodeFromString<List<SavedQuery>>(file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    private fun persist() {
        val snapshot = _saved.value
        scope.launch(Dispatchers.IO) { runCatching { file.writeText(json.encodeToString(snapshot)) } }
    }

    companion object {
        const val DEFAULT_GROUP = "Saved"
    }
}

/**
 * Caches URI-discovery results per provider so a provider that's already been scanned opens instantly
 * with its previously found paths and the time it was last discovered, instead of decompiling again.
 * A re-discover overwrites the record.
 */
class UriDiscoveryStore(context: Context, private val scope: CoroutineScope) {

    @Serializable
    data class Record(
        val className: String,
        val authority: String,
        val paths: List<String>,
        val discoveredAt: Long,
        val scannedAll: Boolean,
        /** Identifier-like literals (columns/keys/tables) scraped from the provider's classes. */
        val hints: List<String> = emptyList(),
    )

    private val file = File(context.filesDir, "uri_discovery.json")
    private val records = MutableStateFlow(load().associateBy { key(it.className, it.authority) })

    fun get(className: String, authority: String): Record? = records.value[key(className, authority)]

    fun put(record: Record) {
        records.update { it + (key(record.className, record.authority) to record) }
        persist()
    }

    private fun key(className: String, authority: String) = "$className|$authority"

    private fun load(): List<Record> = runCatching {
        if (file.exists()) json.decodeFromString<List<Record>>(file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    private fun persist() {
        val snapshot = records.value.values.toList()
        scope.launch(Dispatchers.IO) { runCatching { file.writeText(json.encodeToString(snapshot)) } }
    }
}
