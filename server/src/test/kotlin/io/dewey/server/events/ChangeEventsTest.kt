package io.dewey.server.events

import io.dewey.models.ChangeEvent
import io.dewey.models.ChangeType
import io.dewey.models.ChangedEntity
import io.dewey.server.DeweyTest
import io.dewey.server.database.rows.toBookRow
import io.dewey.server.database.rows.update
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.LibraryTable
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.database.tables.create
import io.dewey.server.database.tables.delete
import io.dewey.server.database.tables.remove
import io.dewey.server.database.tables.replaceBookSeries
import io.dewey.server.database.tables.unhide
import io.dewey.server.file.TrackManager
import io.dewey.server.file.analyzer.AudioFileAnalysisResultImpl
import io.dewey.server.file.scanner.LibraryCleanup
import io.dewey.server.newAuthor
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.newSeries
import io.dewey.server.newTrack
import io.dewey.server.repositories.AuthorRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.mp.KoinPlatform.getKoin
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ChangeEventsTest : DeweyTest() {
    // Unconfined runs the listener on the publishing thread, so a batch is recorded before its commit returns
    private fun batchesOf(block: () -> Unit): List<Set<ChangeEvent>> {
        val batches = CopyOnWriteArrayList<Set<ChangeEvent>>()
        val listening =
            CoroutineScope(Dispatchers.Unconfined).launch {
                getKoin().get<ChangeEvents>().listen().collect { batches += it.events.toSet() }
            }
        try {
            block()
        } finally {
            listening.cancel()
        }
        return batches
    }

    private fun book(
        type: ChangeType,
        id: Uuid,
        libraryId: Uuid,
    ) = ChangeEvent(ChangedEntity.BOOK, type, id, libraryId)

    private fun recounted(libraryId: Uuid) =
        ChangeEvent(ChangedEntity.LIBRARY, ChangeType.UPDATED, libraryId, libraryId)

    private fun rename(
        bookId: Uuid,
        title: String,
    ) = transaction {
        val row = BookTable
            .selectAll()
            .where { BookTable.id eq bookId }
            .single()
            .toBookRow()
        BookTable.update(row, row.copy(title = title))
    }

    @Test
    fun `a transaction announces a new book once, with the author it was added to`() {
        val libId = newLibrary("lib")
        val authorId = newAuthor("Frank Herbert", libId)
        lateinit var bookId: Uuid

        val batches = batchesOf { bookId = newBook("Dune", libId, authors = listOf(authorId)) }

        assertEquals(
            listOf(
                setOf(
                    book(ChangeType.CREATED, bookId, libId),
                    ChangeEvent(ChangedEntity.AUTHOR, ChangeType.UPDATED, authorId, libId),
                    recounted(libId),
                ),
            ),
            batches,
        )
    }

    private fun file(book: String) =
        AudioFileAnalysisResultImpl(
            title = "Chapter 1",
            authors = listOf("Frank Herbert"),
            book = book,
            durationMs = 60_000,
            path = "/media/lib/1.mp3",
            lastModified = Instant.fromEpochMilliseconds(0),
        )

    private fun bookId(title: String): Uuid =
        transaction {
            BookTable
                .select(BookTable.id)
                .where { BookTable.name eq title }
                .single()[BookTable.id]
                .value
        }

    @Test
    fun `an imported file announces its new author and book together`() {
        val libId = newLibrary("lib")

        val batches = batchesOf { getKoin().get<TrackManager>().insert(file("Dune"), libId) }

        assertEquals(
            listOf(
                setOf(
                    ChangedEntity.AUTHOR to ChangeType.CREATED,
                    ChangedEntity.BOOK to ChangeType.CREATED,
                    ChangedEntity.LIBRARY to ChangeType.UPDATED,
                ),
            ),
            batches.map { batch -> batch.mapTo(mutableSetOf()) { it.entity to it.type } },
        )
    }

    @Test
    fun `an edited book is announced with the series it is in, but not the library`() {
        val libId = newLibrary("lib")
        val seriesId = newSeries("Dune Chronicles", libId)
        val bookId = newBook("Dune", libId, series = listOf(seriesId))

        val batches = batchesOf { rename(bookId, "Dune Messiah") }

        assertEquals(
            listOf(
                setOf(
                    book(ChangeType.UPDATED, bookId, libId),
                    ChangeEvent(ChangedEntity.SERIES, ChangeType.UPDATED, seriesId, libId),
                ),
            ),
            batches,
        )
    }

    @Test
    fun `a book moved between series announces the series it left and the one it joined`() {
        val libId = newLibrary("lib")
        val leftId = newSeries("Dune Chronicles", libId)
        val joinedId = newSeries("Prelude to Dune", libId)
        val bookId = newBook("Dune", libId, series = listOf(leftId))

        val batches = batchesOf { transaction { replaceBookSeries(bookId, mapOf(joinedId to null)) } }

        assertEquals(
            listOf(
                setOf(
                    book(ChangeType.UPDATED, bookId, libId),
                    ChangeEvent(ChangedEntity.SERIES, ChangeType.UPDATED, leftId, libId),
                    ChangeEvent(ChangedEntity.SERIES, ChangeType.UPDATED, joinedId, libId),
                ),
            ),
            batches,
        )
    }

    @Test
    fun `a listener that fell behind is told that it missed batches`() =
        runBlocking {
            val events = ChangeEvents()
            val listening = CompletableDeferred<Unit>()
            val busy = CompletableDeferred<Unit>()
            val free = CompletableDeferred<Unit>()
            val received =
                async(Dispatchers.Default) {
                    events
                        .listen { listening.complete(Unit) }
                        .onEach {
                            busy.complete(Unit)
                            free.await()
                        }.take(2)
                        .toList()
                }
            listening.await()

            events.publish(emptyList())
            busy.await()
            repeat(BUFFERED_BATCHES + 10) { events.publish(emptyList()) }
            free.complete(Unit)

            assertEquals(listOf(false, true), received.await().map { it.missedPreviousEvents })
        }

    @Test
    fun `a file retagged to another book announces the book it left`() {
        val libId = newLibrary("lib")
        val trackManager = getKoin().get<TrackManager>()
        trackManager.insert(file("Dune"), libId)

        val batches = batchesOf { trackManager.insert(file("Dune Messiah"), libId) }

        assertContains(batches.single(), book(ChangeType.UPDATED, bookId("Dune"), libId))
        assertContains(batches.single(), book(ChangeType.CREATED, bookId("Dune Messiah"), libId))
    }

    @Test
    fun `an edit is announced as an update`() {
        val libId = newLibrary("lib")
        val bookId = newBook("Dune", libId)

        val batches = batchesOf { rename(bookId, "Dune Messiah") }

        assertEquals(listOf(setOf(book(ChangeType.UPDATED, bookId, libId))), batches)
    }

    @Test
    fun `a transaction that is rolled back announces nothing`() {
        val libId = newLibrary("lib")

        val batches =
            batchesOf {
                assertFailsWith<IllegalStateException> {
                    transaction {
                        BookTable.create(libId, "Dune")
                        error("the import failed")
                    }
                }
            }

        assertEquals(emptyList(), batches)
    }

    @Test
    fun `a book is announced as deleted when it is hidden and as created when it is back`() {
        val libId = newLibrary("lib")
        val bookId = newBook("Dune", libId)
        val trackId = newTrack("t1", "/media/lib/dune/1.mp3", bookId, libId)

        val lostTrack = batchesOf { transaction { TrackTable.remove(TrackTable.id eq trackId) } }
        val hidden = batchesOf { getKoin().get<LibraryCleanup>().removeOrphans(libId) }
        val editedWhileHidden = batchesOf { rename(bookId, "Dune Messiah") }
        val back =
            batchesOf {
                transaction { BookTable.unhide(BookTable.id eq bookId) }
            }

        assertEquals(listOf(setOf(book(ChangeType.UPDATED, bookId, libId))), lostTrack)
        assertEquals(listOf(setOf(book(ChangeType.DELETED, bookId, libId), recounted(libId))), hidden)
        assertEquals(emptyList(), editedWhileHidden)
        assertEquals(listOf(setOf(book(ChangeType.CREATED, bookId, libId), recounted(libId))), back)
    }

    @Test
    fun `an author that is created hidden is not announced`() {
        val libId = newLibrary("lib")

        val batches = batchesOf { getKoin().get<AuthorRepository>().createManual("Frank Herbert", libId) }

        assertEquals(emptyList(), batches)
    }

    @Test
    fun `a library is announced when it is created and deleted, without the books that go with it`() {
        lateinit var libId: Uuid
        val created = batchesOf { libId = newLibrary("lib") }
        newBook("Dune", libId)

        val deleted = batchesOf { transaction { LibraryTable.delete(libId) } }

        assertEquals(
            listOf(setOf(ChangeEvent(ChangedEntity.LIBRARY, ChangeType.CREATED, libId, libId))),
            created,
        )
        assertEquals(
            listOf(setOf(ChangeEvent(ChangedEntity.LIBRARY, ChangeType.DELETED, libId, libId))),
            deleted,
        )
    }
}
