package io.dewey.auth.interactions

import io.dewey.auth.deweyAuthConfig
import io.dewey.auth.models.DeweyUser
import io.dewey.auth.models.DeweyUserWithPermissions
import io.dewey.auth.utils.DeweyPrincipal
import io.dewey.auth.utils.deweyPrincipal
import io.dewey.auth.utils.wrap
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.routing.RoutingContext
import kotlin.uuid.Uuid

interface DeweyDisplayUserParams {
    val id: Uuid
}

fun RoutingContext.displayUser(params: DeweyDisplayUserParams): DeweyUser {
    val principal = deweyPrincipal<DeweyPrincipal>()
    val config = deweyAuthConfig<Any, Any>()

    if (principal.userId != params.id && !config.isAdmin(principal)) {
        throw ErrorResponse.forbidden("View", "account")
    }

    val user = config.getUserById(params.id) ?: throw ErrorResponse.notFound("User", params.id)
    return user.wrap()
}

interface DeweyCurrentUserParams

fun <PERMISSIONS : Any> RoutingContext.currentUser(
    params: DeweyCurrentUserParams,
): DeweyUserWithPermissions<PERMISSIONS> {
    val principal = deweyPrincipal<DeweyPrincipal>()
    val config = deweyAuthConfig<PERMISSIONS, Any>()

    val user = config.getUserById(principal.userId) ?: throw ErrorResponse.notFound("User", principal.userId)
    return user.let {
        val permissions = config.run { getUserPermissions(user) }
        DeweyUserWithPermissions(id = user.id, username = user.username, permissions = permissions)
    }
}
