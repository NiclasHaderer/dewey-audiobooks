package io.dewey.metadata.audiobookdb

import io.dewey.metadata.htmlToText
import io.dewey.metadata.parseDateOrNull
import io.dewey.metadata.responses.MetadataAgentID
import io.dewey.metadata.responses.MetadataAuthor
import io.dewey.metadata.responses.MetadataBook
import io.dewey.metadata.responses.MetadataBookSeries
import io.dewey.metadata.responses.MetadataChapters
import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataSearchAuthor
import io.dewey.metadata.responses.MetadataSearchBook
import io.dewey.metadata.responses.MetadataSeries
import io.dewey.models.ChapterMark
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

internal const val AUDIOBOOKDB_PROVIDER_NAME = "audiobookdb"

private const val AUTHOR_ROLE = "Author"
private const val NARRATOR_ROLE = "Narrator"

@Serializable
internal data class AudiobookDbApiIdName(
    val id: String,
    val name: String? = null,
) {
    fun toMetadataAuthor(): MetadataSearchAuthor =
        MetadataSearchAuthor(
            id = MetadataAgentID(AUDIOBOOKDB_PROVIDER_NAME, id),
            name = name,
            link = authorLink(id),
        )
}

@Serializable
internal data class AudiobookDbApiIdTitle(
    val id: String,
    val title: String? = null,
) {
    fun toMetadataBookSeries(index: Float?): MetadataBookSeries =
        MetadataBookSeries(
            id = MetadataAgentID(AUDIOBOOKDB_PROVIDER_NAME, id),
            title = title,
            link = seriesLink(id),
            index = index,
        )
}

@Serializable
internal data class AudiobookDbApiNamed(
    val name: String? = null,
)

@Serializable
internal data class AudiobookDbApiImage(
    val url: String? = null,
    val sourceUrl: String? = null,
) {
    /** [url] only names the image record and answers a 404, the bytes sit behind [sourceUrl]. */
    val fileUrl: String?
        get() = sourceUrl ?: url
}

@Serializable
internal data class AudiobookDbApiCredit(
    val role: AudiobookDbApiNamed? = null,
    val person: AudiobookDbApiIdName? = null,
)

@Serializable
internal data class AudiobookDbApiListRelease(
    val id: String,
    val title: String? = null,
    val image: AudiobookDbApiImage? = null,
    val narrators: List<AudiobookDbApiIdName> = emptyList(),
    val releaseDate: String? = null,
    val publisher: AudiobookDbApiIdName? = null,
    val language: AudiobookDbApiNamed? = null,
) {
    val metadataLanguage: MetadataLanguage?
        get() = MetadataLanguage.fromTag(language?.name)

    // The authors and the series live on the book behind the release, which a search hit does not name
    fun toMetadataSearchBook(): MetadataSearchBook =
        MetadataSearchBook(
            id = MetadataAgentID(AUDIOBOOKDB_PROVIDER_NAME, id),
            title = title,
            link = releaseLink(id),
            authors = null,
            series = emptyList(),
            language = metadataLanguage,
            releaseDate = parseDate(releaseDate),
            coverURL = image?.fileUrl,
            narrators = narrators.mapNotNull { it.name },
        )
}

@Serializable
internal data class AudiobookDbApiBook(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val originallyPublishedAt: String? = null,
    val originalLanguage: AudiobookDbApiNamed? = null,
    val coverImage: AudiobookDbApiImage? = null,
    val images: List<AudiobookDbApiImage> = emptyList(),
    val people: List<AudiobookDbApiCredit> = emptyList(),
    val series: List<AudiobookDbApiBookSeries> = emptyList(),
)

@Serializable
internal data class AudiobookDbApiBookSeries(
    val ordinal: String? = null,
    val series: AudiobookDbApiIdTitle? = null,
) {
    fun toMetadataBookSeries(): MetadataBookSeries? = series?.toMetadataBookSeries(ordinal?.toFloatOrNull())
}

