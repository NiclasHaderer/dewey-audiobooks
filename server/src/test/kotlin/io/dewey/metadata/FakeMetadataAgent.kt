package io.dewey.metadata

import io.dewey.metadata.responses.MetadataAgentID
import io.dewey.metadata.responses.MetadataAuthor
import io.dewey.metadata.responses.MetadataBook
import io.dewey.metadata.responses.MetadataBookSeries
import io.dewey.metadata.responses.MetadataChapters
import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import io.dewey.metadata.responses.MetadataSearchAuthor
import io.dewey.metadata.responses.MetadataSearchBook
import io.dewey.metadata.responses.MetadataSearchCount
import io.dewey.metadata.responses.MetadataSeries
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

internal fun testId(
    itemID: String,
    provider: String = "fake",
) = MetadataAgentID(provider = provider, itemID = itemID)

internal fun searchHit(
    id: String,
    title: String = id,
    authors: List<String> = emptyList(),
    series: List<String> = emptyList(),
    narrators: List<String> = emptyList(),
    provider: String = "fake",
) = MetadataSearchBook(
    id = testId(id, provider),
    title = title,
    link = null,
    authors =
        authors.map {
            MetadataSearchAuthor(id = testId(it, provider), name = it, link = "link/$it")
        },
    series =
        series.map {
            MetadataBookSeries(id = testId(it, provider), title = it, link = "link/$it", index = null)
        },
    language = null,
    releaseDate = null,
    coverURL = null,
    narrators = narrators,
)

internal fun testBook(
    id: String,
    provider: String = "fake",
) = MetadataBook(
    id = testId(id, provider),
    title = id,
    link = null,
    authors = null,
    series = emptyList(),
    releaseDate = null,
    coverURL = null,
    description = null,
    narrators = emptyList(),
    providerRating = null,
    publisher = null,
    language = null,
    isbn = null,
    asin = null,
)

internal fun testAuthor(
    id: String,
    provider: String = "fake",
) = MetadataAuthor(
    id = testId(id, provider),
    name = id,
    link = "link/$id",
    imageURL = null,
    biography = null,
    website = null,
    bornIn = null,
    birthDate = null,
    deathDate = null,
)

internal fun testSeries(
    id: String,
    provider: String = "fake",
) = MetadataSeries(
    id = testId(id, provider),
    title = id,
    authors = null,
    link = "link/$id",
    coverURL = null,
    description = null,
    totalBooks = null,
    primaryWorks = null,
    books = null,
)

internal class FakeMetadataAgent(
    override val name: String = "fake",
    private val hits: List<MetadataSearchBook> = emptyList(),
    private val resolveBook: suspend (String) -> MetadataBook? = { id ->
        testBook(id).copy(authors = hits.firstOrNull { it.id.itemID == id }?.authors)
    },
    private val resolveAsin: suspend (String) -> MetadataBook? = { null },
    private val resolveChapters: suspend (String) -> MetadataChapters? = { null },
    private val resolveAuthor: suspend (String) -> MetadataAuthor = { testAuthor(it, name) },
    private val resolveSeries: suspend (String) -> MetadataSeries = { testSeries(it, name) },
) : SearchBasedMetadataAgent() {
    override val supportedRegions = listOf(MetadataRegion.US)

    val searchCalls = AtomicInteger()
    val searchQueries = CopyOnWriteArrayList<SearchQuery>()
    val bookLookups = CopyOnWriteArrayList<String>()
    val authorLookups = CopyOnWriteArrayList<String>()
    val seriesLookups = CopyOnWriteArrayList<String>()

    override suspend fun search(
        region: MetadataRegion,
        keywords: String?,
        title: String?,
        author: String?,
        narrator: String?,
        language: MetadataLanguage?,
        pageSize: MetadataSearchCount?,
    ): List<MetadataSearchBook> {
        searchCalls.incrementAndGet()
        searchQueries += SearchQuery(keywords = keywords, title = title, author = author)
        return hits
    }

    override suspend fun getAuthorByID(
        providerId: String,
        authorId: String,
        region: MetadataRegion,
    ): MetadataAuthor {
        authorLookups += "$authorId@$region"
        return resolveAuthor(authorId)
    }

    override suspend fun getBookByID(
        providerId: String,
        bookId: String,
        region: MetadataRegion,
    ): MetadataBook? {
        bookLookups += "$bookId@$region"
        return resolveBook(bookId)
    }

    override suspend fun getBookByAsin(
        asin: String,
        region: MetadataRegion,
    ): MetadataBook? = resolveAsin(asin)

    override suspend fun getBookChapters(
        providerId: String,
        bookId: String,
        region: MetadataRegion,
    ): MetadataChapters? = resolveChapters(bookId)

    override suspend fun getSeriesByID(
        providerId: String,
        seriesId: String,
        region: MetadataRegion,
    ): MetadataSeries {
        seriesLookups += "$seriesId@$region"
        return resolveSeries(seriesId)
    }
}

internal data class SearchQuery(
    val keywords: String?,
    val title: String?,
    val author: String?,
)
