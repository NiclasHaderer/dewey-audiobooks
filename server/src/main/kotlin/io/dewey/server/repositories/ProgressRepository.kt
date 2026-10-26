package io.dewey.server.repositories

import io.dewey.models.Book
import io.dewey.models.ListeningHistoryEntry
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.config.DeweyConfig
import io.dewey.server.database.rows.booksToModels
import io.dewey.server.database.rows.toBookRow
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.ProgressLogRow
import io.dewey.server.database.tables.ProgressLogTable
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.database.tables.UserBookProgressRow
import io.dewey.server.database.tables.UserBookProgressTable
import io.dewey.server.database.tables.insert
import io.dewey.server.database.tables.toProgressLogRow
import io.dewey.server.database.tables.toUserBookProgressRow
import io.dewey.server.database.tables.update
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Once the position is this close to the end, the book counts as listened. */
private const val FINISHED_THRESHOLD_MS = 30_000L

private val WEEK: Duration = 7.days

interface ProgressRepository {
    fun upsert(
        userId: Uuid,
        libraryId: Uuid,
        bookId: Uuid,
        positionMs: Long,
    )

    fun setFinished(
        userId: Uuid,
        libraryId: Uuid,
        bookId: Uuid,
        finished: Boolean,
    )

    fun setDismissed(
        userId: Uuid,
        libraryId: Uuid,
        bookId: Uuid,
        dismissed: Boolean,
    )

    // [readable] are the libraries the request may read, which an access token can narrow below its user's
    fun continueListening(
        userId: Uuid,
        readable: Collection<Uuid>,
        limit: Int,
    ): List<Book>

    fun history(
        userId: Uuid,
        readable: Collection<Uuid>,
        limit: Int,
        offset: Long,
    ): List<ListeningHistoryEntry>

    fun historyTotal(
        userId: Uuid,
        readable: Collection<Uuid>,
    ): Long
}

