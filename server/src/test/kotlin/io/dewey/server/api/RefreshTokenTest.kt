package io.dewey.server.api

import io.dewey.client.models.DeweyLoginUser
import io.dewey.server.DeweyTest
import io.dewey.server.TEST_PASSWORD
import io.dewey.server.api
import io.dewey.server.authCookie
import io.dewey.server.bearer
import io.dewey.server.cookie
import io.dewey.server.deweyServer
import io.dewey.server.register
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The refresh window slides, so a client that keeps refreshing never has to log in again. */
class RefreshTokenTest : DeweyTest() {
    @Test
    fun `refreshing hands out a new refresh cookie that works on its own`() =
        deweyServer {
            register("alice")
            val login = api.loginUser(DeweyLoginUser(password = TEST_PASSWORD, username = "alice"))
            val firstRefresh = login.authCookie("refresh")

            val refreshed = api.refreshAccessToken(cookie("refresh", firstRefresh.value))
            assertEquals(HttpStatusCode.OK, refreshed.status)

            val secondRefresh = refreshed.authCookie("refresh")
            assertTrue(
                firstRefresh.maxAge == secondRefresh.maxAge,
                "the new cookie must carry the full window again",
            )

            assertEquals(HttpStatusCode.OK, api.listLibraries(bearer(refreshed.authCookie("access").value)).status)
            assertEquals(HttpStatusCode.OK, api.refreshAccessToken(cookie("refresh", secondRefresh.value)).status)
        }
}
