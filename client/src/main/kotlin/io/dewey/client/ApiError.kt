package io.dewey.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class ApiErrorType {
    HTTP,
    Connection,
    ClientSerialization,
    Unknown,
}

@Serializable
open class InternalApiError(
    val status: Int,
    val error: String,
    val details: JsonElement? = null,
    val body: String? = null,
)

class ApiError(
    val type: ApiErrorType,
    status: Int,
    error: String,
    details: JsonElement? = null,
    body: String? = null,
) : InternalApiError(status, error, details, body) {
    constructor(type: ApiErrorType, error: InternalApiError) : this(
        type,
        error.status,
        error.error,
        error.details,
        error.body,
    )

    override fun toString(): String = "ApiError(type=$type, status=$status, error=$error, details=$details, body=$body)"
}

fun InternalApiError.wrap(type: ApiErrorType): ApiError = ApiError(type, this)
