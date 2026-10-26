package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class JWK(
    val e: String,
    val kid: String,
    val kty: String,
    val n: String,
    val use: String,
)
