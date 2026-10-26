package io.dewey.models

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AccessToken(
    val id: Uuid,
    val name: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val expiresAt: Instant?,
    val permissions: AccessTokenPermissions,
)

@Serializable
data class CreateAccessToken(
    val name: String,
    val expiresAt: Instant? = null,
    val permissions: AccessTokenPermissions,
    val password: String,
)

@Serializable
data class CreatedAccessToken(
    val token: String,
    val accessToken: AccessToken,
)

@Serializable
data class RevealAccessToken(
    val password: String,
)

@Serializable
data class RevealedAccessToken(
    val token: String,
)
