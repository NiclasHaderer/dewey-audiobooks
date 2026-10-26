package io.dewey.auth.interactions

import io.dewey.auth.deweyAuthConfig
import io.dewey.auth.utils.DeweyPrincipal
import io.dewey.auth.utils.deweyPrincipal
import io.dewey.auth.utils.generateAccessToken
import io.dewey.auth.utils.passwordMatches
import io.dewey.auth.utils.requireLoginSession
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.routing.RoutingContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

fun RoutingContext.confirmPassword(
    password: String,
    change: String,
) {
    val principal = deweyPrincipal<DeweyPrincipal>()
    principal.requireLoginSession(change)
    val user = deweyAuthConfig<Any, Any>().getUserById(principal.userId)
        ?: throw ErrorResponse.notFound("User", principal.userId)
    if (!passwordMatches(password, user)) throw ErrorResponse.userError("Wrong password")
}

fun RoutingContext.issueAccessToken(
    userId: Uuid,
    tokenId: Uuid,
    expiresAt: Instant?,
): String {
    val config = deweyAuthConfig<Any, Any>()
    val user = config.getUserById(userId) ?: throw ErrorResponse.notFound("User", userId)
    return generateAccessToken(user, tokenId, expiresAt, config)
}
