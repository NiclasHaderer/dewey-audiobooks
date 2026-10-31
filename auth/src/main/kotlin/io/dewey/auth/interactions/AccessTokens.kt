package io.dewey.auth.interactions

import io.dewey.auth.deweyAuthConfig
import io.dewey.auth.models.DeweyJwtTypes
import io.dewey.auth.utils.DeweyPrincipal
import io.dewey.auth.utils.deweyPrincipal
import io.dewey.auth.utils.passwordMatches
import io.dewey.auth.utils.signJwt
import io.dewey.auth.withUserMutation
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.routing.RoutingContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

// Runs under the lock a password change takes, so a token created here cannot slip in between the check and a
// concurrent change that revokes every token of the user
fun <T> RoutingContext.withConfirmedPassword(
    password: String,
    block: () -> T,
): T =
    withUserMutation {
        val principal = deweyPrincipal<DeweyPrincipal>()
        val user = deweyAuthConfig<Any, Any>().getUserById(principal.userId)
            ?: throw ErrorResponse.notFound("User", principal.userId)
        if (!passwordMatches(password, user)) throw ErrorResponse.userError("Wrong password")
        block()
    }

fun RoutingContext.issueAccessToken(
    userId: Uuid,
    tokenId: Uuid,
    expiresAt: Instant?,
): String {
    val config = deweyAuthConfig<Any, Any>()
    val user = config.getUserById(userId) ?: throw ErrorResponse.notFound("User", userId)
    return signJwt(user, DeweyJwtTypes.AccessToken, expiresAt, config, tokenId)
}
