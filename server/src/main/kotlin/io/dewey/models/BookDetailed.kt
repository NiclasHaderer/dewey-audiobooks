package io.dewey.models

import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.server.database.tables.BookField
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
class BookDetailed(
    val id: Uuid,
    val libraryId: Uuid,
    val authors: List<NamedId>,
    val series: List<TitledId>,
    val title: String,
    val provider: String?,
    val providerID: String?,
    val providerRating: Float?,
    val releaseDate: LocalDate?,
    val publisher: String?,
    val language: MetadataLanguage?,
    val description: String?,
    val narrators: List<String>,
    val isbn: String?,
    val coverID: Uuid?,
    val genres: List<String>,
    val durationMs: Long,
    val positionMs: Long,
    val status: PlayStatus,
    val tracks: List<Track>,
    val chapters: List<Chapter>,
    val locked: List<BookField>,
) {
    companion object {
        fun fromModel(
            book: Book,
            tracks: List<Track>,
            chapters: List<Chapter>,
            locked: List<BookField>,
        ) = BookDetailed(
            id = book.id,
            libraryId = book.libraryId,
            title = book.title,
            releaseDate = book.releaseDate,
            language = book.language,
            description = book.description,
            tracks = tracks,
            chapters = chapters,
            authors = book.authors,
            narrators = book.narrators,
            series = book.series,
            coverID = book.coverID,
            isbn = book.isbn,
            provider = book.provider,
            providerID = book.providerID,
            providerRating = book.providerRating,
            publisher = book.publisher,
            genres = book.genres,
            durationMs = book.durationMs,
            positionMs = book.positionMs,
            status = book.status,
            locked = locked,
        )
    }
}
