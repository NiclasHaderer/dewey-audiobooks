package io.dewey.auth.utils

import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.auth.Principal
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext
import kotlin.uuid.Uuid

interface DeweyPermissions {
    val isAdmin: Boolean
}

interface DeweyPrincipal {
    val userId: Uuid
    val isAccessToken: Boolean
    val permissions: DeweyPermissions
}

inline fun <reified TYPE : DeweyPrincipal> RoutingContext.deweyPrincipal(): TYPE =
    call.principal()
        ?: throw ErrorResponse.internalError(
            "Route requires user to be logged in, but authentication was not required. Please check your configuration.",
        )
