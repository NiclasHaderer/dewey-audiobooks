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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioContentTypeTest : DeweyTest() {
    @Test
    fun `an m4b is served as audio`() = servesAs("book.m4b", "audio/mp4")

    @Test
    fun `an mp3 is served as audio`() = servesAs("book.mp3", "audio/mpeg")

    @Test
    fun `a flac is served as audio`() = servesAs("book.flac", "audio/flac")

    private fun servesAs(
        fileName: String,
        expected: String,
    ) = deweyServer {
        val file = dataDir.resolve(fileName)
        file.writeBytes(ByteArray(16))
        val libId = newLibrary("lib", folders = listOf(dataDir.absolutePathString()))
        val bookId = newBook("Dune", libId)
        val trackId = newTrack("t1", file.absolutePathString(), bookId, libId, trackNr = 1)
        val token = bearer(registerWithAccess("listener", libId, LibraryPermissionLevel.READONLY))

        val response = api.getAudioFile(trackId, token)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expected, response.headers[HttpHeaders.ContentType])
    }
}
