package io.dewey.server.api

import io.dewey.client.models.BookUpdate
import io.dewey.metadata.FakeMetadataAgent
import io.dewey.metadata.MetadataAgents
import io.dewey.metadata.responses.MetadataChapters
import io.dewey.metadata.searchHit
import io.dewey.models.ChapterMark
import io.dewey.models.NamedMetadataAgent
import io.dewey.openapi.common.Patch
import io.dewey.server.DeweyTest
import io.dewey.server.api
import io.dewey.server.bearer
import io.dewey.server.database.tables.TrackChapter
import io.dewey.server.deweyServer
import io.dewey.server.login
import io.dewey.server.newAuthor
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.newTrack
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import io.dewey.client.models.ChapterMark as ClientChapterMark

class AgentChaptersTest : DeweyTest() {
    private val agentChapters = listOf(ChapterMark("Prologue", 0), ChapterMark("Arrakis", 2_000))

    private fun useAgent(runtimeMs: Long) {
        val agent =
            FakeMetadataAgent(
                hits = listOf(searchHit("Dune", authors = listOf("Frank Herbert"))),
                resolveChapters = { MetadataChapters(runtimeMs, agentChapters) },
            )
        getKoin().loadModules(listOf(module { single { MetadataAgents(listOf(agent)) } }), allowOverride = true)
    }

    private suspend fun ApplicationTestBuilder.matchedChapterTitles(
        preferEmbeddedMetadata: Boolean = false,
        trackChapters: List<TrackChapter> = emptyList(),
        edited: List<ClientChapterMark>? = null,
    ): List<String?> {
        val name = "lib-$preferEmbeddedMetadata"
        val libId =
            newLibrary(
                name,
                preferEmbeddedMetadata = preferEmbeddedMetadata,
                metadataAgents = listOf(NamedMetadataAgent("fake")),
            )
        val bookId = newBook("Dune", libId, authors = listOf(newAuthor("Frank Herbert", libId)))
        newTrack("Dune", "/media/$name/dune.m4b", bookId, libId, durationMs = 60_000, chapters = trackChapters)
        val token = bearer(login("admin"))
        if (edited != null) api.updateBook(bookId, libId, BookUpdate(chapters = Patch.Set(edited)), token)

        assertEquals(HttpStatusCode.OK, api.autoMatchBook(bookId, libId, token).status)

        return api
            .getBook(bookId, libId, token)
            .body()
            .chapters
            .map { it.title }
    }

    @Test
    fun `a match brings its chapters when its runtime fits the files`() =
        deweyServer {
            useAgent(runtimeMs = 60_000 + 9_000)

            assertEquals(listOf("Prologue", "Arrakis"), matchedChapterTitles())
        }

    @Test
    fun `a match leaves edited chapters alone`() =
        deweyServer {
            useAgent(runtimeMs = 60_000)

            assertEquals(
                listOf("Mine"),
                matchedChapterTitles(edited = listOf(ClientChapterMark(startMs = 0, title = "Mine"))),
            )
        }

    @Test
    fun `a match of another edition keeps the chapters of the files`() =
        deweyServer {
            useAgent(runtimeMs = 60_000 + 10 * 60_000)

            assertEquals(listOf("Dune"), matchedChapterTitles(), "one chapter per file, as before the match")
        }

    @Test
    fun `markers in the files win over a match when the library prefers them`() =
        deweyServer {
            useAgent(runtimeMs = 60_000)
            val markers = listOf(TrackChapter("One", 0), TrackChapter("Two", 30_000))

            assertEquals(
                listOf("One", "Two"),
                matchedChapterTitles(preferEmbeddedMetadata = true, trackChapters = markers),
            )
            assertEquals(listOf("Prologue", "Arrakis"), matchedChapterTitles(trackChapters = markers))
        }
}
