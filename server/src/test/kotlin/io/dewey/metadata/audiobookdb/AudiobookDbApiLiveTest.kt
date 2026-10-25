package io.dewey.metadata.audiobookdb

import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Queries the live AudiobookDB API, so it needs network access and breaks when the API changes. */
@Tag("live")
class AudiobookDbApiLiveTest {
    private val agent = AudiobookDbMetadataAgent()
    private val projectHailMaryBook = "hPjDhje6pNjQ"
    private val projectHailMary = "f3j8VRYwgytp"
    private val andyWeir = "ypMPwXzWXaiX"
    private val forwardCollection = "rtEwTXFwaiHr"
    private val orderOfThePhoenix = "Harry Potter and the Order of the Phoenix, Book 5"
    private val friedRelease = "jKWANb7wdVbc"
    private val daleRelease = "NbxYfApUqBtF"
    private val duneRelease = "YAjbHtPzyNPT"

    @Test
    fun `maps a release`() =
        runBlocking {
            val book = assertNotNull(agent.getBookByID("audiobookdb", projectHailMary, MetadataRegion.US))

            assertEquals(projectHailMary, book.id.itemID)
            assertEquals("audiobookdb", book.id.provider)
            assertEquals("Project Hail Mary", book.title)
            assertEquals("https://audiobookdb.org/releases/$projectHailMary", book.link)
            assertEquals(listOf("Andy Weir"), book.authors?.map { it.name })
            assertEquals(listOf(andyWeir), book.authors?.map { it.id.itemID })
            assertEquals("https://audiobookdb.org/people/$andyWeir", book.authors?.first()?.link)
            assertEquals(listOf("Ray Porter"), book.narrators)
            assertEquals(LocalDate(2021, 5, 4), book.releaseDate)
            assertEquals("Audible Studios", book.publisher)
            assertEquals(MetadataLanguage.English, book.language)
            assertEquals("9781603935470", book.isbn)
            assertTrue(book.providerRating!! > 0f, "rating was ${book.providerRating}")
            assertTrue(book.coverURL!!.contains("/source."), "cover was '${book.coverURL}'")
            assertTrue(
                book.description!!.startsWith("Ryland Grace"),
                "description was '${book.description?.take(60)}...'",
            )
        }

    @Test
    fun `searches for audiobooks`() =
        runBlocking {
            val hits = agent.getBookByName("Project Hail Mary", MetadataRegion.US).take(10).toList()

            val book =
                assertNotNull(hits.find { it.id.itemID == projectHailMary }, "hits were ${hits.map { it.title }}")
            assertEquals("Project Hail Mary", book.title)
            assertEquals(listOf("Ray Porter"), book.narrators)
            assertEquals(MetadataLanguage.English, book.language)
            assertTrue(book.coverURL!!.contains("/source."), "cover was '${book.coverURL}'")
        }

    @Test
    fun `keeps the releases of one book apart`() =
        runBlocking {
            val hits = agent.getBookByName(orderOfThePhoenix, MetadataRegion.US).take(10).toList()

            val fried =
                assertNotNull(hits.find { it.id.itemID == friedRelease }, "hits were ${hits.map { it.id.itemID }}")
            val dale =
                assertNotNull(hits.find { it.id.itemID == daleRelease }, "hits were ${hits.map { it.id.itemID }}")
            assertEquals(listOf("Stephen Fry"), fried.narrators)
            assertEquals(listOf("Jim Dale"), dale.narrators)
            assertNotEquals(fried.coverURL, dale.coverURL)
        }

    @Test
    fun `narrows a search down to one narrator`() =
        runBlocking {
            val hits = agent
                .getBookByName(
                    orderOfThePhoenix,
                    MetadataRegion.US,
                    narrator = "Stephen Fry",
                ).take(1)
                .toList()

            assertEquals(listOf(friedRelease), hits.map { it.id.itemID })
        }

    @Test
    fun `has nothing for a book id, because a book is not an audiobook`() =
        runBlocking {
            assertNull(agent.getBookByID("audiobookdb", projectHailMaryBook, MetadataRegion.US))
        }

