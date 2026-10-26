package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class RevealAccessToken(
    val password: String,
)
