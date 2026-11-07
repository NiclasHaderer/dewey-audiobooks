package io.dewey.server.api

import io.dewey.client.models.DeweyRegisterUser
import io.dewey.client.models.DeweyRenameUser
import io.dewey.server.DeweyTest
import io.dewey.server.TEST_PASSWORD
import io.dewey.server.api
import io.dewey.server.bearer
import io.dewey.server.deweyServer
import io.dewey.server.login
import io.dewey.server.register
import io.dewey.server.userId
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthTest : DeweyTest() {
    @Test
    fun `a taken username can neither be registered nor renamed to`() =
        deweyServer {
            register("listener")

            val registered = api.registerUser(DeweyRegisterUser(password = TEST_PASSWORD, username = "admin"))
            assertEquals(HttpStatusCode.Conflict, registered.status)

            val renamed = api.updateUsername(userId("listener"), DeweyRenameUser("admin"), bearer(login("admin")))
            assertEquals(HttpStatusCode.Conflict, renamed.status)
        }
}
