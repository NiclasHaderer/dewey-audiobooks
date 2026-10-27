package io.dewey.metadata.audible

import io.dewey.metadata.htmlToText
import io.dewey.metadata.parseDateOrNull
import io.dewey.metadata.responses.MetadataAgentID
import io.dewey.metadata.responses.MetadataBook
import io.dewey.metadata.responses.MetadataBookSeries
import io.dewey.metadata.responses.MetadataChapters
import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataSearchAuthor
import io.dewey.metadata.responses.MetadataSearchBook
import io.dewey.models.ChapterMark
import io.dewey.server.common.extensions.replaceAll
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val AUDIBLE_PROVIDER_NAME = "audible"

@Serializable
internal data class AudibleApiProductResponse(
    val product: AudibleApiProduct? = null,
)

@Serializable
internal data class AudibleApiProductsResponse(
    val products: List<AudibleApiProduct> = emptyList(),
)

@Serializable
internal data class AudibleApiContentMetadataResponse(
    @SerialName("content_metadata") val contentMetadata: AudibleApiContentMetadata? = null,
)

@Serializable
internal data class AudibleApiContentMetadata(
    @SerialName("chapter_info") val chapterInfo: AudibleApiChapterInfo? = null,
)

@Serializable
internal data class AudibleApiChapterInfo(
    @SerialName("runtime_length_ms") val runtimeLengthMs: Long? = null,
    // Audible flags timings it only estimated, which would start chapters mid-sentence in the actual audio
    @SerialName("is_accurate") val isAccurate: Boolean = true,
    val chapters: List<AudibleApiChapter> = emptyList(),
) {
    fun toMetadataChapters(): MetadataChapters? {
        val runtime = runtimeLengthMs?.takeIf { isAccurate } ?: return null
        val entries = chapters.flatMap { it.flatten() }.ifEmpty { return null }
        return MetadataChapters(runtime, entries.map { ChapterMark(it.title, it.startOffsetMs) })
    }
}

// A part can hold its chapters, which play after the part's own start
@Serializable
internal data class AudibleApiChapter(
    val title: String? = null,
    @SerialName("start_offset_ms") val startOffsetMs: Long,
    val chapters: List<AudibleApiChapter> = emptyList(),
) {
    fun flatten(): List<AudibleApiChapter> = listOf(this) + chapters.flatMap { it.flatten() }
}

@Serializable
internal data class AudibleApiProduct(
    val asin: String,
    val title: String? = null,
    val language: String? = null,
    val isbn: String? = null,
    val authors: List<AudibleApiPerson> = emptyList(),
    val narrators: List<AudibleApiPerson> = emptyList(),
    val series: List<AudibleApiSeries> = emptyList(),
    val rating: AudibleApiRating? = null,
    val relationships: List<AudibleApiRelationship> = emptyList(),
    @SerialName("content_delivery_type") val contentDeliveryType: String? = null,
    @SerialName("publisher_name") val publisherName: String? = null,
    @SerialName("publisher_summary") val publisherSummary: String? = null,
    @SerialName("merchandising_summary") val merchandisingSummary: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("issue_date") val issueDate: String? = null,
    @SerialName("product_images") val productImages: Map<String, String> = emptyMap(),
) {
    fun toMetadataBook(
        region: AudibleRegions,
        imageSize: Int,
    ): MetadataBook =
        MetadataBook(
            id = MetadataAgentID(AUDIBLE_PROVIDER_NAME, asin),
            title = cleanTitle(region),
            link = audibleBookLink(region, asin),
            authors = authors.mapNotNull { it.toMetadataAuthor(region) },
            series = series.mapNotNull { it.toMetadataBookSeries(region) },
            releaseDate = parseDate(releaseDate ?: issueDate),
            coverURL = coverURL(imageSize),
            description = htmlToText(publisherSummary ?: merchandisingSummary),
            narrators = narrators.mapNotNull { it.name },
            providerRating = rating?.overallDistribution?.averageRating,
            publisher = publisherName,
            language = MetadataLanguage.fromTag(language),
            isbn = isbn,
            asin = asin,
        )

    fun toMetadataSearchBook(
        region: AudibleRegions,
        imageSize: Int,
    ): MetadataSearchBook =
        MetadataSearchBook(
            id = MetadataAgentID(AUDIBLE_PROVIDER_NAME, asin),
            title = cleanTitle(region),
            link = audibleBookLink(region, asin),
            authors = authors.mapNotNull { it.toMetadataAuthor(region) },
            series = series.mapNotNull { it.toMetadataBookSeries(region) },
            releaseDate = parseDate(releaseDate ?: issueDate),
            coverURL = coverURL(imageSize),
            narrators = narrators.mapNotNull { it.name },
            language = MetadataLanguage.fromTag(language),
        )

    /** ASINs of the books of a series, in the order Audible sequences them. */
    fun seriesBookAsins(): List<String> =
        relationships
            .filter { it.relationshipToProduct == "child" && it.relationshipType == "series" }
            .sortedBy { it.sequence?.toFloatOrNull() ?: Float.MAX_VALUE }
            .mapNotNull { it.asin }
            .distinct()

    private fun cleanTitle(region: AudibleRegions): String? = title?.replaceAll(region.titleReplacers, "")?.trim()

    private fun coverURL(imageSize: Int): String? =
        productImages[imageSize.toString()] ?: productImages.values.firstOrNull()
}

@Serializable
internal data class AudibleApiPerson(
    val asin: String? = null,
    val name: String? = null,
) {
    fun toMetadataAuthor(region: AudibleRegions): MetadataSearchAuthor? {
        // Narrators and series placeholder products come without an ASIN, which makes them unusable as a referent
        val authorAsin = asin ?: return null
        return MetadataSearchAuthor(
            id = MetadataAgentID(AUDIBLE_PROVIDER_NAME, authorAsin),
            name = name,
            link = audibleAuthorLink(region, authorAsin),
        )
    }
}

@Serializable
internal data class AudibleApiSeries(
    val asin: String? = null,
    val title: String? = null,
    val sequence: String? = null,
) {
    fun toMetadataBookSeries(region: AudibleRegions): MetadataBookSeries? {
        val seriesAsin = asin ?: return null
        return MetadataBookSeries(
            id = MetadataAgentID(AUDIBLE_PROVIDER_NAME, seriesAsin),
            title = title,
            link = audibleSeriesLink(region, seriesAsin),
            index = sequence?.toFloatOrNull(),
        )
    }
}

@Serializable
internal data class AudibleApiRating(
    @SerialName("overall_distribution") val overallDistribution: AudibleApiRatingDistribution? = null,
)

@Serializable
internal data class AudibleApiRatingDistribution(
    @SerialName("average_rating") val averageRating: Float? = null,
)

@Serializable
internal data class AudibleApiRelationship(
    val asin: String? = null,
    val sequence: String? = null,
    @SerialName("relationship_to_product") val relationshipToProduct: String? = null,
    @SerialName("relationship_type") val relationshipType: String? = null,
)

internal fun audibleBookLink(
    region: AudibleRegions,
    asin: String,
) = "https://www.${region.host}/pd/$asin"

internal fun audibleSeriesLink(
    region: AudibleRegions,
    asin: String,
) = "https://www.${region.host}/series/$asin"

internal fun audibleAuthorLink(
    region: AudibleRegions,
    asin: String,
) = "https://www.${region.host}/author/$asin"

private fun parseDate(date: String?): LocalDate? = parseDateOrNull("Audible", date, java.time.LocalDate::parse)
