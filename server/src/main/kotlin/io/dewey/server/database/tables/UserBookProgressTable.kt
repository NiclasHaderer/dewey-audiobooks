package io.dewey.server.database.tables

import io.dewey.models.PlayStatus
import io.dewey.server.database.extensions.timestampMillis
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

object UserBookProgressTable : CompositeIdTable("user_book_progress") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val book = reference("book_id", BookTable, onDelete = ReferenceOption.CASCADE)
    val library = reference("library_id", LibraryTable, onDelete = ReferenceOption.CASCADE).index()
    val positionMs = long("position_ms")
    val updatedAt = timestampMillis("updated_at")
    val finishedAt = timestampMillis("finished_at").nullable()
    val dismissedAt = timestampMillis("dismissed_at").nullable()

    override val primaryKey = PrimaryKey(user, book)

    init {
        addIdColumn(user)
        addIdColumn(book)
        index(false, user, updatedAt)
    }
}

data class UserBookProgressRow(
    val user: Uuid,
    val book: Uuid,
    val library: Uuid,
    val positionMs: Long,
    val updatedAt: Instant,
    val finishedAt: Instant?,
    val dismissedAt: Instant?,
) {
    val status: PlayStatus
        get() =
            when {
                finishedAt != null -> PlayStatus.FINISHED
                positionMs > 0 -> PlayStatus.IN_PROGRESS
                else -> PlayStatus.UNPLAYED
            }
}

fun ResultRow.toUserBookProgressRow(): UserBookProgressRow =
    UserBookProgressRow(
        user = this[UserBookProgressTable.user].value,
        book = this[UserBookProgressTable.book].value,
        library = this[UserBookProgressTable.library].value,
        positionMs = this[UserBookProgressTable.positionMs],
        updatedAt = this[UserBookProgressTable.updatedAt],
        finishedAt = this[UserBookProgressTable.finishedAt],
        dismissedAt = this[UserBookProgressTable.dismissedAt],
    )

context(_: Transaction)
fun UserBookProgressTable.insert(row: UserBookProgressRow) {
    insert { write(it, row) }
}

context(_: Transaction)
fun UserBookProgressTable.update(row: UserBookProgressRow) {
    update({ (user eq row.user) and (book eq row.book) }) { write(it, row) }
}

private fun write(
    stmt: UpdateBuilder<*>,
    row: UserBookProgressRow,
) {
    stmt[UserBookProgressTable.user] = row.user
    stmt[UserBookProgressTable.book] = row.book
    stmt[UserBookProgressTable.library] = row.library
    stmt[UserBookProgressTable.positionMs] = row.positionMs
    stmt[UserBookProgressTable.updatedAt] = row.updatedAt
    stmt[UserBookProgressTable.finishedAt] = row.finishedAt
    stmt[UserBookProgressTable.dismissedAt] = row.dismissedAt
}
