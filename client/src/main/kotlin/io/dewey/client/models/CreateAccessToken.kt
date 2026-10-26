package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CreateAccessToken(
    val expiresAt: Instant? = null,
    val name: String,
    val permissions: AccessTokenPermissions,
)
