package io.dewey.server.api

import io.dewey.client.gen.models.BookUpdate
import io.dewey.client.gen.models.LibraryPermissionLevel
import io.dewey.client.gen.models.ProgressUpdate
import io.dewey.openapi.common.Patch
import io.dewey.server.DeweyTest
import io.dewey.server.api
import io.dewey.server.bearer
import io.dewey.server.deweyServer
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.newTrack
import io.dewey.server.registerWithAccess
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Progress routes are marked [io.dewey.server.plugins.auth.UserScoped], which makes
 * assertLibraryPermissions treat their PUTs as reads. That only holds at the route layer, so it has
 * to be checked over HTTP rather than against the repository.
 */
class ProgressPermissionTest : DeweyTest() {
    @Test
    fun `a readonly member can record progress but still cannot edit the library`() =
        withLibrary(LibraryPermissionLevel.READONLY) { token, libId, bookId ->
            assertEquals(
                HttpStatusCode.NoContent,
                api.setBookProgress(bookId, libId, ProgressUpdate(positionMs = 42000), token).status,
                "a readonly member owns their own progress",
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                api.updateBook(bookId, libId, BookUpdate(title = Patch.Set("Renamed")), token).status,
                "library content is still off limits",
            )
        }

    @Test
    fun `a read write member can do both`() =
        withLibrary(LibraryPermissionLevel.READ_WRITE) { token, libId, bookId ->
            assertEquals(
                HttpStatusCode.NoContent,
                api.setBookProgress(bookId, libId, ProgressUpdate(positionMs = 42000), token).status,
            )
            assertEquals(
                HttpStatusCode.OK,
                api.updateBook(bookId, libId, BookUpdate(title = Patch.Set("Renamed")), token).status,
            )
        }

    private fun withLibrary(
        level: LibraryPermissionLevel,
        block: suspend ApplicationTestBuilder.(Headers, Uuid, Uuid) -> Unit,
    ) = deweyServer {
        val libId = newLibrary("lib", folders = listOf("/media/books"))
        val bookId = newBook("Dune", libId)
        newTrack("t1", "/media/books/dune/1.mp3", bookId, libId, trackNr = 1)
        block(bearer(registerWithAccess("listener", libId, level)), libId, bookId)
    }
}
