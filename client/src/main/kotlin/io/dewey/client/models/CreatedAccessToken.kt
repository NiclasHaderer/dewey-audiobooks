package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class CreatedAccessToken(
    val accessToken: AccessToken,
    val token: String,
)