class ProgressRepositoryImpl :
    ProgressRepository,
    KoinComponent {
    private val config by inject<DeweyConfig>()

    override fun upsert(
        userId: Uuid,
        libraryId: Uuid,
        bookId: Uuid,
        positionMs: Long,
    ) {
        transaction {
            if (positionMs < 0) throw ErrorResponse.userError("positionMs must not be negative")
            val durationMs = durationOfBookIn(libraryId, bookId)
            val now = Clock.System.now()
            val existing = row(userId, bookId)
            val finished = durationMs > 0 && positionMs >= durationMs - FINISHED_THRESHOLD_MS

            val updated =
                UserBookProgressRow(
                    user = userId,
                    book = bookId,
                    library = libraryId,
                    positionMs = positionMs,
                    updatedAt = now,
                    // Seeking back out of the finished window means the book is being re-listened to
                    finishedAt = if (finished) existing?.finishedAt ?: now else null,
                    // Playing a dismissed book undoes the dismissal - it belongs back in continue listening
                    dismissedAt = null,
                )
            write(existing, updated)
            logProgress(updated, now)
        }
    }

    override fun setFinished(
        userId: Uuid,
        libraryId: Uuid,
        bookId: Uuid,
        finished: Boolean,
    ) {
        transaction {
            durationOfBookIn(libraryId, bookId)
            val now = Clock.System.now()
            val existing = row(userId, bookId)
            val updated =
                UserBookProgressRow(
                    user = userId,
                    book = bookId,
                    library = libraryId,
                    // Marking a book is an annotation, not a seek. Overwriting the position here would
                    // throw away the listener's place, and un-marking could never give it back.
                    positionMs = existing?.positionMs ?: 0,
                    updatedAt = now,
                    finishedAt = if (finished) now else null,
                    // Un-finishing says "I want to keep going", so it belongs back in continue listening
                    dismissedAt = if (finished) existing?.dismissedAt else null,
                )
            write(existing, updated)
        }
    }

    override fun setDismissed(
        userId: Uuid,
        libraryId: Uuid,
        bookId: Uuid,
        dismissed: Boolean,
    ) {
        transaction {
            durationOfBookIn(libraryId, bookId)
            val now = Clock.System.now()
            val existing = row(userId, bookId) ?: throw ErrorResponse.notFound("Progress", bookId)
            val updated = existing.copy(updatedAt = now, dismissedAt = if (dismissed) now else null)
            write(existing, updated)
        }
    }

    override fun continueListening(
        userId: Uuid,
        readable: Collection<Uuid>,
        limit: Int,
    ): List<Book> =
        transaction {
            val cutoff = Clock.System.now() - WEEK * config.continueListeningWeeks
            val rows =
                UserBookProgressTable
                    .selectAll()
                    .where {
                        (UserBookProgressTable.user eq userId) and
                            (UserBookProgressTable.library inList readable) and
                            UserBookProgressTable.finishedAt.isNull() and
                            UserBookProgressTable.dismissedAt.isNull() and
                            (UserBookProgressTable.positionMs greater 0L) and
                            (UserBookProgressTable.updatedAt greaterEq cutoff)
                    }.orderBy(UserBookProgressTable.updatedAt to SortOrder.DESC)
                    .limit(limit)
                    .map { it.toUserBookProgressRow() }

            val books = booksById(rows.map { it.book }, userId, readable)
            rows.mapNotNull { books[it.book] }
        }

    override fun history(
        userId: Uuid,
        readable: Collection<Uuid>,
        limit: Int,
        offset: Long,
    ): List<ListeningHistoryEntry> =
        transaction {
            val rows =
                ProgressLogTable
                    .selectAll()
                    .where {
                        (ProgressLogTable.user eq userId) and
                            (ProgressLogTable.library inList readable)
                    }.orderBy(ProgressLogTable.at to SortOrder.DESC)
                    .offset(offset)
                    .limit(limit)
                    .map { it.toProgressLogRow() }

            val books = booksById(rows.map { it.book }, userId, readable)
            rows.mapNotNull { row ->
                val book = books[row.book] ?: return@mapNotNull null
                ListeningHistoryEntry(
                    id = row.id,
                    book = book,
                    positionMs = row.positionMs,
                    at = row.at,
                )
            }
        }

    override fun historyTotal(
        userId: Uuid,
        readable: Collection<Uuid>,
    ): Long =
        transaction {
            ProgressLogTable
                .selectAll()
                .where {
                    (ProgressLogTable.user eq userId) and
                        (ProgressLogTable.library inList readable)
                }.count()
        }

    context(_: Transaction)
    private fun logProgress(
        progress: UserBookProgressRow,
        now: Instant,
    ) {
        ProgressLogTable.insert(
            ProgressLogRow(
                id = Uuid.random(),
                user = progress.user,
                book = progress.book,
                library = progress.library,
                positionMs = progress.positionMs,
                at = now,
            ),
        )
    }

    context(_: Transaction)
    private fun write(
        existing: UserBookProgressRow?,
        updated: UserBookProgressRow,
    ) {
        if (existing == null) UserBookProgressTable.insert(updated) else UserBookProgressTable.update(updated)
    }

    context(_: Transaction)
    private fun booksById(
        bookIds: List<Uuid>,
        userId: Uuid,
        readable: Collection<Uuid>,
    ): Map<Uuid, Book> {
        // The progress log holds one row per write, so a page of history is mostly the same few books
        // repeated. Deduplicating keeps the IN list proportional to the books, not to the rows.
        val ids = bookIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        val rows =
            BookTable
                .selectAll()
                .where { (BookTable.id inList ids) and (BookTable.library inList readable) }
                .map { it.toBookRow() }
        return booksToModels(rows, userId).associateBy { it.id }
    }

    context(_: Transaction)
    private fun row(
        userId: Uuid,
        bookId: Uuid,
    ): UserBookProgressRow? =
        UserBookProgressTable
            .selectAll()
            .where { (UserBookProgressTable.user eq userId) and (UserBookProgressTable.book eq bookId) }
            .firstOrNull()
            ?.toUserBookProgressRow()

    context(_: Transaction)
    private fun durationOfBookIn(
        libraryId: Uuid,
        bookId: Uuid,
    ): Long {
        val total = TrackTable.durationMs.sum()
        val row =
            (BookTable leftJoin TrackTable)
                .select(BookTable.id, total)
                .where { (BookTable.id eq bookId) and (BookTable.library eq libraryId) }
                .groupBy(BookTable.id)
                .firstOrNull() ?: throw ErrorResponse.notFound("Book", bookId)
        // Can be null if the book has no tracks
        return row[total] ?: 0
    }
}
