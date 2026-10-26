package io.dewey.auth.utils

import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.auth.Principal
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext
import kotlin.uuid.Uuid

interface DeweyPrincipal {
    val userId: Uuid
    val isAdmin: Boolean
    val accessTokenId: Uuid?
}

/**
 * Accounts are only managed with a login, an access token works on libraries alone. A token that could change
 * passwords, permissions or other users would let a leaked admin token take over accounts for good.
 */
fun DeweyPrincipal.requireLoginSession(change: String) {
    if (accessTokenId != null) {
        throw ErrorResponse.forbidden("An access token cannot $change, log in with your password instead")
    }
}

inline fun <reified TYPE : DeweyPrincipal> RoutingContext.deweyPrincipal(): TYPE =
    call.principal()
        ?: throw ErrorResponse.internalError(
            "Route requires user to be logged in, but authentication was not required. Please check your configuration.",
        )
