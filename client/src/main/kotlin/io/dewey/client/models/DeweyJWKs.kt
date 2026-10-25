package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class DeweyJWKs(
    val keys: List<JWK>,
)
