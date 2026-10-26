package io.dewey.auth.interactions

import io.dewey.auth.deweyAuthConfig
import io.dewey.auth.models.DeweyRenameUser
import io.dewey.auth.models.DeweyUser
import io.dewey.auth.utils.DeweyPrincipal
import io.dewey.auth.utils.deweyPrincipal
import io.dewey.auth.utils.requireLoginSession
import io.dewey.auth.utils.wrap
import io.dewey.auth.withUserMutation
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.routing.RoutingContext
import kotlin.uuid.Uuid

interface DeweyRenameUserParams {
    val id: Uuid
}

fun RoutingContext.renameUser(
    params: DeweyRenameUserParams,
    renamedUser: DeweyRenameUser,
): DeweyUser {
    val principal = deweyPrincipal<DeweyPrincipal>()
    val config = deweyAuthConfig<Any, Any>()

    if (principal.userId != params.id && !principal.isAdmin) {
        throw ErrorResponse.forbidden("Only admins can rename another user")
    }
    principal.requireLoginSession("rename an account")

    config.usernameMeetsRequirements(renamedUser.username).also { (meetsRequirement, message) ->
        if (!meetsRequirement) {
            throw ErrorResponse.userError(message!!)
        }
    }

    return withUserMutation {
        val user = config.getUserById(params.id) ?: throw ErrorResponse.notFound("User", params.id)
        config.renameUser(user, renamedUser.username).wrap()
    }
}
