package io.dewey.auth.interactions

import io.dewey.auth.deweyAuthConfig
import io.dewey.auth.models.DeweyUserWithPermissions
import io.dewey.auth.utils.DeweyPrincipal
import io.dewey.auth.utils.deweyPrincipal
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.routing.RoutingContext

interface DeweyListUserParams

fun <PERMISSIONS : Any> RoutingContext.listUsers(
    params: DeweyListUserParams,
): List<DeweyUserWithPermissions<PERMISSIONS>> {
    val config = deweyAuthConfig<PERMISSIONS, Any>()

    val principal = deweyPrincipal<DeweyPrincipal>()
    if (!principal.permissions.isAdmin) {
        throw ErrorResponse.forbidden("Only admins can list users")
    }

    return config.listAllUsers().map { user ->
        val permissions = config.run { getUserPermissions(user) }
        DeweyUserWithPermissions(id = user.id, username = user.username, permissions = permissions)
    }
}
