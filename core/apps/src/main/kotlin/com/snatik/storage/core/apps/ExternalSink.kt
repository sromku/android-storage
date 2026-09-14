package com.snatik.storage.core.apps

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional external collector for monitoring tools: streams records to a user-run endpoint (e.g. a
 * SQLite-backed receiver on a laptop) over HTTP as newline-delimited JSON, so history can be kept
 * with no on-device row cap. App-wide (configured once in Settings) and usable by any monitor tool.
 */
class ExternalSink(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("external_sink", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _url = MutableStateFlow(prefs.getString(KEY_URL, "").orEmpty())
    val url: StateFlow<String> = _url.asStateFlow()

    /** True only when turned on AND a URL is set — the recorder checks this before each send. */
    val enabled: Boolean get() = _enabled.value && _url.value.isNotBlank()

    fun setEnabled(value: Boolean) { _enabled.value = value; prefs.edit().putBoolean(KEY_ENABLED, value).apply() }
    fun setUrl(value: String) { _url.value = value.trim(); prefs.edit().putString(KEY_URL, value.trim()).apply() }

    /**
     * POST a batch of records as NDJSON, tagged with [tool] (which the collector uses as the table
     * name). Any monitor can call this. Throws on network/HTTP failure so callers can report it.
     */
    suspend fun send(tool: String, records: List<JSONObject>) {
        if (!enabled || records.isEmpty()) return
        val body = buildString {
            for (o in records) {
                append(o.put("tool", tool).toString())
                append('\n')
            }
        }
        post(body)
    }

    /** Convenience for the app-ops recorder: serialize accesses and send them under the "appops" tool. */
    suspend fun sendAppOps(accesses: List<AppOpAccess>, pollTimeMs: Long) {
        if (!enabled || accesses.isEmpty()) return
        send("appops", accesses.map { a ->
            JSONObject()
                .put("package", a.packageName)
                .put("op", a.op)
                .put("at_ms", (pollTimeMs - a.agoMs).coerceAtLeast(0))
                .put("state", a.state.name)
                .put("duration_ms", a.durationMs ?: JSONObject.NULL)
                .put("denied", a.denied)
                .put("sensitive", a.sensitive)
                .put("key", a.key)
        })
    }

    /** Send a tiny probe so Settings can confirm the endpoint is reachable. */
    suspend fun test(): Result<Unit> = runCatching {
        post(JSONObject().put("tool", "ping").put("at_ms", System.currentTimeMillis()).toString() + "\n")
    }

    private suspend fun post(body: String) = withContext(Dispatchers.IO) {
        val conn = (URL(_url.value).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Content-Type", "application/x-ndjson")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_URL = "url"
    }
}