    @Test
    fun `looks up an author`() =
        runBlocking {
            val author = assertNotNull(agent.getAuthorByID("audiobookdb", andyWeir, MetadataRegion.US))

            assertEquals("Andy Weir", author.name)
            assertEquals(andyWeir, author.id.itemID)
            assertEquals("audiobookdb", author.id.provider)
            assertEquals("https://audiobookdb.org/people/$andyWeir", author.link)
            assertEquals(LocalDate(1972, 6, 16), author.birthDate)
            assertTrue(author.bornIn?.startsWith("Davis") == true, "bornIn was '${author.bornIn}'")
            assertTrue(!author.biography.isNullOrBlank(), "biography was '${author.biography}'")
        }

    @Test
    fun `resolves a series with its books`() =
        runBlocking {
            val series = assertNotNull(agent.getSeriesByID("audiobookdb", forwardCollection, MetadataRegion.US))

            assertEquals("Forward Collection", series.title)
            assertEquals("https://audiobookdb.org/series/$forwardCollection", series.link)
            val books = assertNotNull(series.books)
            assertTrue(books.size >= 6, "series only resolved ${books.size} books")
            assertTrue(books.all { !it.title.isNullOrBlank() }, "series contained untitled books")
        }

    @Test
    fun `returns null for unknown ids`() =
        runBlocking {
            assertNull(agent.getBookByID("audiobookdb", "zzzzzzzzzzzz", MetadataRegion.US))
            assertNull(agent.getSeriesByID("audiobookdb", "zzzzzzzzzzzz", MetadataRegion.US))
            assertNull(agent.getAuthorByID("audiobookdb", "zzzzzzzzzzzz", MetadataRegion.US))
        }

    @Test
    fun `finds an author by name`() =
        runBlocking {
            val authors = agent.getAuthorByName("Andy Weir", MetadataRegion.US).take(3).toList()

            val weir = assertNotNull(authors.find { it.id.itemID == andyWeir }, "found ${authors.map { it.name }}")
            assertEquals("Andy Weir", weir.name)
            assertTrue(weir.bornIn?.startsWith("Davis") == true, "bornIn was '${weir.bornIn}'")
        }

    @Test
    fun `finds a series by name`() =
        runBlocking {
            val series = agent.getSeriesByName("Forward Collection", MetadataRegion.US).take(3).toList()

            val forward =
                assertNotNull(series.find { it.id.itemID == forwardCollection }, "found ${series.map { it.title }}")
            assertEquals("Forward Collection", forward.title)
            assertTrue(forward.books!!.size >= 6, "series only resolved ${forward.books?.size} books")
        }

    @Test
    fun `finds a book by name`() =
        runBlocking {
            val books = agent
                .getBookByName(
                    "Project Hail Mary",
                    MetadataRegion.US,
                    authorName = "Andy Weir",
                ).take(3)
                .toList()

            val book = assertNotNull(books.find { it.id.itemID == projectHailMary }, "found ${books.map { it.title }}")
            assertEquals(listOf("Ray Porter"), book.narrators)
            assertEquals(listOf("Andy Weir"), book.authors?.map { it.name })
        }

    @Test
    fun `picks the edition the narrator names`() =
        runBlocking {
            val fried = agent.getBookByName(orderOfThePhoenix, MetadataRegion.US, narrator = "Stephen Fry").first()
            val dale = agent.getBookByName(orderOfThePhoenix, MetadataRegion.US, narrator = "Jim Dale").first()

            assertEquals(friedRelease, fried.id.itemID)
            assertEquals(daleRelease, dale.id.itemID)
            assertEquals(listOf("Stephen Fry"), fried.narrators)
            assertEquals(listOf("Jim Dale"), dale.narrators)
        }

    @Test
    fun `still resolves a book whose narrator is unknown here`() =
        runBlocking {
            val book = agent.getBookByName(orderOfThePhoenix, MetadataRegion.US, narrator = "Nobody At All").first()

            assertTrue(book.id.itemID in listOf(friedRelease, daleRelease), "resolved ${book.id.itemID}")
        }

    @Test
    fun `maps the chapters of a release`() =
        runBlocking {
            val chapters = assertNotNull(agent.getBookChapters("audiobookdb", duneRelease, MetadataRegion.US))

            assertEquals(75_735_564L, chapters.runtimeMs)
            assertEquals(
                listOf("Opening Credits" to 0L, "Book One: Dune" to 60_970L),
                chapters.chapters.take(2).map { it.title to it.startMs },
            )
        }
}