@Serializable
internal data class AudiobookDbApiReleaseDetail(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val isbn: String? = null,
    val releaseDate: String? = null,
    val language: AudiobookDbApiNamed? = null,
    val publisher: AudiobookDbApiIdName? = null,
    val people: List<AudiobookDbApiCredit> = emptyList(),
    val images: List<AudiobookDbApiImage> = emptyList(),
    val book: AudiobookDbApiIdTitle? = null,
    val runtimeLengthMs: Long? = null,
    val chapterDetail: AudiobookDbApiChapterDetail? = null,
) {
    fun toMetadataChapters(): MetadataChapters? {
        val runtime = runtimeLengthMs ?: return null
        val entries = chapterDetail?.chapters?.sortedBy { it.startOffsetMs }?.ifEmpty { null } ?: return null
        return MetadataChapters(runtime, entries.map { ChapterMark(it.title, it.startOffsetMs) })
    }

    fun toMetadataBook(
        book: AudiobookDbApiBook?,
        rating: Float?,
    ): MetadataBook =
        MetadataBook(
            id = MetadataAgentID(AUDIOBOOKDB_PROVIDER_NAME, id),
            title = title ?: book?.title,
            link = releaseLink(id),
            authors =
                book?.people?.filter { it.role?.name == AUTHOR_ROLE }?.mapNotNull { it.person?.toMetadataAuthor() }
                    ?: emptyList(),
            series = book?.series?.mapNotNull { it.toMetadataBookSeries() } ?: emptyList(),
            releaseDate = parseDate(releaseDate ?: book?.originallyPublishedAt),
            coverURL =
                images.firstOrNull()?.fileUrl
                    ?: book?.coverImage?.fileUrl
                    ?: book?.images?.firstOrNull()?.fileUrl,
            description = htmlToText(description) ?: htmlToText(book?.description),
            narrators = people.filter { it.role?.name == NARRATOR_ROLE }.mapNotNull { it.person?.name },
            providerRating = rating,
            publisher = publisher?.name,
            language = MetadataLanguage.fromTag((language ?: book?.originalLanguage)?.name),
            isbn = isbn,
        )
}

@Serializable
internal data class AudiobookDbApiChapterDetail(
    val chapters: List<AudiobookDbApiChapter> = emptyList(),
)

@Serializable
internal data class AudiobookDbApiChapter(
    val title: String? = null,
    val startOffsetMs: Long,
)

@Serializable
internal data class AudiobookDbApiPerson(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val deathDate: String? = null,
    val links: List<String> = emptyList(),
    val images: List<AudiobookDbApiImage> = emptyList(),
) {
    fun toMetadataAuthor(): MetadataAuthor =
        MetadataAuthor(
            id = MetadataAgentID(AUDIOBOOKDB_PROVIDER_NAME, id),
            name = name,
            link = authorLink(id),
            imageURL = images.firstOrNull()?.fileUrl,
            biography = htmlToText(description),
            website = links.firstOrNull(),
            bornIn = birthPlace,
            birthDate = parseDate(birthDate),
            deathDate = parseDate(deathDate),
        )
}

@Serializable
internal data class AudiobookDbApiSeries(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val images: List<AudiobookDbApiImage> = emptyList(),
    val books: List<AudiobookDbApiSeriesEntry> = emptyList(),
) {
    fun toMetadataSeries(): MetadataSeries {
        val seriesRef = AudiobookDbApiIdTitle(id, title)
        val books =
            books
                .sortedBy { it.ordinal?.toFloatOrNull() ?: Float.MAX_VALUE }
                .mapNotNull { it.toMetadataSearchBook(seriesRef) }
        return MetadataSeries(
            id = MetadataAgentID(AUDIOBOOKDB_PROVIDER_NAME, id),
            title = title,
            link = seriesLink(id),
            description = htmlToText(description),
            totalBooks = books.size,
            primaryWorks = null,
            books = books,
            coverURL = images.firstOrNull()?.fileUrl ?: books.firstOrNull()?.coverURL,
            authors = null,
        )
    }
}

@Serializable
internal data class AudiobookDbApiSeriesEntry(
    val ordinal: String? = null,
    val book: AudiobookDbApiSeriesBook? = null,
) {
    fun toMetadataSearchBook(series: AudiobookDbApiIdTitle): MetadataSearchBook? {
        val book = book ?: return null
        return MetadataSearchBook(
            id = MetadataAgentID(AUDIOBOOKDB_PROVIDER_NAME, book.id),
            title = book.title,
            link = bookLink(book.id),
            authors = null,
            series = listOf(series.toMetadataBookSeries(ordinal?.toFloatOrNull())),
            language = null,
            releaseDate = null,
            coverURL = book.images.firstOrNull()?.fileUrl,
            narrators = emptyList(),
        )
    }
}

@Serializable
internal data class AudiobookDbApiSeriesBook(
    val id: String,
    val title: String? = null,
    val images: List<AudiobookDbApiImage> = emptyList(),
)

@Serializable
internal data class AudiobookDbApiRatings(
    val chips: List<AudiobookDbApiRatingChip> = emptyList(),
)

@Serializable
internal data class AudiobookDbApiRatingChip(
    val average: Float? = null,
)

private fun bookLink(bookId: String) = "https://audiobookdb.org/books/$bookId"

private fun releaseLink(releaseId: String) = "https://audiobookdb.org/releases/$releaseId"

private fun authorLink(personId: String) = "https://audiobookdb.org/people/$personId"

private fun seriesLink(seriesId: String) = "https://audiobookdb.org/series/$seriesId"

private fun parseDate(date: String?): LocalDate? =
    parseDateOrNull("AudiobookDB", date) { OffsetDateTime.parse(it).toLocalDate() }
