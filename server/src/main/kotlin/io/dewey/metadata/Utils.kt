package io.dewey.metadata

import io.dewey.metadata.responses.MetadataSearchBook
import io.dewey.metadata.responses.MetadataSearchCount
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jsoup.parser.Parser
import java.time.format.DateTimeParseException

private val log = logger {}

internal fun ParametersBuilder.appendOptional(
    name: String,
    value: String?,
) {
    if (value != null) append(name, value)
}

internal fun MetadataSearchCount.toResultCount(maxResults: Int): Int =
    when (this) {
        MetadataSearchCount.Small -> 20
        MetadataSearchCount.Medium -> 30
        MetadataSearchCount.Large -> 40
        MetadataSearchCount.ExtraLarge -> maxResults
    }

internal fun fullTextQuery(vararg terms: String?): String? =
    terms.filterNotNull().joinToString(" ").trim().ifEmpty {
        null
    }

internal fun parseDateOrNull(
    providerName: String,
    date: String?,
    parse: (String) -> java.time.LocalDate,
): LocalDate? =
    date?.let {
        try {
            parse(it).toKotlinLocalDate()
        } catch (e: DateTimeParseException) {
            log.warn(e) { "$providerName answered with the unparsable date '$it'" }
            null
        }
    }

internal suspend fun <T> fetchChunked(
    ids: List<String>,
    chunkSize: Int,
    fetch: suspend (chunk: List<String>) -> List<T>,
): List<T> =
    coroutineScope {
        ids
            .chunked(chunkSize)
            .map { chunk -> async { fetch(chunk) } }
            .awaitAll()
            .flatten()
    }

internal fun httpsApiUrl(
    host: String,
    pathSegments: List<String>,
    parameters: Parameters = Parameters.Empty,
): Url =
    URLBuilder(
        protocol = URLProtocol.HTTPS,
        host = host,
        pathSegments = pathSegments,
        parameters = parameters,
    ).build()

private val htmlLineBreak = Regex("(?i)<br\\s*/?>|</p\\s*>")
private val htmlTag = Regex("<[^>]+>")
private val paddedNewline = Regex("[^\\S\n]*\n[^\\S\n]*")
private val repeatedNewline = Regex("\n{3,}")

/** Providers serve summaries as HTML fragments, while the metadata responses are plain text. */
internal fun htmlToText(html: String?): String? =
    html
        ?.replace(htmlLineBreak, "\n")
        ?.replace(htmlTag, "")
        ?.let { Parser.unescapeEntities(it, false) }
        ?.replace(paddedNewline, "\n")
        ?.replace(repeatedNewline, "\n\n")
        ?.trim()
        ?.ifEmpty { null }

private const val RESOLUTION_WINDOW = 5

internal fun <T> resolveInWindows(
    ids: List<String>,
    resolve: suspend (String) -> T?,
): Flow<T> =
    flow {
        ids.chunked(RESOLUTION_WINDOW).forEach { window ->
            val resolved = coroutineScope { window.map { async { resolve(it) } }.awaitAll() }
            resolved.filterNotNull().forEach { emit(it) }
        }
    }

internal fun List<MetadataSearchBook>.narratorFirst(narrator: String?): List<MetadataSearchBook> {
    if (narrator.isNullOrBlank()) return this
    return sortedByDescending { hit -> hit.narrators.maxOfOrNull { FuzzySearch.tokenSetRatio(narrator, it) } ?: 0 }
}
