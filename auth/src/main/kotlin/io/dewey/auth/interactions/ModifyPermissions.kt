package io.dewey.auth.interactions

import io.dewey.auth.deweyAuthConfig
import io.dewey.auth.models.DeweyModifyPermissions
import io.dewey.auth.models.DeweyUser
import io.dewey.auth.utils.DeweyPrincipal
import io.dewey.auth.utils.deweyPrincipal
import io.dewey.auth.utils.requireLoginSession
import io.dewey.auth.utils.wrap
import io.dewey.auth.withUserMutation
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.routing.RoutingContext
import kotlin.uuid.Uuid

interface DeweyModifyPermissionsParams {
    val id: Uuid
}

fun <UPDATE_PERMISSIONS> RoutingContext.modifyUserPermissions(
    params: DeweyModifyPermissionsParams,
    body: DeweyModifyPermissions<UPDATE_PERMISSIONS>,
): DeweyUser {
    val principal = deweyPrincipal<DeweyPrincipal>()
    val config = deweyAuthConfig<Any, UPDATE_PERMISSIONS>()

    if (!principal.isAdmin) {
        throw ErrorResponse.forbidden("Only admins can modify permissions")
    }
    principal.requireLoginSession("change permissions")

    return withUserMutation {
        val user = config.getUserById(params.id) ?: throw ErrorResponse.notFound("User", params.id)
        config.updateUserPermissions(user, body.permissions).wrap()
    }
}
