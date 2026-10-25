package io.dewey.auth.models

import kotlinx.serialization.Serializable

@Serializable
open class JWK(
    open val kty: String,
    open val use: String,
    open val kid: String,
    open val n: String,
    open val e: String,
)
