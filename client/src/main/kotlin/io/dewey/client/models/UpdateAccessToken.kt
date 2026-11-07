package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateAccessToken(
    val name: String,
    val password: String,
    val permissions: AccessTokenPermissions,
)
