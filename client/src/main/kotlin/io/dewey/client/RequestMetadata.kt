package io.dewey.client

import io.ktor.http.*
import kotlin.reflect.KType

class RequestMetadata<T>(
    val path: String,
    val method: HttpMethod,
    val headers: Headers,
    val body: T,
    val shouldLogin: Boolean,
    val securitySchema: String?,
    val queryParameters: Map<String, Any?> = emptyMap(),
)
