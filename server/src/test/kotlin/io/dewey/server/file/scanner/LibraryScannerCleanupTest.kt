package io.dewey.server.file.scanner

import io.dewey.server.DeweyTest
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.LibraryTable
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.database.tables.create
import io.dewey.server.database.tables.replaceBookAuthors
import io.dewey.server.database.tables.replaceBookSeries
import io.dewey.server.newAuthor
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.newSeries
import io.dewey.server.newTrack
import io.dewey.server.repositories.DEFER_DELETION_GRACE
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class LibraryScannerCleanupTest : DeweyTest() {
    private val cleanup = LibraryCleanup()

    private fun newBookWithTrack(
        libraryId: Uuid,
        prefix: String,
        trackScanIndex: ULong,
    ) {
        val bookAuthor = newAuthor("$prefix Author", libraryId)
        val bookSeries = newSeries("$prefix Series", libraryId)
        val newBook = newBook("$prefix Book", libraryId, authors = listOf(bookAuthor), series = listOf(bookSeries))
        newTrack(
            title = "$prefix Track",
            path = "/media/$prefix/track.mp3",
            bookId = newBook,
            libraryId = libraryId,
            scanIndex = trackScanIndex,
        )
    }

    private fun setScanIndex(
        libraryId: Uuid,
        index: ULong,
    ) = transaction { LibraryTable.update({ LibraryTable.id eq libraryId }) { it[scanIndex] = index } }

    private fun cleanup(libraryId: Uuid) {
        cleanup.removeStaleTracks(libraryId)
        cleanup.removeOrphans(libraryId)
    }

    // The window only elapses in wall clock time, so backdate the deadline the previous cleanup wrote
    private fun expireDeadlines() =
        transaction {
            val past = Clock.System.now() - DEFER_DELETION_GRACE - 60.seconds
            BookTable.update({ BookTable.deferDeletionUntil.isNotNull() }) { it[deferDeletionUntil] = past }
            AuthorTable.update({ AuthorTable.deferDeletionUntil.isNotNull() }) { it[deferDeletionUntil] = past }
            SeriesTable.update({ SeriesTable.deferDeletionUntil.isNotNull() }) { it[deferDeletionUntil] = past }
        }

    private fun counts(libraryId: Uuid) =
        transaction {
            listOf(
                TrackTable.selectAll().where { TrackTable.library eq libraryId }.count(),
                BookTable.selectAll().where { BookTable.library eq libraryId }.count(),
                AuthorTable.selectAll().where { AuthorTable.library eq libraryId }.count(),
                SeriesTable.selectAll().where { SeriesTable.library eq libraryId }.count(),
            )
        }

    private fun deferred(libraryId: Uuid) =
        transaction {
            listOf(
                BookTable
                    .selectAll()
                    .where { (BookTable.library eq libraryId) and BookTable.deferDeletionUntil.isNotNull() }
                    .count(),
                AuthorTable
                    .selectAll()
                    .where { (AuthorTable.library eq libraryId) and AuthorTable.deferDeletionUntil.isNotNull() }
                    .count(),
                SeriesTable
                    .selectAll()
                    .where { (SeriesTable.library eq libraryId) and SeriesTable.deferDeletionUntil.isNotNull() }
                    .count(),
            )
        }

    @Test
    fun `an author the user moved a book away from is hidden and then deleted`() {
        val scanned = newLibrary("scanned")
        val tagged = newAuthor("Tagged Author", scanned)
        val chosen = newAuthor("Chosen Author", scanned)
        val book = newBook("Mort", scanned, authors = listOf(tagged))
        newTrack(title = "Mort Track", path = "/media/scanned/mort.mp3", bookId = book, libraryId = scanned)
        transaction { replaceBookAuthors(book, listOf(chosen)) }

        cleanup(scanned)

        assertEquals(listOf(1L, 1L, 2L, 0L), counts(scanned), "both authors survive the first pass")
        assertEquals(listOf(0L, 1L, 0L), deferred(scanned), "the author without a book is hidden")

        expireDeadlines()
        cleanup(scanned)

        assertEquals(listOf(1L, 1L, 1L, 0L), counts(scanned), "nothing links the book to it any more")
    }

    @Test
    fun `a series the user moved a book out of is hidden and then deleted`() {
        val scanned = newLibrary("scanned")
        val tagged = newSeries("Tagged Series", scanned)
        val chosen = newSeries("Chosen Series", scanned)
        val book = newBook("Mort", scanned, series = listOf(tagged))
        newTrack(title = "Mort Track", path = "/media/scanned/mort.mp3", bookId = book, libraryId = scanned)
        transaction { replaceBookSeries(book, mapOf(chosen to null)) }

        cleanup(scanned)

        assertEquals(listOf(1L, 1L, 0L, 2L), counts(scanned), "both series survive the first pass")
        assertEquals(listOf(0L, 0L, 1L), deferred(scanned), "the series without a book is hidden")

        expireDeadlines()
        cleanup(scanned)

        assertEquals(listOf(1L, 1L, 0L, 1L), counts(scanned), "nothing links the book to it any more")
    }

    @Test
    fun `cleanup leaves other libraries untouched`() {
        val scanned = newLibrary("scanned")
        val other = newLibrary("other")
        newBookWithTrack(scanned, "scanned", trackScanIndex = 1uL)
        newBookWithTrack(other, "other", trackScanIndex = 1uL)
        setScanIndex(scanned, 2uL)

        cleanup(scanned)

        assertEquals(
            listOf(1L, 1L, 1L, 1L),
            counts(other),
            "rescanning one library must not delete another library's track, book, author or series",
        )
        assertEquals(listOf(0L, 0L, 0L), deferred(other), "another library's rows must not be scheduled either")
    }

    @Test
    fun `the first cleanup only schedules the book that is no longer on disk`() {
        val scanned = newLibrary("scanned")
        newBookWithTrack(scanned, "scanned", trackScanIndex = 1uL)
        setScanIndex(scanned, 2uL)

        cleanup(scanned)

        assertEquals(
            listOf(0L, 1L, 1L, 1L),
            counts(scanned),
            "the track is gone, but the book it orphaned is only hidden",
        )
        assertEquals(
            listOf(1L, 1L, 1L),
            deferred(scanned),
            "and its author and series, left with nothing visible, are hidden in the same pass",
        )
    }

    @Test
    fun `a second cleanup within the grace period deletes nothing`() {
        val scanned = newLibrary("scanned")
        newBookWithTrack(scanned, "scanned", trackScanIndex = 1uL)
        setScanIndex(scanned, 2uL)

        cleanup(scanned)
        cleanup.removeOrphans(scanned)

        assertEquals(
            listOf(0L, 1L, 1L, 1L),
            counts(scanned),
            "two purges minutes apart must not collapse the staging window",
        )
    }

    @Test
    fun `cleanup removes content of the scanned library that is no longer on disk`() {
        val scanned = newLibrary("scanned")
        newBookWithTrack(scanned, "scanned", trackScanIndex = 1uL)
        setScanIndex(scanned, 2uL)

        cleanup(scanned)
        expireDeadlines()
        cleanup.removeOrphans(scanned)

        assertEquals(
            listOf(0L, 0L, 0L, 0L),
            counts(scanned),
            "a track that was not touched by the scan must be removed together with its orphaned relations",
        )
    }

    @Test
    fun `a book that gets its track back before the second cleanup is un-scheduled`() {
        val scanned = newLibrary("scanned")
        newBookWithTrack(scanned, "scanned", trackScanIndex = 1uL)
        setScanIndex(scanned, 2uL)
        cleanup(scanned)

        val bookId = transaction { BookTable.selectAll().single()[BookTable.id].value }
        newTrack("scanned Track", "/media/scanned/track.mp3", bookId, scanned, scanIndex = 2uL)
        expireDeadlines()
        cleanup.removeOrphans(scanned)

        assertEquals(listOf(1L, 1L, 1L, 1L), counts(scanned), "the restored book must survive")
        assertEquals(listOf(0L, 0L, 0L), deferred(scanned), "and must no longer be staged for deletion")
    }

    @Test
    fun `cleanup keeps content that the scan touched`() {
        val scanned = newLibrary("scanned")
        newBookWithTrack(scanned, "scanned", trackScanIndex = 2uL)
        setScanIndex(scanned, 2uL)

        cleanup(scanned)

        assertEquals(listOf(1L, 1L, 1L, 1L), counts(scanned), "a touched track and its relations must survive")
        assertEquals(listOf(0L, 0L, 0L), deferred(scanned), "and nothing must be staged for deletion")
    }

    @Test
    fun `a hand made author or series is deferred like any other orphan`() {
        val scanned = newLibrary("scanned")
        val (author, series) =
            transaction {
                AuthorTable.create(scanned, "Orphan") to SeriesTable.create(scanned, "Orphan")
            }

        cleanup.removeOrphans(scanned)

        assertEquals(
            listOf(author) to listOf(series),
            transaction {
                AuthorTable.selectAll().map { it[AuthorTable.id].value } to
                    SeriesTable.selectAll().map { it[SeriesTable.id].value }
            },
            "the first cleanup only hides them",
        )
        assertEquals(listOf(0L, 1L, 1L), deferred(scanned))

        expireDeadlines()
        cleanup.removeOrphans(scanned)

        assertEquals(listOf(0L, 0L, 0L, 0L), counts(scanned), "the second one removes them")
    }

    @Test
    fun `pruneOrphans never deletes a track`() {
        val scanned = newLibrary("scanned")
        newBookWithTrack(scanned, "scanned", trackScanIndex = 1uL)
        setScanIndex(scanned, 2uL)

        cleanup.removeOrphans(scanned)

        assertEquals(
            listOf(1L, 1L, 1L, 1L),
            counts(scanned),
            "pruneOrphans runs outside a scan, so it must not act on scanIndex staleness",
        )
    }
}
