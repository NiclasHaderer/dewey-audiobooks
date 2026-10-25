package io.dewey.metadata

import io.dewey.metadata.responses.MetadataRegion
import io.dewey.metadata.responses.MetadataSearchAuthor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetadataAgentWrapperTest {
    private val audible = FakeMetadataAgent(name = "audible", hits = listOf(searchHit("a", provider = "audible")))
    private val openLibrary =
        FakeMetadataAgent(name = "openLibrary", hits = listOf(searchHit("b", provider = "openLibrary")))

    private fun wrapper() = MetadataAgentWrapper(listOf(audible, openLibrary))

    @Test
    fun `an ID lookup only goes to the agent it belongs to`() =
        runBlocking {
            wrapper().getBookByID(providerId = "openLibrary", bookId = "b", region = MetadataRegion.US)

            assertEquals(emptyList(), audible.bookLookups)
            assertEquals(listOf("b@US"), openLibrary.bookLookups)
        }

    @Test
    fun `an ID of an unknown provider has no result`() =
        runBlocking {
            assertNull(wrapper().getBookByID(providerId = "goodreads", bookId = "b", region = MetadataRegion.US))
        }

    @Test
    fun `a lookup by name hands out the results of the first agent before touching the second`() =
        runBlocking {
            val first = wrapper().getBookByName("a", MetadataRegion.US).first()

            assertEquals("a", first.id.itemID)
            assertEquals(0, openLibrary.searchCalls.get())
        }

    @Test
    fun `collecting a lookup by name walks through all agents`() =
        runBlocking {
            val books = wrapper().getBookByName("a", MetadataRegion.US).toList()

            assertEquals(listOf("a", "b"), books.map { it.id.itemID })
        }

    @Test
    fun `a best match without combining stops at the first agent that answers`() =
        runBlocking {
            val match = wrapper().bestBookMatch("a", MetadataRegion.US)

            assertEquals("a", match?.id?.itemID)
            assertEquals(0, openLibrary.searchCalls.get(), "the lower priority agent must not be asked")
        }

    @Test
    fun `a best match falls through to the next agent when the preferred one has nothing`() =
        runBlocking {
            val silent = FakeMetadataAgent(name = "silent")

            val match = MetadataAgentWrapper(listOf(silent, openLibrary)).bestBookMatch("b", MetadataRegion.US)

            assertEquals("b", match?.id?.itemID)
        }

    private fun agentWithBooksBy(
        name: String,
        authorsByBook: Map<String, String>,
    ) = FakeMetadataAgent(
        name = name,
        hits = authorsByBook.keys.map { searchHit(it, title = "Playing with Fire", provider = name) },
        resolveBook = { id ->
            val author = authorsByBook.getValue(id)
            testBook(id, name).copy(
                authors = listOf(MetadataSearchAuthor(testId(author, name), author, "link/$author")),
            )
        },
    )

    @Test
    fun `a best match skips the books of another author`() =
        runBlocking {
            val agent =
                agentWithBooksBy("audiobookdb", mapOf("gerritsen" to "Tess Gerritsen", "landy" to "Derek Landy"))

            val match =
                MetadataAgentWrapper(listOf(agent))
                    .bestBookMatch("Playing with Fire", MetadataRegion.US, authorName = "Derek Landy")

            assertEquals("landy", match?.id?.itemID)
        }

    @Test
    fun `a best match asks the next agent when the preferred one only knows another author`() =
        runBlocking {
            val preferred = agentWithBooksBy("audiobookdb", mapOf("gerritsen" to "Tess Gerritsen"))
            val other = agentWithBooksBy("audible", mapOf("landy" to "Derek Landy"))

            val match =
                MetadataAgentWrapper(listOf(preferred, other))
                    .bestBookMatch("Playing with Fire", MetadataRegion.US, authorName = "Derek Landy")

            assertEquals(testId("landy", "audible"), match?.id)
        }

    @Test
    fun `a best match is nothing when every candidate is by another author`() =
        runBlocking {
            val agent = agentWithBooksBy("audiobookdb", mapOf("gerritsen" to "Tess Gerritsen"))

            val match =
                MetadataAgentWrapper(listOf(agent))
                    .bestBookMatch("Playing with Fire", MetadataRegion.US, authorName = "Derek Landy")

            assertNull(match)
        }

    @Test
    fun `combining fills the fields the preferred agent left empty`() =
        runBlocking {
            val preferred =
                FakeMetadataAgent(
                    name = "audible",
                    hits = listOf(searchHit("a", provider = "audible")),
                    resolveBook = { testBook(it, "audible").copy(publisher = "Audible Studios") },
                )
            val other =
                FakeMetadataAgent(
                    name = "openLibrary",
                    hits = listOf(searchHit("b", provider = "openLibrary")),
                    resolveBook = {
                        testBook(it, "openLibrary").copy(
                            publisher = "Penguin",
                            isbn = "9780000000000",
                            narrators = listOf("Ada"),
                        )
                    },
                )

            val match =
                MetadataAgentWrapper(listOf(preferred, other), combineFields = true)
                    .bestBookMatch("a", MetadataRegion.US)

            assertEquals(testId("a", "audible"), match?.id, "the merged record stays attributable to its matcher")
            assertEquals("Audible Studios", match?.publisher, "a field the preferred agent filled must survive")
            assertEquals("9780000000000", match?.isbn)
            assertEquals(listOf("Ada"), match?.narrators)
        }
}
