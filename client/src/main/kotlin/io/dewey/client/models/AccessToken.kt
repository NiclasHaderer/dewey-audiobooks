package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AccessToken(
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    val id: Uuid,
    val lastUsedAt: Instant? = null,
    val name: String,
    val permissions: AccessTokenPermissions,
)
