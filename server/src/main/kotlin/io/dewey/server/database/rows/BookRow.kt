package io.dewey.server.database.rows

import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.models.Book
import io.dewey.models.ChangeType
import io.dewey.models.ChapterMark
import io.dewey.models.NamedId
import io.dewey.models.PlayStatus
import io.dewey.models.TitledId
import io.dewey.server.database.tables.AuthorBookTable
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookField
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.SeriesBookTable
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.database.tables.UserBookProgressRow
import io.dewey.server.database.tables.UserBookProgressTable
import io.dewey.server.database.tables.toUserBookProgressRow
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

data class BookRow(
    val id: Uuid,
    val library: Uuid,
    val title: String,
    val taggedName: String?,
    val releaseDate: LocalDate?,
    val publisher: String?,
    val language: MetadataLanguage?,
    val description: String?,
    val narrators: List<String>?,
    val isbn: String?,
    val provider: String?,
    val providerID: String?,
    val providerRating: Float?,
    val coverID: Uuid?,
    val genres: List<String>?,
    val chapters: List<ChapterMark>?,
    val locked: Set<BookField>,
)

fun ResultRow.toBookRow(): BookRow =
    BookRow(
        id = this[BookTable.id].value,
        library = this[BookTable.library].value,
        title = this[BookTable.title],
        taggedName = this[BookTable.taggedName],
        releaseDate = this[BookTable.releaseDate],
        publisher = this[BookTable.publisher],
        language = this[BookTable.language],
        description = this[BookTable.description],
        narrators = this[BookTable.narrators],
        isbn = this[BookTable.isbn],
        provider = this[BookTable.provider],
        providerID = this[BookTable.providerId],
        providerRating = this[BookTable.providerRating],
        coverID = this[BookTable.coverId]?.value,
        genres = this[BookTable.genres],
        chapters = this[BookTable.chapters],
        locked = this[BookTable.locked],
    )

context(_: Transaction)
fun BookTable.update(
    stored: BookRow,
    row: BookRow,
) {
    if (stored == row) return
    update({ id eq row.id }) {
        it[name] = row.title
        it[taggedName] = row.taggedName
        it[releaseDate] = row.releaseDate
        it[publisher] = row.publisher
        it[language] = row.language
        it[description] = row.description
        it[narrators] = row.narrators
        it[isbn] = row.isbn
        it[provider] = row.provider
        it[providerId] = row.providerID
        it[providerRating] = row.providerRating
        it[coverId] = row.coverID
        it[genres] = row.genres
        it[chapters] = row.chapters
        it[locked] = row.locked
    }
    record(ChangeType.UPDATED, listOf(row.id))
}

context(_: Transaction)
fun lockBookField(
    bookId: Uuid,
    field: BookField,
) {
    val locked = BookTable.select(BookTable.locked).where { BookTable.id eq bookId }.single()[BookTable.locked]
    BookTable.update({ BookTable.id eq bookId }) { it[BookTable.locked] = locked + field }
    BookTable.record(ChangeType.UPDATED, listOf(bookId))
}

context(_: Transaction)
fun BookRow.toModel(
    userId: Uuid,
    authorOrder: SortOrder = SortOrder.ASC,
    seriesOrder: SortOrder = SortOrder.ASC,
): Book = booksToModels(listOf(this), userId, authorOrder, seriesOrder).single()

context(_: Transaction)
fun booksToModels(
    rows: List<BookRow>,
    userId: Uuid,
    authorOrder: SortOrder = SortOrder.ASC,
    seriesOrder: SortOrder = SortOrder.ASC,
): List<Book> {
    if (rows.isEmpty()) return emptyList()
    val ids = rows.map { it.id }
    val authors = bookAuthors(ids)
    val series = bookSeries(ids)
    val durations = bookDurations(ids)
    val progress = bookProgress(userId, ids)
    return rows.map { row ->
        val bookProgress = progress[row.id]
        Book(
            id = row.id,
            libraryId = row.library,
            title = row.title,
            description = row.description,
            providerID = row.providerID,
            provider = row.provider,
            providerRating = row.providerRating,
            coverID = row.coverID,
            releaseDate = row.releaseDate,
            narrators = row.narrators.orEmpty(),
            isbn = row.isbn,
            language = row.language,
            publisher = row.publisher,
            authors =
                (authors[row.id] ?: emptyList())
                    .sortedBy { it.name.lowercase() }
                    .let { if (authorOrder == SortOrder.DESC) it.reversed() else it },
            series =
                (series[row.id] ?: emptyList())
                    .sortedBy { it.title.lowercase() }
                    .let { if (seriesOrder == SortOrder.DESC) it.reversed() else it },
            genres = row.genres.orEmpty(),
            durationMs = durations[row.id] ?: 0,
            positionMs = bookProgress?.positionMs ?: 0,
            status = bookProgress?.status ?: PlayStatus.UNPLAYED,
        )
    }
}

context(_: Transaction)
fun bookDurations(bookIds: List<Uuid>): Map<Uuid, Long> {
    val total = TrackTable.durationMs.sum()
    return TrackTable
        .select(TrackTable.book, total)
        .where { TrackTable.book inList bookIds }
        .groupBy(TrackTable.book)
        .associate { it[TrackTable.book].value to (it[total] ?: 0L) }
}

context(_: Transaction)
fun bookProgress(
    userId: Uuid,
    bookIds: List<Uuid>,
): Map<Uuid, UserBookProgressRow> =
    UserBookProgressTable
        .selectAll()
        .where { (UserBookProgressTable.user eq userId) and (UserBookProgressTable.book inList bookIds) }
        .associate { it[UserBookProgressTable.book].value to it.toUserBookProgressRow() }

context(_: Transaction)
fun bookAuthors(bookIds: List<Uuid>): Map<Uuid, List<NamedId>> =
    AuthorBookTable
        .join(AuthorTable, JoinType.INNER, AuthorBookTable.author, AuthorTable.id)
        .select(AuthorBookTable.book, AuthorTable.id, AuthorTable.name)
        .where { AuthorBookTable.book inList bookIds }
        .groupBy({ it[AuthorBookTable.book].value }) {
            NamedId(it[AuthorTable.id].value, it[AuthorTable.name])
        }

context(_: Transaction)
fun bookSeries(bookIds: List<Uuid>): Map<Uuid, List<TitledId>> =
    SeriesBookTable
        .join(SeriesTable, JoinType.INNER, SeriesBookTable.series, SeriesTable.id)
        .select(SeriesBookTable.book, SeriesTable.id, SeriesTable.title)
        .where { SeriesBookTable.book inList bookIds }
        .groupBy({ it[SeriesBookTable.book].value }) {
            TitledId(it[SeriesTable.id].value, it[SeriesTable.title])
        }
