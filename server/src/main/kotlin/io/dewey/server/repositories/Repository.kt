package io.dewey.server.repositories

import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.database.tables.AuthorBookTable
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.LibraryEntityTable
import io.dewey.server.database.tables.SeriesBookTable
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.authorLinksWithBooks
import io.dewey.server.database.tables.hide
import io.dewey.server.database.tables.seriesLinksWithBooks
import io.dewey.server.database.tables.unhide
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

// How long a book/author/series should be kept around before being properly deleted
val DEFER_DELETION_GRACE: Duration = 24.hours

// Hidden books do not count: an author or series whose every book is on its way out hides them
context(_: Transaction)
fun visiblyLinked(
    linksWithBooks: ColumnSet,
    owner: Column<EntityID<Uuid>>,
) = linksWithBooks
    .select(owner)
    .where { BookTable.deferDeletionUntil.isNull() }

context(_: Transaction)
fun requireVisible(
    owner: LibraryEntityTable,
    thing: String,
    id: Uuid,
) {
    val hidden =
        owner
            .select(owner.id)
            .where { owner.visible and (owner.id eq id) }
            .empty()
    if (hidden) throw ErrorResponse.notFound(thing, id)
}

// An orphan gets a deadline, which also hides it, anything with a book again is un-hidden. An existing
// deadline is never pushed back: touching an orphan does not buy it another grace period.
context(_: Transaction)
fun stampDeferral(
    table: LibraryEntityTable,
    scope: Op<Boolean>,
    orphaned: Op<Boolean>,
    now: Instant,
) {
    table.hide(scope and orphaned, until = now.plus(DEFER_DELETION_GRACE))
    table.unhide(scope and not(orphaned))
}

// For anything that changes which books hang off an author or series, instead of waiting for the next scan
context(_: Transaction)
fun refreshAuthorDeferral(authorIds: Collection<Uuid>) =
    stampDeferral(
        table = AuthorTable,
        scope = AuthorTable.id inList authorIds,
        orphaned = AuthorTable.id notInSubQuery visiblyLinked(authorLinksWithBooks, AuthorBookTable.author),
        now = Clock.System.now(),
    )

context(_: Transaction)
fun refreshSeriesDeferral(seriesIds: Collection<Uuid>) =
    stampDeferral(
        table = SeriesTable,
        scope = SeriesTable.id inList seriesIds,
        orphaned = SeriesTable.id notInSubQuery visiblyLinked(seriesLinksWithBooks, SeriesBookTable.series),
        now = Clock.System.now(),
    )

fun noMatch(searchedFor: String): ErrorResponse =
    ErrorResponse.missing("No metadata agent of the library had a match for '$searchedFor'")

interface Repository<RAW, NORMAL, DETAILED, PARTIAL_API> {
    val searchLimit: Int
        get() = 30

    fun raw(
        id: Uuid,
        libraryId: Uuid,
    ): RAW

    fun get(
        userId: Uuid,
        id: Uuid,
        libraryId: Uuid,
        showInvisible: Boolean = false,
    ): DETAILED

    fun getAll(
        userId: Uuid,
        libraryId: Uuid,
        order: SortOrder,
        limit: Int = 20,
        offset: Long = 0L,
        showInvisible: Boolean = false,
    ): List<NORMAL>

    fun search(
        userId: Uuid,
        query: String,
        libraryId: Uuid,
    ): List<NORMAL>

    fun modify(
        userId: Uuid,
        id: Uuid,
        libraryId: Uuid,
        partial: PARTIAL_API,
    ): NORMAL

    fun autoMatch(
        userId: Uuid,
        id: Uuid,
        libraryId: Uuid,
    ): NORMAL

    fun total(
        libraryId: Uuid,
        showInvisible: Boolean = false,
    ): Long
}
