package io.dewey.auth.interactions

import io.dewey.auth.deweyAuthConfig
import io.dewey.auth.utils.generateAccessToken
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.routing.RoutingContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

fun RoutingContext.issueAccessToken(
    userId: Uuid,
    tokenId: Uuid,
    expiresAt: Instant?,
): String {
    val config = deweyAuthConfig<Any, Any>()
    val user = config.getUserById(userId) ?: throw ErrorResponse.notFound("User", userId)
    return generateAccessToken(user, tokenId, expiresAt, config)
}
