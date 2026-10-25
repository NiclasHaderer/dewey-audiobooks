package io.dewey.metadata.libby

import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Queries the live Libby API, so it needs network access and breaks when the API changes. */
@Tag("live")
class LibbyApiLiveTest {
    private val provider = LibbyMetadataAgent(libraryKey = "brooklyn", imageSize = 500)
    private val philosophersStone = "11046339"
    private val harryPotterSeries = "1890760"
    private val stephenFry = "137205"

    @Test
    fun `maps a book`() =
        runBlocking {
            val book = assertNotNull(provider.getBookByID("libby", philosophersStone, MetadataRegion.US))

            assertEquals(philosophersStone, book.id.itemID)
            assertEquals("libby", book.id.provider)
            assertEquals("Harry Potter and the Philosopher's Stone", book.title)
            assertEquals("https://share.libbyapp.com/title/$philosophersStone", book.link)
            assertEquals(listOf("J. K. Rowling"), book.authors?.map { it.name })
            assertEquals(listOf("218800"), book.authors?.map { it.id.itemID })
            assertEquals(
                "https://libbyapp.com/search/brooklyn/search/creator-218800/page-1",
                book.authors?.first()?.link,
            )
            assertEquals(listOf("Stephen Fry"), book.narrators)
            assertEquals(LocalDate(2024, 4, 18), book.releaseDate)
            assertEquals("Pottermore Publishing", book.publisher)
            assertEquals(MetadataLanguage.English, book.language)
            assertEquals("9781789392333", book.isbn)
            assertTrue(book.providerRating!! > 0f, "rating was ${book.providerRating}")
            assertTrue(book.coverURL!!.startsWith("https://"), "cover was '${book.coverURL}'")
        }

    @Test
    fun `maps the series of a book`() =
        runBlocking {
            val book = assertNotNull(provider.getBookByID("libby", philosophersStone, MetadataRegion.US))

            assertEquals(listOf("Harry Potter"), book.series.map { it.title })
            assertEquals(listOf(harryPotterSeries), book.series.map { it.id.itemID })
            assertEquals(listOf(1f), book.series.map { it.index })
            assertEquals(
                "https://libbyapp.com/search/brooklyn/search/series-$harryPotterSeries/page-1",
                book.series.first().link,
            )
        }

    @Test
    fun `turns the description into plain text`() =
        runBlocking {
            val book = assertNotNull(provider.getBookByID("libby", philosophersStone, MetadataRegion.US))
            val description = assertNotNull(book.description)

            assertTrue(description.startsWith("Stephen Fry brings"), "description was '${description.take(60)}...'")
            assertFalse(description.contains("<"), "description still contains markup")
            assertFalse(description.contains("&"), "description still contains entities")
        }

    @Test
    fun `searches for audiobooks`() =
        runBlocking {
            val hits = provider.search(MetadataRegion.US, keywords = "harry potter stephen fry")

            val book =
                assertNotNull(hits.find { it.id.itemID == philosophersStone }, "hits were ${hits.map { it.title }}")
            assertEquals("Harry Potter and the Philosopher's Stone", book.title)
            assertEquals(listOf("Stephen Fry"), book.narrators)
        }

    @Test
    fun `resolves a series with its ordered audiobooks`() =
        runBlocking {
            val series = assertNotNull(provider.getSeriesByID("libby", harryPotterSeries, MetadataRegion.US))

            assertEquals("Harry Potter", series.title)
            assertTrue(series.authors!!.contains("J. K. Rowling"), "authors were ${series.authors}")
            val books = assertNotNull(series.books)
            assertTrue(books.size >= 7, "series only resolved ${books.size} books")
            assertEquals(List(7) { it + 1f }, books.take(7).map { it.series.single().index })
            assertTrue(books.first().title!!.startsWith("Harry Potter"), "first book was '${books.first().title}'")
        }

    @Test
    fun `looks up an author by creator id`() =
        runBlocking {
            val author = assertNotNull(provider.getAuthorByID("libby", stephenFry, MetadataRegion.US))

            assertEquals("Stephen Fry", author.name)
            assertEquals(stephenFry, author.id.itemID)
            assertEquals("libby", author.id.provider)
        }

    @Test
    fun `returns null for unknown ids`() =
        runBlocking {
            assertNull(provider.getBookByID("libby", "999999999", MetadataRegion.US))
            assertNull(provider.getSeriesByID("libby", "999999999", MetadataRegion.US))
            assertNull(provider.getAuthorByID("libby", "999999999", MetadataRegion.US))
        }
}
