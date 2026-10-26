package io.dewey.metadata

import io.dewey.metadata.responses.MetadataAuthor
import io.dewey.metadata.responses.MetadataBook
import io.dewey.metadata.responses.MetadataChapters
import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import io.dewey.metadata.responses.MetadataSeries
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import me.xdrop.fuzzywuzzy.FuzzySearch

private val log = logger {}

class MetadataAgentWrapper(
    private val agentList: List<MetadataAgent>,
    private val combineFields: Boolean = false,
) : MetadataAgent {
    override val name = agentList.joinToString(", ") { it.name }
    override val supportedRegions: List<MetadataRegion>
        get() = agentList.flatMap { it.supportedRegions }.distinct()

    private val agentsByName by lazy { agentList.associateBy { it.name } }

    override suspend fun getAuthorByID(
        providerId: String,
        authorId: String,
        region: MetadataRegion,
    ): MetadataAuthor? = agent(providerId)?.getAuthorByID(providerId = providerId, authorId = authorId, region = region)

    override suspend fun getBookByID(
        providerId: String,
        bookId: String,
        region: MetadataRegion,
    ): MetadataBook? = agent(providerId)?.getBookByID(providerId = providerId, bookId = bookId, region = region)

    override suspend fun getBookChapters(
        providerId: String,
        bookId: String,
        region: MetadataRegion,
    ): MetadataChapters? = agent(providerId)?.getBookChapters(providerId = providerId, bookId = bookId, region = region)

    override suspend fun getSeriesByID(
        providerId: String,
        seriesId: String,
        region: MetadataRegion,
    ): MetadataSeries? = agent(providerId)?.getSeriesByID(providerId = providerId, seriesId = seriesId, region = region)

    override fun getAuthorByName(
        authorName: String,
        region: MetadataRegion,
        language: MetadataLanguage?,
    ): Flow<MetadataAuthor> =
        fromEachAgent { it.getAuthorByName(authorName = authorName, region = region, language = language) }

    override fun getBookByName(
        bookName: String,
        region: MetadataRegion,
        keywords: String?,
        authorName: String?,
        narrator: String?,
        language: MetadataLanguage?,
    ): Flow<MetadataBook> =
        fromEachAgent {
            it.getBookByName(
                bookName = bookName,
                region = region,
                keywords = keywords,
                authorName = authorName,
                narrator = narrator,
                language = language,
            )
        }

    override fun getSeriesByName(
        seriesName: String,
        region: MetadataRegion,
        authorName: String?,
        language: MetadataLanguage?,
    ): Flow<MetadataSeries> =
        fromEachAgent {
            it.getSeriesByName(seriesName = seriesName, region = region, authorName = authorName, language = language)
        }

    suspend fun bestAuthorMatch(
        authorName: String,
        region: MetadataRegion,
        language: MetadataLanguage?,
    ): MetadataAuthor? =
        bestMatch(MetadataAuthor::fillFrom) {
            it.getAuthorByName(authorName = authorName, region = region, language = language)
        }

    suspend fun bestBookMatch(
        bookName: String,
        region: MetadataRegion,
        keywords: String? = null,
        authorName: String? = null,
        narrator: String? = null,
        language: MetadataLanguage? = null,
        asin: String? = null,
    ): MetadataBook? =
        bestMatch(
            MetadataBook::fillFrom,
            accept = { it.isWrittenBy(authorName) },
            direct = { agent -> asin?.let { agent.getBookByAsin(it, region) } },
        ) {
            it.getBookByName(
                bookName = bookName,
                region = region,
                keywords = keywords,
                authorName = authorName,
                narrator = narrator,
                language = language,
            )
        }

    suspend fun bestSeriesMatch(
        seriesName: String,
        region: MetadataRegion,
        authorName: String?,
        language: MetadataLanguage?,
    ): MetadataSeries? =
        bestMatch(MetadataSeries::fillFrom) {
            it.getSeriesByName(seriesName = seriesName, region = region, authorName = authorName, language = language)
        }

    private suspend fun <T : Any> bestMatch(
        fill: (T, T) -> T,
        accept: (T) -> Boolean = { true },
        direct: suspend (MetadataAgent) -> T? = { null },
        query: (MetadataAgent) -> Flow<T>,
    ): T? {
        var best: T? = null
        for (agent in agentList) {
            // An identifier names the item, so its hit is not second-guessed like a search hit is
            val candidate = direct(agent) ?: query(agent).firstOrNull(accept) ?: continue
            if (!combineFields) return candidate
            best = best?.let { fill(it, candidate) } ?: candidate
        }
        return best
    }

    /**
     * Every agent hands out its results best match first, so they are concatenated instead of ranked again: ranking
     * across agents would mean resolving every result of every agent before the first one can be handed out.
     */
    private fun <T> fromEachAgent(query: (MetadataAgent) -> Flow<T>): Flow<T> =
        flow {
            agentList.forEach { emitAll(query(it)) }
        }

    private fun agent(providerId: String): MetadataAgent? {
        val agent = agentsByName[providerId]
        if (agent == null) {
            log.warn { "No metadata agent named '$providerId' (available: ${agentsByName.keys})" }
        }
        return agent
    }
}

private const val AUTHOR_MATCH_THRESHOLD = 80

// Not every agent can search by author: AudiobookDB only searches titles, so its best hit for a common title is
// whichever book it ranks first. A candidate that names no author is rejected as well, it cannot be told apart.
private fun MetadataBook.isWrittenBy(authorName: String?): Boolean =
    authorName.isNullOrBlank() ||
        authors.orEmpty().any { FuzzySearch.tokenSetRatio(authorName, it.name ?: "") >= AUTHOR_MATCH_THRESHOLD }

private fun MetadataAuthor.fillFrom(other: MetadataAuthor): MetadataAuthor =
    MetadataAuthor(
        id = id,
        name = name ?: other.name,
        link = link.ifBlank { other.link },
        imageURL = imageURL ?: other.imageURL,
        biography = biography ?: other.biography,
        website = website ?: other.website,
        bornIn = bornIn ?: other.bornIn,
        birthDate = birthDate ?: other.birthDate,
        deathDate = deathDate ?: other.deathDate,
    )

private fun MetadataBook.fillFrom(other: MetadataBook): MetadataBook =
    MetadataBook(
        id = id,
        title = title ?: other.title,
        link = link ?: other.link,
        authors = authors?.ifEmpty { null } ?: other.authors,
        series = series.ifEmpty { other.series },
        releaseDate = releaseDate ?: other.releaseDate,
        coverURL = coverURL ?: other.coverURL,
        description = description ?: other.description,
        narrators = narrators.ifEmpty { other.narrators },
        providerRating = providerRating ?: other.providerRating,
        publisher = publisher ?: other.publisher,
        language = language ?: other.language,
        isbn = isbn ?: other.isbn,
        asin = asin ?: other.asin,
    )

private fun MetadataSeries.fillFrom(other: MetadataSeries): MetadataSeries =
    MetadataSeries(
        id = id,
        title = title ?: other.title,
        authors = authors?.ifEmpty { null } ?: other.authors,
        link = link.ifBlank { other.link },
        coverURL = coverURL ?: other.coverURL,
        description = description ?: other.description,
        totalBooks = totalBooks ?: other.totalBooks,
        primaryWorks = primaryWorks ?: other.primaryWorks,
        books = books?.ifEmpty { null } ?: other.books,
    )
