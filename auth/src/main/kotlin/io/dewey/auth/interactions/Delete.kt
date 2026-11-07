package io.dewey.auth.interactions

import io.dewey.auth.deweyAuthConfig
import io.dewey.auth.utils.DeweyPrincipal
import io.dewey.auth.utils.deweyPrincipal
import io.dewey.auth.withUserMutation
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.routing.RoutingContext
import kotlin.uuid.Uuid

interface DeweyDeleteUserParams {
    val id: Uuid
}

fun RoutingContext.deleteUser(
    params: DeweyDeleteUserParams,
    body: Unit,
) {
    val principal = deweyPrincipal<DeweyPrincipal>()
    val config = deweyAuthConfig<Any, Any>()

    if (principal.userId != params.id && !principal.permissions.isAdmin) {
        throw ErrorResponse.forbidden("Only admins can delete another user")
    }

    withUserMutation {
        val user = config.getUserById(params.id) ?: throw ErrorResponse.userError("User not found")
        config.deleteUser(user)
    }
}
