package com.snatik.storage.app.feature.api

import io.ktor.http.HttpStatusCode

/** An API failure with an HTTP status and a JSON-RPC error code. */
class ApiException(val status: HttpStatusCode, val rpcCode: Int, message: String) : Exception(message) {
    companion object {
        fun badRequest(message: String) = ApiException(HttpStatusCode.BadRequest, -32602, message)
        fun forbidden(message: String) = ApiException(HttpStatusCode.Forbidden, -32000, message)
        fun notFound(message: String) = ApiException(HttpStatusCode.NotFound, -32001, message)
        fun failed(message: String) = ApiException(HttpStatusCode.InternalServerError, -32002, message)
    }
}
