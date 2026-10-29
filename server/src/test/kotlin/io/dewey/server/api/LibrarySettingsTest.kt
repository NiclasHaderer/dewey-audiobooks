package io.dewey.server.api

import io.dewey.client.models.LibraryPermissionLevel
import io.dewey.client.models.PartialUpdateLibrary
import io.dewey.openapi.common.Patch
import io.dewey.server.DeweyTest
import io.dewey.server.api
import io.dewey.server.bearer
import io.dewey.server.deweyServer
import io.dewey.server.login
import io.dewey.server.newLibrary
import io.dewey.server.registerWithAccess
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySettingsTest : DeweyTest() {
    @Test
    fun `only an admin changes a library`() =
        deweyServer {
            val libId = newLibrary("lib")
            val writer = bearer(registerWithAccess("writer", libId, LibraryPermissionLevel.READ_WRITE))
            val rename = PartialUpdateLibrary(name = Patch.Set("Renamed"))

            assertEquals(HttpStatusCode.Forbidden, api.updateLibrary(libId, rename, writer).status)
            assertEquals(HttpStatusCode.Forbidden, api.deleteLibrary(libId, writer).status)
            assertEquals(HttpStatusCode.OK, api.updateLibrary(libId, rename, bearer(login("admin"))).status)
        }
}
