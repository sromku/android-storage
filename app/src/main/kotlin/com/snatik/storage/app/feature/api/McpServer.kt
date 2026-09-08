package com.snatik.storage.app.feature.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A minimal Model Context Protocol server over one JSON-RPC message. It answers initialize,
 * tools/list and tools/call, mapping every tool to an [ApiOperations] call. Notifications get
 * no response, which the transport turns into 202.
 */
class McpServer(private val operations: ApiOperations, private val onCall: (AuditEntry) -> Unit) {

    private val protocolVersion = "2025-06-18"

    /** Returns the response object, or null for a notification. */
    suspend fun handle(message: JsonObject): JsonObject? {
        val id = message["id"]
        val method = message["method"]?.jsonPrimitive?.content
        if (id == null || id is JsonNull) return null // notification, e.g. notifications/initialized
        return when (method) {
            "initialize" -> reply(id, buildJsonObject {
                put("protocolVersion", protocolVersion)
                put("capabilities", buildJsonObject { put("tools", buildJsonObject { }) })
                put("serverInfo", buildJsonObject { put("name", "android-storage"); put("version", "0.1.0") })
            })
            "ping" -> reply(id, buildJsonObject { })
            "tools/list" -> reply(id, buildJsonObject {
                put("tools", buildJsonArray {
                    operations.all.forEach { op ->
                        add(buildJsonObject {
                            put("name", op.name)
                            val gates = buildList { if (op.privileged) add("shell"); if (op.destructive) add("changes the device") }
                            put("description", op.description + if (gates.isEmpty()) "" else " (needs: ${gates.joinToString(", ")})")
                            put("inputSchema", op.schema)
                        })
                    }
                })
            })
            "tools/call" -> {
                val params = message["params"]?.jsonObject ?: return error(id, -32602, "Missing params")
                val name = params["name"]?.jsonPrimitive?.content ?: return error(id, -32602, "Missing tool name")
                val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())
                try {
                    val result = operations.call(name, arguments)
                    onCall(AuditEntry(System.currentTimeMillis(), name, "mcp", summarize(arguments), allowed = true))
                    reply(id, buildJsonObject {
                        put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", result.toString()) }) })
                        put("isError", false)
                    })
                } catch (e: ApiException) {
                    onCall(AuditEntry(System.currentTimeMillis(), name, "mcp", summarize(arguments), allowed = false, error = e.message))
                    // MCP convention: tool errors are results with isError, not protocol errors.
                    reply(id, buildJsonObject {
                        put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", e.message ?: "error") }) })
                        put("isError", true)
                    })
                }
            }
            else -> error(id, -32601, "Method not found: $method")
        }
    }

    private fun reply(id: JsonElement, result: JsonElement) = buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }
    private fun error(id: JsonElement, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id); put("error", buildJsonObject { put("code", code); put("message", message) })
    }

    private fun summarize(arguments: JsonObject): String = arguments.entries.take(3).joinToString(", ") { (k, v) -> "$k=${v.jsonPrimitive.contentOrNull().take(40)}" }
    private fun JsonPrimitive.contentOrNull() = runCatching { content }.getOrDefault("")
}
