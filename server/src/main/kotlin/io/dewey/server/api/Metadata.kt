package io.dewey.server.api

import io.dewey.metadata.MetadataAgent
import io.dewey.metadata.MetadataAgents
import io.dewey.metadata.responses.MetadataAuthor
import io.dewey.metadata.responses.MetadataBook
import io.dewey.metadata.responses.MetadataChapters
import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import io.dewey.metadata.responses.MetadataSeries
import io.dewey.metadata.toResultCount
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.openapi.ktor.get
import io.dewey.server.repositories.LibraryRepository
import io.ktor.server.routing.Routing
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonPrimitive
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

private const val DEFAULT_SEARCH_RESULTS = 20
private const val MAX_SEARCH_RESULTS = 100

fun Routing.metadataRouting() {
    val metadataAgents by inject<MetadataAgents>()
    val libraryRepository by inject<LibraryRepository>()

    fun agentFor(libraryId: Uuid): LibraryAgent =
        libraryRepository.raw(libraryId).let { LibraryAgent(metadataAgents.forLibrary(it), it.region, it.language) }

    get<Api.Libraries.Id.Metadata.Author.Id, MetadataAuthor> {
        val (metadataAgent, region) = agentFor(it.libraryId)
        metadataAgent.getAuthorByID(providerId = it.provider, authorId = it.id, region = region)
            ?: throw ErrorResponse.notFound("Author", it.id, JsonPrimitive("Provider ${it.provider}"))
    }

    get<Api.Libraries.Id.Metadata.Author.Search, List<MetadataAuthor>> {
        val (metadataAgent, region, language) = agentFor(it.libraryId)
        metadataAgent.getAuthorByName(authorName = it.query, region = region, language = language).toList()
    }

    get<Api.Libraries.Id.Metadata.Book.Id, MetadataBook> {
        val (metadataAgent, region) = agentFor(it.libraryId)
        metadataAgent.getBookByID(providerId = it.provider, region = region, bookId = it.id)
            ?: throw ErrorResponse.notFound("Book", it.id, JsonPrimitive("Provider ${it.provider}"))
    }

    get<Api.Libraries.Id.Metadata.Book.Id.Chapters, MetadataChapters> {
        val (metadataAgent, region) = agentFor(it.libraryId)
        metadataAgent.getBookChapters(providerId = it.provider, bookId = it.id, region = region)
            ?: throw ErrorResponse.notFound("Chapters of book", it.id, JsonPrimitive("Provider ${it.provider}"))
    }

    get<Api.Libraries.Id.Metadata.Book.Search, List<MetadataBook>> {
        val (metadataAgent, region, language) = agentFor(it.libraryId)
        metadataAgent
            .getBookByName(
                bookName = it.query,
                region = region,
                keywords = it.keywords,
                authorName = it.authorName,
                narrator = it.narrator,
                // An explicit language narrows the search further than the library default does
                language = it.language ?: language,
            ).take(it.pageSize?.toResultCount(MAX_SEARCH_RESULTS) ?: DEFAULT_SEARCH_RESULTS)
            .toList()
    }

    get<Api.Libraries.Id.Metadata.Series.Id, MetadataSeries> {
        val (metadataAgent, region) = agentFor(it.libraryId)
        metadataAgent.getSeriesByID(providerId = it.provider, region = region, seriesId = it.id)
            ?: throw ErrorResponse.notFound("Series", it.id, JsonPrimitive("Provider ${it.provider}"))
    }
    get<Api.Libraries.Id.Metadata.Series.Search, List<MetadataSeries>> {
        val (metadataAgent, region, language) = agentFor(it.libraryId)
        metadataAgent
            .getSeriesByName(seriesName = it.query, region = region, authorName = it.authorName, language = language)
            .toList()
    }
}

private data class LibraryAgent(
    val agent: MetadataAgent,
    val region: MetadataRegion,
    val language: MetadataLanguage,
)
