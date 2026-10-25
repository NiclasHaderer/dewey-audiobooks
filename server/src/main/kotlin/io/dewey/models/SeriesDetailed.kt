package io.dewey.models

import io.dewey.server.database.tables.SeriesField
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
class SeriesDetailed(
    val id: Uuid,
    val libraryId: Uuid,
    val authors: List<NamedId>,
    val title: String,
    val provider: String?,
    val providerID: String?,
    val totalBooks: Int?,
    val primaryWorks: Int?,
    val coverID: Uuid?,
    val description: String?,
    val genres: List<String>,
    val bookCoverIDs: List<Uuid>,
    val yearRange: YearRange?,
    val narrators: List<String>,
    val books: List<Book>,
    val locked: List<SeriesField>,
) {
    companion object {
        fun fromModel(
            series: Series,
            books: List<Book>,
            locked: List<SeriesField>,
        ): SeriesDetailed {
            val years = books.mapNotNull { it.releaseDate }
            return SeriesDetailed(
                id = series.id,
                libraryId = series.libraryId,
                title = series.title,
                totalBooks = series.totalBooks,
                yearRange = years.minOrNull()?.let { YearRange(start = it.year, end = years.max().year) },
                narrators = books.flatMap { it.narrators }.distinct(),
                description = series.description,
                books = books,
                authors = series.authors,
                primaryWorks = series.primaryWorks,
                coverID = series.coverID,
                provider = series.provider,
                providerID = series.providerID,
                genres = series.genres,
                bookCoverIDs = series.bookCoverIDs,
                locked = locked,
            )
        }
    }
}
