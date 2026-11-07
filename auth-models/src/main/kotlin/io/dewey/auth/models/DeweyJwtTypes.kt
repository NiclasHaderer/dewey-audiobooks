package io.dewey.auth.models

enum class DeweyJwtTypes(
    val type: String,
) {
    SessionAccess("session-access"),
    SessionRefresh("session-refresh"),
    AccessToken("access-token"),
}
