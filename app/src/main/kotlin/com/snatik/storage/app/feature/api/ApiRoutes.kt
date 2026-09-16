package com.snatik.storage.app.feature.api

import com.snatik.storage.core.net.TransferServer
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Mounts the REST endpoints and the MCP endpoint on the shared server. Both require the API to be
 * enabled and the bearer token to match, and both run the same [ApiOperations].
 */
class ApiService(private val operations: ApiOperations, private val config: ApiConfig, private val audit: AuditLog) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mcp = McpServer(operations) { audit.record(it) }

    val routes: Route.(TransferServer) -> Unit = {
        route("/api/v1") {
            get("/tools") {
                if (!ok(call)) return@get unauthorized(call)
                call.respondText(json.encodeToString(buildJsonObject {
                    put("tools", kotlinx.serialization.json.buildJsonArray {
                        operations.all.forEach { op -> add(buildJsonObject { put("name", op.name); put("description", op.description); put("privileged", op.privileged); put("destructive", op.destructive); put("inputSchema", op.schema) }) }
                    })
                }), ContentType.Application.Json)
            }
            post("/{op}") {
                if (!ok(call)) return@post unauthorized(call)
                val name = call.parameters["op"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val body = call.receiveText().takeIf { it.isNotBlank() }?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: JsonObject(emptyMap())
                runOp(call, name, body, "rest")
            }
            get("/{op}") {
                if (!ok(call)) return@get unauthorized(call)
                val name = call.parameters["op"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val args = buildJsonObject { call.request.queryParameters.entries().forEach { (k, v) -> if (k != "token") put(k, v.first()) } }
                runOp(call, name, args, "rest")
            }
        }
        post("/mcp") {
            if (!ok(call)) return@post call.respond(HttpStatusCode.Unauthorized)
            val text = call.receiveText()
            val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val response = mcp.handle(message)
            if (response == null) call.respond(HttpStatusCode.Accepted) else call.respondText(json.encodeToString(response), ContentType.Application.Json)
        }
    }

    private suspend fun runOp(call: ApplicationCall, name: String, args: JsonObject, transport: String) {
        val argsJson = runCatching { json.encodeToString(args) }.getOrDefault("{}")
        try {
            val result = operations.call(name, args)
            audit.record(AuditEntry(System.currentTimeMillis(), name, transport, args.keys.joinToString(","), allowed = true, args = argsJson))
            call.respondText(json.encodeToString(result), ContentType.Application.Json)
        } catch (e: ApiException) {
            audit.record(AuditEntry(System.currentTimeMillis(), name, transport, args.keys.joinToString(","), allowed = false, error = e.message, args = argsJson))
            call.respondText(json.encodeToString(buildJsonObject { put("error", e.message ?: "error") }), ContentType.Application.Json, e.status)
        }
    }

    private fun ok(call: ApplicationCall): Boolean {
        if (!config.settings.value.enabled) return false
        val token = config.settings.value.token
        val presented = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim() ?: call.request.header("X-Api-Token") ?: call.request.queryParameters["token"]
        return token.isNotEmpty() && presented == token
    }

    private suspend fun unauthorized(call: ApplicationCall) = call.respondText(
        json.encodeToString(buildJsonObject { put("error", if (config.settings.value.enabled) "unauthorized" else "API is off") }),
        ContentType.Application.Json,
        HttpStatusCode.Unauthorized,
    )
}
