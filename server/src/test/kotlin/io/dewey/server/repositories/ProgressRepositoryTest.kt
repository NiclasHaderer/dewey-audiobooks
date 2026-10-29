package io.dewey.server.repositories

import io.dewey.models.LibraryPermissionLevel
import io.dewey.models.PlayStatus
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.DeweyTest
import io.dewey.server.config.DeweyConfig
import io.dewey.server.database.tables.ProgressLogTable
import io.dewey.server.database.tables.UserBookProgressRow
import io.dewey.server.database.tables.UserBookProgressTable
import io.dewey.server.database.tables.insert
import io.dewey.server.database.tables.toProgressLogRow
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.newTrack
import io.dewey.server.newUser
import io.dewey.server.plugins.auth.DeweyPrincipalImpl
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.mp.KoinPlatform.getKoin
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ProgressRepositoryTest : DeweyTest() {
    private val progressRepository by lazy { getKoin().get<ProgressRepository>() }
    private val bookRepository by lazy { getKoin().get<BookRepository>() }
    private val seriesRepository by lazy { getKoin().get<SeriesRepository>() }
    private val genreRepository by lazy { getKoin().get<GenreRepository>() }
    private val narratorRepository by lazy { getKoin().get<NarratorRepository>() }

    override fun configure(dataDir: Path) = DeweyConfig(dataDir = dataDir, continueListeningWeeks = 2)

    private lateinit var libId: Uuid
    private lateinit var userId: Uuid
    private lateinit var bookId: Uuid

    /** newTrack seeds 60_000 ms per track, so three tracks make a three minute book. */
    private val durationMs = 180_000L

    private fun principal(user: Uuid) = DeweyPrincipalImpl(user, accessToken = null)

    @BeforeTest
    fun seed() {
        libId = newLibrary("lib", folders = listOf("/media/books"))
        userId = newUser("listener", libraries = mapOf(libId to LibraryPermissionLevel.READONLY))
        bookId = newBook("Dune", libId, narrators = listOf("Simon Vance"), genres = listOf("Sci-Fi"))
        repeat(3) { newTrack("t$it", "/media/books/dune/$it.mp3", bookId, libId, trackNr = it + 1) }
    }

    @Test
    fun `upsert stores a resume position that reads back`() {
        progressRepository.upsert(userId, libId, bookId, 42_000)

        val progress = progressOf()
        assertEquals(42_000, progress.positionMs)
        assertEquals(durationMs, progress.durationMs)
        assertEquals(PlayStatus.IN_PROGRESS, progress.status)
    }

    @Test
    fun `a book with no progress row reads back as unplayed`() {
        val progress = progressOf()
        assertEquals(0, progress.positionMs)
        assertEquals(durationMs, progress.durationMs)
        assertEquals(PlayStatus.UNPLAYED, progress.status)
    }

    @Test
    fun `crossing the finished threshold marks the book listened`() {
        progressRepository.upsert(userId, libId, bookId, durationMs - 40_000)
        assertEquals(PlayStatus.IN_PROGRESS, progressOf().status)

        progressRepository.upsert(userId, libId, bookId, durationMs - 20_000)
        assertEquals(PlayStatus.FINISHED, progressOf().status)
    }

    @Test
    fun `seeking back out of the finished window resumes the book`() {
        progressRepository.upsert(userId, libId, bookId, durationMs)
        assertEquals(PlayStatus.FINISHED, progressOf().status)

        progressRepository.upsert(userId, libId, bookId, 10_000)
        assertEquals(PlayStatus.IN_PROGRESS, progressOf().status)
    }

    @Test
    fun `marking a book keeps the listener's place`() {
        progressRepository.upsert(userId, libId, bookId, 42_000)

        progressRepository.setFinished(userId, libId, bookId, finished = true)
        val listened = progressOf()
        assertEquals(PlayStatus.FINISHED, listened.status)
        assertEquals(42_000, listened.positionMs, "marking finished must not move the position")

        progressRepository.setFinished(userId, libId, bookId, finished = false)
        val unlistened = progressOf()
        assertEquals(PlayStatus.IN_PROGRESS, unlistened.status)
        assertEquals(42_000, unlistened.positionMs, "changing your mind must give the place back")
    }

    @Test
    fun `writing position zero is the reset`() {
        progressRepository.upsert(userId, libId, bookId, 42_000)
        progressRepository.setDismissed(userId, libId, bookId, dismissed = true)

        progressRepository.upsert(userId, libId, bookId, 0)

        val reset = progressOf()
        assertEquals(0, reset.positionMs)
        assertEquals(PlayStatus.UNPLAYED, reset.status)
        assertTrue(
            progressRepository.continueListening(principal(userId), 10).isEmpty(),
            "nothing to continue at zero",
        )
    }

    @Test
    fun `mark listened works on a book that was never opened`() {
        progressRepository.setFinished(userId, libId, bookId, finished = true)
        assertEquals(PlayStatus.FINISHED, progressOf().status)
        assertTrue(progressRepository.continueListening(principal(userId), limit = 10).isEmpty())
    }

    @Test
    fun `dismissal hides a book from continue listening but keeps it resumable`() {
        progressRepository.upsert(userId, libId, bookId, 42_000)
        assertEquals(listOf(bookId), progressRepository.continueListening(principal(userId), 10).map { it.id })

        progressRepository.setDismissed(userId, libId, bookId, dismissed = true)
        assertTrue(progressRepository.continueListening(principal(userId), 10).isEmpty())
        assertEquals(42_000, progressOf().positionMs)

        progressRepository.setDismissed(userId, libId, bookId, dismissed = false)
        assertEquals(listOf(bookId), progressRepository.continueListening(principal(userId), 10).map { it.id })
    }

    @Test
    fun `playing a dismissed book brings it back into continue listening`() {
        progressRepository.upsert(userId, libId, bookId, 42_000)
        progressRepository.setDismissed(userId, libId, bookId, dismissed = true)
        assertTrue(progressRepository.continueListening(principal(userId), 10).isEmpty())

        progressRepository.upsert(userId, libId, bookId, 50_000)
        assertEquals(listOf(bookId), progressRepository.continueListening(principal(userId), 10).map { it.id })
    }

    @Test
    fun `progress older than the configured window drops out of continue listening`() {
        progressRepository.upsert(userId, libId, bookId, 42_000)
        backdate(bookId, Clock.System.now() - WEEK * 3)

        assertTrue(progressRepository.continueListening(principal(userId), 10).isEmpty())
        assertEquals(42_000, progressOf().positionMs)
    }

    @Test
    fun `every progress write appends one log row verbatim`() {
        progressRepository.upsert(userId, libId, bookId, 0)
        progressRepository.upsert(userId, libId, bookId, 30_000)
        // A backward seek is recorded as-is; interpreting it is a query's job, not the writer's
        progressRepository.upsert(userId, libId, bookId, 5_000)

        assertEquals(listOf(0L, 30_000L, 5_000L), logRows().map { it.positionMs })
    }

    @Test
    fun `history exposes the log entries with their book`() {
        progressRepository.upsert(userId, libId, bookId, 30_000)

        val entry = progressRepository.history(principal(userId), limit = 10, offset = 0).single()
        assertEquals(bookId, entry.book.id)
        assertEquals(30_000, entry.positionMs)
        assertEquals(1, progressRepository.historyTotal(principal(userId)))
    }

    @Test
    fun `a progress row pointing outside the readable libraries surfaces nothing`() {
        val otherLib = newLibrary("other", folders = listOf("/media/other"))
        val otherBook = newBook("Top Secret", otherLib)
        repeat(3) { newTrack("s$it", "/media/other/$it.mp3", otherBook, otherLib, trackNr = it + 1) }

        // Forge the row the book-in-library check normally prevents
        transaction {
            UserBookProgressTable.insert(
                UserBookProgressRow(
                    user = userId,
                    book = otherBook,
                    library = libId,
                    positionMs = 42_000,
                    updatedAt = Clock.System.now(),
                    finishedAt = null,
                    dismissedAt = null,
                ),
            )
        }

        assertTrue(
            progressRepository.continueListening(principal(userId), 10).none { it.id == otherBook },
            "a book outside the readable libraries must never be resolved",
        )
    }

    @Test
    fun `a book reached through the wrong library is rejected`() {
        val otherLib = newLibrary("other", folders = listOf("/media/other"))
        val otherBook = newBook("Elsewhere", otherLib)

        // The library id in the URL is one the caller may read, so only the book-to-library check catches this
        assertFailsWith<ErrorResponse> { progressRepository.upsert(userId, libId, otherBook, 1_000) }
    }

    @Test
    fun `book duration and status show up wherever a book is returned`() {
        progressRepository.upsert(userId, libId, bookId, 42_000)

        val listed = bookRepository.getAll(userId, libId, SortOrder.ASC, 20, 0).single { it.id == bookId }
        assertEquals(durationMs, listed.durationMs)
        assertEquals(42_000, listed.positionMs)
        assertEquals(PlayStatus.IN_PROGRESS, listed.status)

        val detailed = bookRepository.get(userId, bookId, libId)
        assertEquals(PlayStatus.IN_PROGRESS, detailed.status)

        val viaGenre = genreRepository.get(userId, "Sci-Fi", libId).books.single { it.id == bookId }
        assertEquals(PlayStatus.IN_PROGRESS, viaGenre.status)
        assertEquals(durationMs, viaGenre.durationMs)

        val viaNarrator = narratorRepository.get(userId, "Simon Vance", libId).books.single { it.id == bookId }
        assertEquals(PlayStatus.IN_PROGRESS, viaNarrator.status)
    }

    @Test
    fun `progress from another user is not visible`() {
        val other = newUser("other", libraries = mapOf(libId to LibraryPermissionLevel.READONLY))
        progressRepository.upsert(userId, libId, bookId, 42_000)

        assertEquals(PlayStatus.UNPLAYED, bookRepository.get(other, bookId, libId).status)
        assertTrue(progressRepository.continueListening(principal(other), 10).isEmpty())
    }

    /** Progress has no read endpoint of its own; it is read off the book. */
    private fun progressOf(book: Uuid = bookId) = bookRepository.get(userId, book, libId)

    private fun logRows() =
        transaction {
            ProgressLogTable
                .selectAll()
                .where { ProgressLogTable.book eq bookId }
                .orderBy(ProgressLogTable.at to SortOrder.ASC)
                .map { it.toProgressLogRow() }
        }

    private fun backdate(
        book: Uuid,
        to: Instant,
    ) = transaction {
        UserBookProgressTable.update({ UserBookProgressTable.book eq book }) { it[updatedAt] = to }
    }

    private companion object {
        val WEEK: Duration = 7.days
    }
}
