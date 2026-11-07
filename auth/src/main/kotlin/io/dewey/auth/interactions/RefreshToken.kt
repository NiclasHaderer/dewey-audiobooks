package io.dewey.auth.interactions

import io.dewey.auth.deweyAuthConfig
import io.dewey.auth.models.DeweyAccessToken
import io.dewey.auth.models.DeweyJwtTypes
import io.dewey.auth.utils.generateJwtPairForUser
import io.dewey.auth.utils.validateJwt
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.server.routing.RoutingContext

interface DeweyRefreshTokenParams

fun RoutingContext.refreshAccessToken(
    params: DeweyRefreshTokenParams,
    body: Unit,
): DeweyAccessToken {
    val refreshToken = call.request.cookies[REFRESH_COOKIE] ?: throw ErrorResponse.unauthorized("No refresh token")
    val config = deweyAuthConfig<Any, Any>()
    val user = validateJwt(config, refreshToken, DeweyJwtTypes.SessionRefresh)

    val pair = generateJwtPairForUser(user, config)
    call.appendAuthCookies(pair, config)
    return DeweyAccessToken(pair.accessToken)
}
