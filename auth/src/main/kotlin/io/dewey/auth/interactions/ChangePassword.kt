package io.dewey.auth.interactions

import io.dewey.auth.deweyAuthConfig
import io.dewey.auth.models.DeweyChangePassword
import io.dewey.auth.utils.DeweyPrincipal
import io.dewey.auth.utils.deweyPrincipal
import io.dewey.auth.utils.generateJwtPairForUser
import io.dewey.auth.utils.hashPassword
import io.dewey.auth.utils.passwordMatches
import io.dewey.auth.withUserMutation
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.routing.RoutingContext
import kotlin.uuid.Uuid

interface DeweyChangePasswordParams {
    val id: Uuid
}

fun RoutingContext.changeUserPassword(
    params: DeweyChangePasswordParams,
    passwordChange: DeweyChangePassword,
) {
    val principal = deweyPrincipal<DeweyPrincipal>()
    val config = deweyAuthConfig<Any, Any>()

    if (principal.userId != params.id && !config.isAdmin(principal)) {
        throw ErrorResponse.forbidden("Only admins can change another user's password")
    }

    val user = config.getUserById(params.id) ?: throw ErrorResponse.userError("Could not find user with username")

    config.passwordMeetsRequirements(passwordChange.newPassword).also { (meetsRequirements, message) ->
        if (!meetsRequirements) {
            throw ErrorResponse.userError(message!!)
        }
    }

    if (principal.userId == params.id && !passwordMatches(passwordChange.currentPassword, user)) {
        throw ErrorResponse.userError("Wrong password")
    }

    val newPassword = hashPassword(passwordChange.newPassword)
    val updated = withUserMutation { config.updatePassword(user, newPassword) }
    check(updated.tokenVersion != user.tokenVersion) {
        "updatePassword must increment the user's tokenVersion, otherwise tokens issued before the change stay valid"
    }

    // The bump invalidated every token of this user, including the one used for this request
    if (principal.userId == params.id) call.appendAuthCookies(generateJwtPairForUser(updated, config), config)
}
