package io.dewey.server.file.scanner

import io.dewey.server.database.tables.AuthorBookTable
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.ImageTable
import io.dewey.server.database.tables.LibraryEntityTable
import io.dewey.server.database.tables.LibraryTable
import io.dewey.server.database.tables.SeriesBookTable
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.database.tables.authorLinksWithBooks
import io.dewey.server.database.tables.remove
import io.dewey.server.database.tables.seriesLinksWithBooks
import io.dewey.server.repositories.stampDeferral
import io.dewey.server.repositories.visiblyLinked
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.union
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class LibraryCleanup {
    // Only ever correct straight after a completed scan: anything the walk did not stamp is treated as gone,
    // so calling this outside that window deletes tracks that were simply not visited yet.
    fun removeStaleTracks(libraryId: Uuid): Unit =
        transaction {
            val scanIndex =
                LibraryTable
                    .select(LibraryTable.scanIndex)
                    .where { LibraryTable.id eq libraryId }
                    .single()[LibraryTable.scanIndex]
            TrackTable.remove((TrackTable.library eq libraryId) and (TrackTable.scanIndex less scanIndex))
        }

    fun removeOrphans(libraryId: Uuid): Unit =
        transaction {
            val now = Clock.System.now()
            // Books first: deleting them cascades the link rows away, which is what leaves the authors
            // and series below without books.
            val bookHasNoTrack = BookTable.id notInSubQuery TrackTable.select(TrackTable.book)
            reap(
                table = BookTable,
                libraryId = libraryId,
                hidden = bookHasNoTrack,
                deletable = bookHasNoTrack,
                now = now,
            )
            reap(
                table = AuthorTable,
                libraryId = libraryId,
                hidden = AuthorTable.id notInSubQuery visiblyLinked(authorLinksWithBooks, AuthorBookTable.author),
                deletable = AuthorTable.id notInSubQuery AuthorBookTable.select(AuthorBookTable.author),
                now = now,
            )
            reap(
                table = SeriesTable,
                libraryId = libraryId,
                hidden = SeriesTable.id notInSubQuery visiblyLinked(seriesLinksWithBooks, SeriesBookTable.series),
                deletable = SeriesTable.id notInSubQuery SeriesBookTable.select(SeriesBookTable.series),
                now = now,
            )

            removeOrphanedImages()
        }

    // One cleanup stamps the orphan with a deadline, the first cleanup past that deadline deletes it
    context(_: Transaction)
    private fun reap(
        table: LibraryEntityTable,
        libraryId: Uuid,
        hidden: Op<Boolean>,
        deletable: Op<Boolean>,
        now: Instant,
    ) {
        val inLibrary = table.library eq libraryId
        table.deleteWhere { inLibrary and deletable and (table.deferDeletionUntil lessEq now) }
        stampDeferral(table, inLibrary, hidden, now)
    }

    fun removeOrphanedImages(): Unit =
        transaction {
            ImageTable.deleteWhere {
                ImageTable.id notInSubQuery
                    BookTable
                        .referenced(BookTable.coverId)
                        .union(SeriesTable.referenced(SeriesTable.coverId))
                        .union(AuthorTable.referenced(AuthorTable.imageId))
            }
        }

    private fun Table.referenced(column: Column<EntityID<Uuid>?>) = select(column).where { column.isNotNull() }
}
