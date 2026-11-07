package io.dewey.models

import io.dewey.openapi.ktor.ValidateObject
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import kotlin.time.Clock
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
) : ValidateObject {
    override suspend fun RoutingContext.validateBody() {
        requireName(name)
        if (expiresAt != null && expiresAt <= Clock.System.now()) {
            throw ErrorResponse.userError("The access token would expire right away, pick an expiry in the future")
        }
    }
}

@Serializable
data class UpdateAccessToken(
    val name: String,
    val permissions: AccessTokenPermissions,
    val password: String,
) : ValidateObject {
    override suspend fun RoutingContext.validateBody() = requireName(name)
}

private fun requireName(name: String) {
    if (name.isBlank()) throw ErrorResponse.userError("An access token name cannot be empty")
}

@Serializable
data class CreatedAccessToken(
    val token: String,
    val accessToken: AccessToken,
)

@Serializable
data class PasswordConfirmation(
    val password: String,
)

@Serializable
data class RevealedAccessToken(
    val token: String,
)
