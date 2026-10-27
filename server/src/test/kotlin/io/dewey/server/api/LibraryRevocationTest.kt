package io.dewey.server.api

import io.dewey.client.models.LibraryPermissionLevel
import io.dewey.client.models.ProgressUpdate
import io.dewey.server.DeweyTest
import io.dewey.server.api
import io.dewey.server.bearer
import io.dewey.server.deweyServer
import io.dewey.server.grant
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.newTrack
import io.dewey.server.registerWithAccess
import io.dewey.server.revoke
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Access is resolved from the database on every request rather than baked into the JWT, so revoking
 * a library has to take effect immediately - even while the user still holds a valid access token.
 */
class LibraryRevocationTest : DeweyTest() {
    @Test
    fun `revoking a library hides its books, progress and history from the same token`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            val trackId = newTrack("t1", "/media/books/dune/1.mp3", bookId, libId, trackNr = 1)
            repeat(2) { newTrack("t${it + 2}", "/media/books/dune/${it + 2}.mp3", bookId, libId, trackNr = it + 2) }
            val token = bearer(registerWithAccess("listener", libId, LibraryPermissionLevel.READONLY))

            // Build up some state while access is still granted
            assertEquals(
                HttpStatusCode.NoContent,
                api.setBookProgress(bookId, libId, ProgressUpdate(positionMs = 42000), token).status,
            )
            assertTrue(
                api
                    .listBooks(libId, headers = token)
                    .body()
                    .items
                    .any { it.title == "Dune" },
            )
            assertTrue(api.getContinueListening(headers = token).body().any { it.title == "Dune" })
            assertTrue(
                api
                    .getListeningHistory(headers = token)
                    .body()
                    .items
                    .any { it.book.title == "Dune" },
            )

            revoke("listener", libId)

            // Same token, no new login
            assertEquals(HttpStatusCode.Forbidden, api.listBooks(libId, headers = token).status)
            assertEquals(HttpStatusCode.Forbidden, api.getBook(bookId, libId, token).status)
            assertEquals(HttpStatusCode.Forbidden, api.getLibrary(libId, token).status)
            assertEquals(HttpStatusCode.Forbidden, api.getAudioFile(trackId, token).status)

            assertTrue(api.getContinueListening(headers = token).body().isEmpty())
            assertTrue(
                api
                    .getListeningHistory(headers = token)
                    .body()
                    .items
                    .isEmpty(),
            )
            assertTrue(api.listLibraries(token).body().isEmpty(), "the library itself must not be listed")

            val search = api.searchInAllLibraries(query = "Dune", headers = token).body()
            assertTrue(search.books.isEmpty(), "revoked books must not surface in search: $search")

            assertEquals(
                HttpStatusCode.Forbidden,
                api.setBookProgress(bookId, libId, ProgressUpdate(positionMs = 99000), token).status,
            )
        }

    @Test
    fun `regranting a library brings the old progress back`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            repeat(3) { newTrack("t$it", "/media/books/dune/$it.mp3", bookId, libId, trackNr = it + 1) }
            val token = bearer(registerWithAccess("listener", libId, LibraryPermissionLevel.READONLY))

            api.setBookProgress(bookId, libId, ProgressUpdate(positionMs = 42000), token)

            revoke("listener", libId)
            assertTrue(api.getContinueListening(headers = token).body().isEmpty())

            grant("listener", libId, LibraryPermissionLevel.READONLY)
            assertEquals(
                42000,
                api
                    .getContinueListening(headers = token)
                    .body()
                    .single()
                    .positionMs,
            )
        }
}
