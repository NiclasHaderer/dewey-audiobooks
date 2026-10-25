package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class DeweyAccessToken(
    val accessToken: String,
)
