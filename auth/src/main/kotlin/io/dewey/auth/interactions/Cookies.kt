package io.dewey.auth.interactions

import io.dewey.auth.DeweyAuthConfig
import io.dewey.auth.models.DeweyJwtPair
import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall

internal const val ACCESS_COOKIE = "access"
internal const val REFRESH_COOKIE = "refresh"

// Clearing a cookie only works if every attribute matches the one that set it, so both go through here.
// The access cookie is scoped to "/" because the media routes under /api/stream need it too, not just /api/auth.
private fun ApplicationCall.appendAuthCookie(
    name: String,
    value: String,
    lifetimeMillis: Long,
    config: DeweyAuthConfig<*, *, *>,
) = response.cookies.append(
    Cookie(
        name = name,
        value = value,
        httpOnly = true,
        secure = config.ssl,
        path = if (name == ACCESS_COOKIE) "/" else null,
        extensions = mapOf("SameSite" to "Strict"),
        maxAge = (lifetimeMillis / 1000).toInt(),
    ),
)

internal fun ApplicationCall.appendAuthCookies(
    pair: DeweyJwtPair,
    config: DeweyAuthConfig<*, *, *>,
) {
    appendAuthCookie(ACCESS_COOKIE, pair.accessToken, config.accessTokenExpiryTime, config)
    appendAuthCookie(REFRESH_COOKIE, pair.refreshToken, config.refreshTokenExpiryTime, config)
}

internal fun ApplicationCall.clearAuthCookies(config: DeweyAuthConfig<*, *, *>) {
    appendAuthCookie(ACCESS_COOKIE, "", 0, config)
    appendAuthCookie(REFRESH_COOKIE, "", 0, config)
}
