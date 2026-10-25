package io.dewey.server.api

import io.dewey.client.models.DeweyChangePassword
import io.dewey.client.models.DeweyLoginUser
import io.dewey.server.DeweyTest
import io.dewey.server.TEST_PASSWORD
import io.dewey.server.api
import io.dewey.server.authCookie
import io.dewey.server.bearer
import io.dewey.server.cookie
import io.dewey.server.deweyServer
import io.dewey.server.login
import io.dewey.server.register
import io.dewey.server.userId
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

/** Changing the password bumps the user's token version, so every previously issued token stops working. */
class TokenVersionTest : DeweyTest() {
    @Test
    fun `password change invalidates old access and refresh tokens`() =
        deweyServer {
            register("alice")
            val login = api.loginUser(DeweyLoginUser(password = TEST_PASSWORD, username = "alice"))
            val oldAccess = login.authCookie("access").value
            val oldRefresh = login.authCookie("refresh").value
            assertEquals(HttpStatusCode.OK, api.listLibraries(bearer(oldAccess)).status)

            val change =
                api.updatePassword(
                    userId("alice"),
                    DeweyChangePassword(currentPassword = TEST_PASSWORD, newPassword = TEST_PASSWORD),
                    bearer(oldAccess),
                )
            assertEquals(HttpStatusCode.NoContent, change.status)

            assertEquals(HttpStatusCode.Unauthorized, api.listLibraries(bearer(oldAccess)).status)
            assertEquals(
                HttpStatusCode.Unauthorized,
                api.refreshAccessToken(cookie("refresh", oldRefresh)).status,
            )

            // The session that changed the password gets fresh cookies and keeps working
            val newAccess = change.authCookie("access").value
            val newRefresh = change.authCookie("refresh").value
            assertEquals(HttpStatusCode.OK, api.listLibraries(bearer(newAccess)).status)
            assertEquals(HttpStatusCode.OK, api.refreshAccessToken(cookie("refresh", newRefresh)).status)
            assertEquals(HttpStatusCode.OK, api.listLibraries(bearer(login("alice"))).status)
        }

    @Test
    fun `deleted user is rejected with the same token`() =
        deweyServer {
            register("bob")
            val token = login("bob")
            val admin = login("admin")
            assertEquals(HttpStatusCode.OK, api.listLibraries(bearer(token)).status)

            assertEquals(HttpStatusCode.NoContent, api.deleteUser(userId("bob"), bearer(admin)).status)
            assertEquals(HttpStatusCode.Unauthorized, api.listLibraries(bearer(token)).status)
        }
}
