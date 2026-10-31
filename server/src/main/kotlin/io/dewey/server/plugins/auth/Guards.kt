package io.dewey.server.plugins.auth

object Guards {
    const val Admin = "admin"
    const val Normal = "normal"
    const val Media = "media"

    const val NormalLogin = "normal-login"
    const val AdminLogin = "admin-login"
    const val MediaLogin = "media-login"

    val LoginOnly = setOf(NormalLogin, AdminLogin, MediaLogin)
    val AdminOnly = setOf(Admin, AdminLogin)

    // Media is fetched by the browser itself, an <img> or <audio> that cannot set a header, so it may send a cookie
    val CookieAllowed = setOf(Media, MediaLogin)

    val All = listOf(Normal, Media, Admin) + LoginOnly
}
