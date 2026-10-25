package io.dewey.server.database.rows

import io.dewey.models.ChangeType
import io.dewey.models.NamedId
import io.dewey.models.Series
import io.dewey.server.database.tables.AuthorBookTable
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.SeriesBookTable
import io.dewey.server.database.tables.SeriesField
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.seriesLinksWithBooks
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

data class SeriesRow(
    val id: Uuid,
    val library: Uuid,
    val title: String,
    val taggedName: String?,
    val totalBooks: Int?,
    val primaryWorks: Int?,
    val description: String?,
    val provider: String?,
    val providerID: String?,
    val coverID: Uuid?,
    val locked: Set<SeriesField>,
)

fun ResultRow.toSeriesRow(): SeriesRow =
    SeriesRow(
        id = this[SeriesTable.id].value,
        library = this[SeriesTable.library].value,
        title = this[SeriesTable.title],
        taggedName = this[SeriesTable.taggedName],
        totalBooks = this[SeriesTable.totalBooks],
        primaryWorks = this[SeriesTable.primaryWorks],
        description = this[SeriesTable.description],
        provider = this[SeriesTable.provider],
        providerID = this[SeriesTable.providerId],
        coverID = this[SeriesTable.coverId]?.value,
        locked = this[SeriesTable.locked],
    )

context(_: Transaction)
fun SeriesTable.update(
    stored: SeriesRow,
    row: SeriesRow,
) {
    if (stored == row) return
    update({ id eq row.id }) {
        it[name] = row.title
        it[taggedName] = row.taggedName
        it[totalBooks] = row.totalBooks
        it[primaryWorks] = row.primaryWorks
        it[description] = row.description
        it[provider] = row.provider
        it[providerId] = row.providerID
        it[coverId] = row.coverID
        it[locked] = row.locked
    }
    record(ChangeType.UPDATED, listOf(row.id))
}

context(_: Transaction)
fun SeriesRow.toModel(authorOrder: SortOrder = SortOrder.ASC): Series =
    seriesToModels(listOf(this), authorOrder).single()

context(_: Transaction)
fun seriesToModels(
    rows: List<SeriesRow>,
    authorOrder: SortOrder = SortOrder.ASC,
): List<Series> {
    if (rows.isEmpty()) return emptyList()
    val ids = rows.map { it.id }
    val authors = seriesAuthors(ids)
    val books = seriesBooks(ids)
    return rows.map { row ->
        val ownBooks = books[row.id].orEmpty()
        Series(
            id = row.id,
            libraryId = row.library,
            title = row.title,
            description = row.description,
            providerID = row.providerID,
            provider = row.provider,
            coverID = row.coverID,
            primaryWorks = row.primaryWorks,
            totalBooks = row.totalBooks,
            authors =
                (authors[row.id] ?: emptyList())
                    .sortedBy { it.name.lowercase() }
                    .let { if (authorOrder == SortOrder.DESC) it.reversed() else it },
            genres = ownBooks.flatMap { it.genres }.distinctBy { it.lowercase() },
            bookCoverIDs = ownBooks.mapNotNull { it.coverID },
        )
    }
}

context(_: Transaction)
fun seriesAuthors(seriesIds: List<Uuid>): Map<Uuid, List<NamedId>> =
    SeriesBookTable
        .join(AuthorBookTable, JoinType.INNER, SeriesBookTable.book, AuthorBookTable.book)
        .join(AuthorTable, JoinType.INNER, AuthorBookTable.author, AuthorTable.id)
        .select(SeriesBookTable.series, AuthorTable.id, AuthorTable.name)
        .where { SeriesBookTable.series inList seriesIds }
        .groupBy({ it[SeriesBookTable.series].value }) {
            NamedId(it[AuthorTable.id].value, it[AuthorTable.name])
        }.mapValues { (_, authors) -> authors.distinctBy { it.id } }

data class SeriesBook(
    val coverID: Uuid?,
    val genres: List<String>,
)

context(_: Transaction)
fun seriesBooks(seriesIds: List<Uuid>): Map<Uuid, List<SeriesBook>> =
    seriesLinksWithBooks
        .select(SeriesBookTable.series, SeriesBookTable.seriesIndex, BookTable.coverId, BookTable.genres)
        .where { (SeriesBookTable.series inList seriesIds) and BookTable.visible }
        .orderBy(SeriesBookTable.seriesIndex to SortOrder.ASC_NULLS_LAST)
        .groupBy({ it[SeriesBookTable.series].value }) {
            SeriesBook(it[BookTable.coverId]?.value, it[BookTable.genres].orEmpty())
        }
