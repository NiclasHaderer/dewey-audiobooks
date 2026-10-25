package io.dewey.server.api

import io.dewey.client.gen.models.BookField
import io.dewey.client.gen.models.BookUpdate
import io.dewey.client.gen.models.ChapterMark
import io.dewey.openapi.common.Patch
import io.dewey.server.DeweyTest
import io.dewey.server.api
import io.dewey.server.bearer
import io.dewey.server.database.tables.TrackChapter
import io.dewey.server.deweyServer
import io.dewey.server.login
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.newTrack
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterApiTest : DeweyTest() {
    @Test
    fun `chapters come from the embedded markers`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            val trackId =
                newTrack(
                    "Dune",
                    "/media/books/dune/Dune.m4b",
                    bookId,
                    libId,
                    durationMs = 4000,
                    chapters = listOf(TrackChapter("One", 0), TrackChapter("Two", 2000)),
                )
            val token = bearer(login("admin"))

            val chapters = api.getBook(bookId, libId, token).body().chapters

            assertEquals(listOf("One" to 0L, "Two" to 2000L), chapters.map { it.title to it.startMs })
            assertEquals(listOf(2000L, 4000L), chapters.map { it.endMs })
            assertEquals(listOf(trackId, trackId), chapters.map { it.trackId })
        }

    @Test
    fun `edited chapters override the files until they are unlocked`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            newTrack("Part 1", "/media/books/dune/Part 1.mp3", bookId, libId, trackNr = 1, durationMs = 1000)
            newTrack("Part 2", "/media/books/dune/Part 2.mp3", bookId, libId, trackNr = 2, durationMs = 1000)
            val token = bearer(login("admin"))
            val marks =
                listOf(
                    ChapterMark(startMs = 0, title = "Prologue"),
                    ChapterMark(startMs = 500, title = "Arrakis"),
                )

            val response = api.updateBook(bookId, libId, BookUpdate(chapters = Patch.Set(marks)), token)

            assertEquals(HttpStatusCode.OK, response.status)
            val edited = api.getBook(bookId, libId, token).body()
            assertEquals(listOf("Prologue", "Arrakis"), edited.chapters.map { it.title })
            assertEquals(listOf(500L, 2000L), edited.chapters.map { it.endMs })
            assertEquals(listOf(BookField.CHAPTERS), edited.locked)

            api.updateBook(bookId, libId, BookUpdate(unlock = Patch.Set(listOf(BookField.CHAPTERS))), token)

            val unlocked = api.getBook(bookId, libId, token).body()
            assertEquals(listOf("Part 1", "Part 2"), unlocked.chapters.map { it.title })
            assertEquals(emptyList(), unlocked.locked)
        }

    @Test
    fun `edited chapters can be left untitled`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            newTrack("Part 1", "/media/books/dune/Part 1.mp3", bookId, libId, durationMs = 1000)
            val token = bearer(login("admin"))
            val marks =
                listOf(
                    ChapterMark(startMs = 0, title = "Prologue"),
                    ChapterMark(startMs = 500, title = " "),
                )

            val response = api.updateBook(bookId, libId, BookUpdate(chapters = Patch.Set(marks)), token)

            assertEquals(HttpStatusCode.OK, response.status)
            val edited = api.getBook(bookId, libId, token).body()
            assertEquals(listOf("Prologue", " "), edited.chapters.map { it.title })
        }

    @Test
    fun `invalid chapters are rejected`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            newTrack("Part 1", "/media/books/dune/Part 1.mp3", bookId, libId, durationMs = 1000)
            val token = bearer(login("admin"))

            val invalid =
                listOf(
                    emptyList(),
                    listOf(ChapterMark(startMs = 500, title = "B"), ChapterMark(startMs = 0, title = "A")),
                    listOf(
                        ChapterMark(startMs = 0, title = "A"),
                        ChapterMark(startMs = 1000, title = "Past the end"),
                    ),
                )
            for (marks in invalid) {
                val response = api.updateBook(bookId, libId, BookUpdate(chapters = Patch.Set(marks)), token)
                assertEquals(HttpStatusCode.BadRequest, response.status, "$marks must be rejected")
            }
        }
}
