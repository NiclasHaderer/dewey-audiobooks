package io.dewey.server.database.tables

import io.dewey.models.ChangeType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

context(_: Transaction)
fun bookIdsLinkedToSeries(seriesId: Uuid): List<Uuid> =
    SeriesBookTable
        .select(SeriesBookTable.book)
        .where { SeriesBookTable.series eq seriesId }
        .map { it[SeriesBookTable.book].value }

context(_: Transaction)
fun bookIdsLinkedToAuthor(authorId: Uuid): List<Uuid> =
    AuthorBookTable
        .select(AuthorBookTable.book)
        .where { AuthorBookTable.author eq authorId }
        .map { it[AuthorBookTable.book].value }

context(_: Transaction)
fun seriesIdsLinkedToBook(bookId: Uuid): List<Uuid> =
    SeriesBookTable
        .select(SeriesBookTable.series)
        .where { SeriesBookTable.book eq bookId }
        .map { it[SeriesBookTable.series].value }

context(_: Transaction)
fun authorIdsLinkedToBook(bookId: Uuid): List<Uuid> =
    AuthorBookTable
        .select(AuthorBookTable.author)
        .where { AuthorBookTable.book eq bookId }
        .map { it[AuthorBookTable.author].value }

context(_: Transaction)
fun replaceBookAuthors(
    bookId: Uuid,
    authorIds: Collection<Uuid>,
) = with(AuthorBookTable) {
    val wanted = authorIds.toSet()
    val existing = authorIdsLinkedToBook(bookId).toSet()

    val stale = existing - wanted
    if (stale.isNotEmpty()) deleteWhere { (book eq bookId) and (author inList stale) }
    (wanted - existing).forEach { authorId ->
        insert {
            it[book] = bookId
            it[author] = authorId
        }
    }
    AuthorTable.record(ChangeType.UPDATED, existing + wanted)
    BookTable.record(ChangeType.UPDATED, listOf(bookId))
}

// A null index leaves the one a series already has alone: a user moving a book between series says nothing
// about its position, and dropping the position the tags gave it would be a loss nobody asked for.
context(_: Transaction)
fun replaceBookSeries(
    bookId: Uuid,
    targets: Map<Uuid, Float?>,
) = with(SeriesBookTable) {
    val existing = seriesIdsLinkedToBook(bookId).toSet()
    deleteWhere { (book eq bookId) and (series notInList targets.keys) }
    targets.forEach { (seriesId, index) ->
        if (seriesId in existing) {
            if (index != null) update({ (book eq bookId) and (series eq seriesId) }) { it[seriesIndex] = index }
        } else {
            insert {
                it[book] = bookId
                it[series] = seriesId
                it[seriesIndex] = index
            }
        }
    }
    // Only the series the book left: recording the book announces the ones it is in now
    SeriesTable.record(ChangeType.UPDATED, existing)
    BookTable.record(ChangeType.UPDATED, listOf(bookId))
}

val authorLinksWithBooks
    get() = AuthorBookTable.join(BookTable, JoinType.INNER, AuthorBookTable.book, BookTable.id)

val seriesLinksWithBooks
    get() = SeriesBookTable.join(BookTable, JoinType.INNER, SeriesBookTable.book, BookTable.id)
