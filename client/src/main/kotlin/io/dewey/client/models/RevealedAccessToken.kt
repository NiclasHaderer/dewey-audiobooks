package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class RevealedAccessToken(
    val token: String,
)
