package io.dewey.metadata.audiobookdb

import io.dewey.metadata.MetadataAgent
import io.dewey.metadata.MetadataHttpClient
import io.dewey.metadata.ThrottleConfig
import io.dewey.metadata.appendOptional
import io.dewey.metadata.httpsApiUrl
import io.dewey.metadata.narratorFirst
import io.dewey.metadata.resolveInWindows
import io.dewey.metadata.responses.MetadataAuthor
import io.dewey.metadata.responses.MetadataBook
import io.dewey.metadata.responses.MetadataChapters
import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import io.dewey.metadata.responses.MetadataSearchBook
import io.dewey.metadata.responses.MetadataSearchCount
import io.dewey.metadata.responses.MetadataSeries
import io.dewey.metadata.toResultCount
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Duration

class AudiobookDbMetadataAgent(
    apiKey: String? = null,
) : MetadataAgent {
    private val http =
        MetadataHttpClient(
            "AudiobookDB",
            throttle = apiKey.let {
                val requestsPerSecond = if (apiKey == null) 5 else 10
                ThrottleConfig(requests = requestsPerSecond, window = Duration.ofSeconds(1))
            },
            defaultHeaders =
                Headers.build {
                    append(HttpHeaders.UserAgent, USER_AGENT)
                    if (apiKey != null) append(API_KEY_HEADER, apiKey)
                },
        )

    override val name = AUDIOBOOKDB_PROVIDER_NAME

    override val supportedRegions = MetadataRegion.entries

    override fun getBookByName(
        bookName: String,
        region: MetadataRegion,
        keywords: String?,
        authorName: String?,
        narrator: String?,
        language: MetadataLanguage?,
    ): Flow<MetadataBook> =
        flow {
            // The releases are answered best match first, so the narrator is all that reorders them.
            val hits =
                searchBooks(region = region, keywords = keywords, title = bookName, language = language)
                    .narratorFirst(narrator)
            emitAll(resolveInWindows(hits.map { it.id.itemID }) { getBookByID(name, it, region) })
        }

    override fun getAuthorByName(
        authorName: String,
        region: MetadataRegion,
        language: MetadataLanguage?,
    ): Flow<MetadataAuthor> =
        flow {
            val ids = searchPeople(authorName).map { it.id }
            emitAll(resolveInWindows(ids) { getAuthorByID(name, it, region) })
        }

    override fun getSeriesByName(
        seriesName: String,
        region: MetadataRegion,
        authorName: String?,
        language: MetadataLanguage?,
    ): Flow<MetadataSeries> =
        flow {
            val ids = searchSeries(seriesName).map { it.id }
            emitAll(resolveInWindows(ids) { getSeriesByID(name, it, region) })
        }

    private suspend fun searchBooks(
        region: MetadataRegion,
        keywords: String? = null,
        title: String? = null,
        author: String? = null,
        narrator: String? = null,
        language: MetadataLanguage? = null,
        pageSize: MetadataSearchCount? = null,
    ): List<MetadataSearchBook> {
        val query = title ?: keywords ?: narrator ?: return emptyList()

        val url = apiUrl(listOf("releases"), searchParameters(query, pageSize))
        val items = http.getJson<List<AudiobookDbApiListRelease>>(url) ?: return emptyList()
        return items
            .filter { language == null || it.metadataLanguage == null || it.metadataLanguage == language }
            .filter { narrator == null || it.narrators.any { hit -> hit.name?.contains(narrator, true) == true } }
            .map { it.toMetadataSearchBook() }
            .filter { !it.title.isNullOrBlank() }
    }

    /** The people index, unlike the book search, actually covers author names. */
    private suspend fun searchPeople(name: String): List<AudiobookDbApiIdName> =
        http.getJson<List<AudiobookDbApiIdName>>(apiUrl(listOf("people"), searchParameters(name))) ?: emptyList()

    private suspend fun searchSeries(title: String): List<AudiobookDbApiIdTitle> =
        http.getJson<List<AudiobookDbApiIdTitle>>(apiUrl(listOf("series"), searchParameters(title))) ?: emptyList()

    override suspend fun getAuthorByID(
        providerId: String,
        authorId: String,
        region: MetadataRegion,
    ): MetadataAuthor? = http.getJson<AudiobookDbApiPerson>(apiUrl(listOf("people", authorId)))?.toMetadataAuthor()

    /** The ID is a release, not a book: a book is the work, and its releases are the audiobooks of it. */
    override suspend fun getBookByID(
        providerId: String,
        bookId: String,
        region: MetadataRegion,
    ): MetadataBook? {
        val release = release(bookId) ?: return null
        return coroutineScope {
            val book = async { release.book?.let { http.getJson<AudiobookDbApiBook>(apiUrl(listOf("books", it.id))) } }
            val rating =
                async {
                    http
                        .getJson<AudiobookDbApiRatings>(apiUrl(listOf("items", "release", release.id, "ratings")))
                        ?.chips
                        ?.firstNotNullOfOrNull { it.average }
                }
            release.toMetadataBook(book = book.await(), rating = rating.await())
        }
    }

    override suspend fun getBookByAsin(
        asin: String,
        region: MetadataRegion,
    ): MetadataBook? {
        val parameters = Parameters.build { append("external", "$AUDIBLE_EXTERNAL_CATEGORY:$asin") }
        val release =
            http.getJson<List<AudiobookDbApiListRelease>>(apiUrl(listOf("releases"), parameters))?.firstOrNull()
                ?: return null
        return getBookByID(name, release.id, region)
    }

    override suspend fun getBookChapters(
        providerId: String,
        bookId: String,
        region: MetadataRegion,
    ): MetadataChapters? = release(bookId)?.toMetadataChapters()

    private suspend fun release(releaseId: String): AudiobookDbApiReleaseDetail? =
        http.getJson<AudiobookDbApiReleaseDetail>(apiUrl(listOf("releases", releaseId)))

    override suspend fun getSeriesByID(
        providerId: String,
        seriesId: String,
        region: MetadataRegion,
    ): MetadataSeries? = http.getJson<AudiobookDbApiSeries>(apiUrl(listOf("series", seriesId)))?.toMetadataSeries()

    private fun searchParameters(
        query: String,
        pageSize: MetadataSearchCount? = null,
    ): Parameters =
        Parameters.build {
            append("q", query)
            appendOptional("take", pageSize?.toResultCount(AUDIOBOOKDB_API_MAX_RESULTS)?.toString())
        }

    private fun apiUrl(
        pathSegments: List<String>,
        parameters: Parameters = Parameters.Empty,
    ): Url = httpsApiUrl(AUDIOBOOKDB_API_HOST, listOf(AUDIOBOOKDB_API_PATH) + pathSegments, parameters)

    companion object {
        private const val AUDIOBOOKDB_API_HOST = "audiobookdb.org"
        private const val AUDIOBOOKDB_API_PATH = "api"

        private const val AUDIOBOOKDB_API_MAX_RESULTS = 100

        private const val API_KEY_HEADER = "X-API-Key"

        private const val USER_AGENT = "curl/7.68.0"
    }
}
