package io.dewey.metadata.audible

import io.dewey.metadata.responses.MetadataLanguage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the mapping of the Audible API responses against payloads captured from the live API, so a renamed field or a
 * changed structure shows up as a failing test instead of as a silently empty piece of metadata.
 */
class AudibleApiMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String) =
        checkNotNull(javaClass.getResource("/audible/$name")) { "missing fixture $name" }.readText()

    private fun product(name: String) =
        assertNotNull(json.decodeFromString<AudibleApiProductResponse>(fixture(name)).product)

    @Test
    fun `maps a book`() {
        val book = product("product.json").toMetadataBook(AudibleRegions.US, 500)

        assertEquals("B017V4IM1G", book.id.itemID)
        assertEquals("audible", book.id.provider)
        // The US region strips the ", Book 1" suffix of "Harry Potter and the Sorcerer's Stone, Book 1"
        assertEquals("Harry Potter and the Sorcerer's Stone", book.title)
        assertEquals("https://www.audible.com/pd/B017V4IM1G", book.link)
        assertEquals(listOf("J.K. Rowling"), book.authors?.map { it.name })
        assertEquals(listOf("B000AP9A6K"), book.authors?.map { it.id.itemID })
        assertEquals("https://www.audible.com/author/B000AP9A6K", book.authors?.first()?.link)
        assertEquals(listOf("Jim Dale"), book.narrators)
        assertEquals(LocalDate(2015, 11, 20), book.releaseDate)
        assertEquals("https://m.media-amazon.com/images/I/51xJbFMRsxL._SL500_.jpg", book.coverURL)
        assertEquals("Pottermore Publishing", book.publisher)
        assertEquals(MetadataLanguage.English, book.language)
        assertEquals("9781781102633", book.isbn)
        assertEquals(4.908434538641135f, book.providerRating)
    }

    @Test
    fun `maps the series of a book`() {
        val book = product("product.json").toMetadataBook(AudibleRegions.US, 500)

        assertEquals(listOf("Harry Potter", "Wizarding World Collection"), book.series?.map { it.title })
        assertEquals(listOf("B0182NWM9I", "B07CM5ZDJL"), book.series?.map { it.id.itemID })
        assertEquals(listOf(1f, 1f), book.series?.map { it.index })
        assertEquals("https://www.audible.com/series/B0182NWM9I", book.series?.first()?.link)
    }

    @Test
    fun `turns the summary into plain text`() {
        val description = assertNotNull(product("product.json").toMetadataBook(AudibleRegions.US, 500).description)

        assertTrue(description.startsWith("Jim Dale's Grammy"), "description was '${description.take(60)}...'")
        assertFalse(description.contains("<"), "description still contains markup")
        assertFalse(description.contains("&"), "description still contains entities")
    }

    @Test
    fun `strips the narrator suffix of german titles`() {
        val products = json.decodeFromString<AudibleApiProductsResponse>(fixture("search-de.json")).products
        val titles = products.map { it.toMetadataSearchBook(AudibleRegions.DE, 500).title }

        // "Harry Potter und die Heiligtuemer des Todes - Gesprochen von Rufus Beck"
        assertEquals("Harry Potter und die Heiligtümer des Todes", titles.first())
        assertTrue(titles.none { it!!.contains("Gesprochen von") }, "a title kept its narrator suffix: $titles")
    }

    @Test
    fun `keeps the language of a search hit so it can be filtered`() {
        val products = json.decodeFromString<AudibleApiProductsResponse>(fixture("search-de.json")).products

        // The catalog endpoint ignores a language parameter, so the german marketplace serves both
        assertEquals(listOf("german", "german", "english", "english", "german"), products.map { it.language })
    }

    @Test
    fun `orders the books of a series by their sequence`() {
        val series = product("series.json")

        assertEquals("BookSeries", series.contentDeliveryType)
        assertEquals(
            listOf(
                "B017V4IM1G", // 1
                "B017V4IWVG", // 2
                "B017V4JA2Q", // 3
                "B083L7WMS2", // 4, an excerpt Audible lists as a child of the series
                "B017V4NUPO", // 4
                "B017V4NMX4", // 5
                "B017V4NOZ0", // 6
                "B017WJ5ZK6", // 7
            ),
            series.seriesBookAsins(),
        )
    }

    @Test
    fun `has no title for an unknown asin`() {
        val response = json.decodeFromString<AudibleApiProductResponse>("""{"product":{"asin":"NOTANASIN"}}""")

        assertNull(assertNotNull(response.product).title)
    }
}
