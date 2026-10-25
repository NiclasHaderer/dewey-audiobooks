package io.dewey.metadata.libby

import io.dewey.metadata.MetadataHttpClient
import io.dewey.metadata.SearchBasedMetadataAgent
import io.dewey.metadata.ThrottleConfig
import io.dewey.metadata.appendOptional
import io.dewey.metadata.fetchChunked
import io.dewey.metadata.fullTextQuery
import io.dewey.metadata.httpsApiUrl
import io.dewey.metadata.responses.MetadataAgentID
import io.dewey.metadata.responses.MetadataAuthor
import io.dewey.metadata.responses.MetadataBook
import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import io.dewey.metadata.responses.MetadataSearchBook
import io.dewey.metadata.responses.MetadataSearchCount
import io.dewey.metadata.responses.MetadataSeries
import io.dewey.metadata.toResultCount
import io.ktor.http.Parameters
import io.ktor.http.Url
import java.time.Duration
import kotlin.collections.get

class LibbyMetadataAgent(
    private val libraryKey: String = "brooklyn",
    private val imageSize: Int = 500,
) : SearchBasedMetadataAgent() {
    private val http =
        MetadataHttpClient("Libby", throttle = ThrottleConfig(requests = 10, window = Duration.ofSeconds(1)))

    override val name = LIBBY_PROVIDER_NAME

    override val supportedRegions = listOf(MetadataRegion.US)

    override suspend fun search(
        region: MetadataRegion,
        keywords: String?,
        title: String?,
        author: String?,
        narrator: String?,
        language: MetadataLanguage?,
        pageSize: MetadataSearchCount?,
    ): List<MetadataSearchBook> {
        val query = fullTextQuery(keywords, title, author, narrator) ?: return emptyList()

        val parameters =
            Parameters.build {
                append("query", query)
                append("mediaTypes", LIBBY_AUDIOBOOK_TYPE)
                appendOptional("language", language?.toLibbyLanguageId())
                appendOptional("perPage", pageSize?.toResultCount(LIBBY_API_MAX_RESULTS)?.toString())
            }
        val url = apiUrl(listOf("libraries", libraryKey, "media"), parameters)
        val items = http.getJson<LibbyApiSearchResponse>(url)?.items ?: return emptyList()
        return items.map { it.toMetadataSearchBook(libraryKey, imageSize) }.filter { !it.title.isNullOrBlank() }
    }

    /** Libby has no author entity, so the name has to be picked off a title the creator appears on. */
    override suspend fun getAuthorByID(
        providerId: String,
        authorId: String,
        region: MetadataRegion,
    ): MetadataAuthor? {
        val parameters =
            Parameters.build {
                append("creatorId", authorId)
                append("perPage", "1")
            }
        val url = apiUrl(listOf("libraries", libraryKey, "media"), parameters)
        val creator =
            http
                .getJson<LibbyApiSearchResponse>(url)
                ?.items
                ?.firstNotNullOfOrNull { item -> item.creators.firstOrNull { it.id?.toString() == authorId } }
                ?: return null
        return MetadataAuthor(
            id = MetadataAgentID(LIBBY_PROVIDER_NAME, authorId),
            name = creator.name,
            link = libbyAuthorLink(libraryKey, authorId),
            imageURL = null,
            biography = null,
            website = null,
            bornIn = null,
            birthDate = null,
            deathDate = null,
        )
    }

    override suspend fun getBookByID(
        providerId: String,
        bookId: String,
        region: MetadataRegion,
    ): MetadataBook? =
        http.getJson<LibbyApiMedia>(apiUrl(listOf("media", bookId)))?.toMetadataBook(libraryKey, imageSize)

    override suspend fun getSeriesByID(
        providerId: String,
        seriesId: String,
        region: MetadataRegion,
    ): MetadataSeries? {
        val series =
            http.getJson<LibbyApiSeriesResponse>(apiUrl(listOf("libraries", libraryKey, "series", seriesId)))
                ?: return null

        val orderedItems = series.items.orderedByReadingOrder()
        val mediaById = getMediaBulk(orderedItems.mapNotNull { it.id }).associateBy { it.id }
        // A series lists every edition and language of its books, so keep the most popular audiobook per reading order
        val books =
            orderedItems
                .mapNotNull { item ->
                    mediaById[item.id]?.takeIf { it.type?.id == LIBBY_AUDIOBOOK_TYPE }?.let {
                        item to
                            it
                    }
                }.distinctBy { (item, _) -> item.readingOrder ?: item.id }
                .map { (_, media) -> media.toMetadataSearchBook(libraryKey, imageSize) }

        return MetadataSeries(
            id = MetadataAgentID(LIBBY_PROVIDER_NAME, seriesId),
            title = series.name,
            link = libbySeriesLink(libraryKey, seriesId),
            description = null,
            totalBooks = books.size,
            primaryWorks = null,
            books = books,
            coverURL = books.firstOrNull()?.coverURL,
            authors = books.firstOrNull()?.authors?.mapNotNull { it.name } ?: emptyList(),
        )
    }

    private suspend fun getMediaBulk(titleIds: List<String>): List<LibbyApiMedia> =
        fetchChunked(titleIds, LIBBY_API_TITLE_BATCH_SIZE) { chunk ->
            val parameters = Parameters.build { append("titleIds", chunk.joinToString(",")) }
            http.getJson<List<LibbyApiMedia>>(apiUrl(listOf("media", "bulk"), parameters)) ?: emptyList()
        }

    private fun List<LibbyApiSeriesItem>.orderedByReadingOrder(): List<LibbyApiSeriesItem> =
        sortedWith(
            compareBy(
                { it.readingOrder?.toFloatOrNull() ?: Float.MAX_VALUE },
                { it.rank ?: Int.MAX_VALUE },
            ),
        )

    private fun apiUrl(
        pathSegments: List<String>,
        parameters: Parameters = Parameters.Empty,
    ): Url = httpsApiUrl(LIBBY_API_HOST, listOf(LIBBY_API_VERSION) + pathSegments, parameters)

    private fun MetadataLanguage.toLibbyLanguageId(): String =
        when (this) {
            MetadataLanguage.Spanish -> "es"
            MetadataLanguage.English -> "en"
            MetadataLanguage.German -> "de"
            MetadataLanguage.French -> "fr"
            MetadataLanguage.Italian -> "it"
            MetadataLanguage.Danish -> "da"
            MetadataLanguage.Finnish -> "fi"
            MetadataLanguage.Norwegian -> "no"
            MetadataLanguage.Swedish -> "sv"
            MetadataLanguage.Russian -> "ru"
        }

    companion object {
        private const val LIBBY_API_HOST = "thunder.api.overdrive.com"
        private const val LIBBY_API_VERSION = "v2"

        private const val LIBBY_API_TITLE_BATCH_SIZE = 50

        private const val LIBBY_API_MAX_RESULTS = 50

        private const val LIBBY_AUDIOBOOK_TYPE = "audiobook"
    }
}
