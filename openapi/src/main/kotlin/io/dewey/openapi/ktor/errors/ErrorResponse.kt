package io.dewey.openapi.ktor.errors

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonElement
import kotlin.uuid.Uuid

class ErrorResponse private constructor(
    val status: HttpStatusCode,
    val error: String,
    val details: JsonElement? = null,
) : Exception(error) {
    companion object {
        fun notFound(
            thing: String,
            id: Uuid,
            details: JsonElement? = null,
        ): ErrorResponse = notFound(thing, id.toString(), details)

        fun notFound(
            type: String,
            actual: String,
            details: JsonElement? = null,
        ): ErrorResponse = ErrorResponse(HttpStatusCode.NotFound, "$type '$actual' not found", details)

        fun missing(
            error: String,
            details: JsonElement? = null,
        ): ErrorResponse = ErrorResponse(HttpStatusCode.NotFound, error, details)

        fun userError(
            error: String,
            details: JsonElement? = null,
        ): ErrorResponse = ErrorResponse(HttpStatusCode.BadRequest, error, details)

        fun unauthorized(
            error: String,
            details: JsonElement? = null,
        ): ErrorResponse = ErrorResponse(HttpStatusCode.Unauthorized, error, details)

        fun forbidden(
            action: String,
            resource: String,
            details: JsonElement? = null,
        ): ErrorResponse = ErrorResponse(HttpStatusCode.Forbidden, "Forbidden to $action $resource", details)

        fun notImplemented(
            error: String,
            details: JsonElement? = null,
        ): ErrorResponse = ErrorResponse(HttpStatusCode.NotImplemented, error, details)

        fun internalError(
            error: String,
            details: JsonElement? = null,
        ): ErrorResponse = ErrorResponse(HttpStatusCode.InternalServerError, error, details)
    }
}
