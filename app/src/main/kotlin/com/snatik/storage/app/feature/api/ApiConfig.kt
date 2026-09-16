package com.snatik.storage.app.feature.api

import android.content.Context
import androidx.core.content.edit
import com.snatik.storage.core.net.TransferServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ApiSettings(
    val enabled: Boolean = false,
    /** Allow shell-tier operations (logcat, shell, privileged provider queries). */
    val allowPrivileged: Boolean = false,
    /** Allow operations that change the device (write, delete, send intents, SQL writes). */
    val allowDestructive: Boolean = false,
    val token: String = "",
)

/** API on/off, the safety gates and the bearer token, persisted. */
class ApiConfig(context: Context) {

    private val prefs = context.getSharedPreferences("api", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        ApiSettings(
            enabled = prefs.getBoolean("enabled", false),
            allowPrivileged = prefs.getBoolean("privileged", false),
            allowDestructive = prefs.getBoolean("destructive", false),
            token = prefs.getString("token", null) ?: TransferServer.newCode() + TransferServer.newCode(),
        ),
    )
    val settings: StateFlow<ApiSettings> = _settings.asStateFlow()

    init {
        if (!prefs.contains("token")) prefs.edit { putString("token", _settings.value.token) }
    }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }
    fun setPrivileged(on: Boolean) = update { it.copy(allowPrivileged = on) }
    fun setDestructive(on: Boolean) = update { it.copy(allowDestructive = on) }
    fun regenerateToken() = update { it.copy(token = TransferServer.newCode() + TransferServer.newCode()) }

    private fun update(block: (ApiSettings) -> ApiSettings) {
        _settings.update(block)
        val s = _settings.value
        prefs.edit {
            putBoolean("enabled", s.enabled)
            putBoolean("privileged", s.allowPrivileged)
            putBoolean("destructive", s.allowDestructive)
            putString("token", s.token)
        }
    }
}

data class AuditEntry(
    val time: Long,
    val tool: String,
    val transport: String,
    val summary: String,
    val allowed: Boolean,
    val error: String? = null,
    /** The full arguments the caller sent, as JSON, for the detail view. */
    val args: String = "",
)

/** In-memory record of every API call, shown in the API screen. */
class AuditLog {
    private val _entries = MutableStateFlow<List<AuditEntry>>(emptyList())
    val entries: StateFlow<List<AuditEntry>> = _entries.asStateFlow()

    fun record(entry: AuditEntry) = _entries.update { (listOf(entry) + it).take(300) }
    fun clear() { _entries.value = emptyList() }
}
