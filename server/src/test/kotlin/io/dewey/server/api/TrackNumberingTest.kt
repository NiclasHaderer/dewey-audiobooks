package io.dewey.server.api

import io.dewey.client.models.LibraryPermissionLevel
import io.dewey.server.DeweyTest
import io.dewey.server.api
import io.dewey.server.bearer
import io.dewey.server.deweyServer
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.newTrack
import io.dewey.server.registerWithAccess
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackNumberingTest : DeweyTest() {
    @Test
    fun `books without trusted numbering are numbered by position`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            newTrack("Chapter 10", "/media/books/dune/Chapter 10.mp3", bookId, libId, trackNr = 1)
            newTrack("Chapter 02", "/media/books/dune/Chapter 02.mp3", bookId, libId)
            val token = bearer(registerWithAccess("listener", libId, LibraryPermissionLevel.READONLY))

            val tracks = api.getBook(bookId, libId, token).body().tracks
            assertEquals(listOf("Chapter 02" to 1, "Chapter 10" to 2), tracks.map { it.title to it.trackNr })
        }
}
