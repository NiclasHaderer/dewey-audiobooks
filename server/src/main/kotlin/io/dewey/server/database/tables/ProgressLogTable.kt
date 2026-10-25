package io.dewey.server.database.tables

import io.dewey.server.database.extensions.timestampMillis
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Append-only: one row per progress write, never updated
 */
object ProgressLogTable : UuidTable("progress_log") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val book = reference("book_id", BookTable, onDelete = ReferenceOption.CASCADE)
    val library = reference("library_id", LibraryTable, onDelete = ReferenceOption.CASCADE).index()
    val positionMs = long("position_ms")
    val at = timestampMillis("at")

    init {
        index(false, user, at)
        index(false, user, book)
    }
}

data class ProgressLogRow(
    val id: Uuid,
    val user: Uuid,
    val book: Uuid,
    val library: Uuid,
    val positionMs: Long,
    val at: Instant,
)

fun ResultRow.toProgressLogRow(): ProgressLogRow =
    ProgressLogRow(
        id = this[ProgressLogTable.id].value,
        user = this[ProgressLogTable.user].value,
        book = this[ProgressLogTable.book].value,
        library = this[ProgressLogTable.library].value,
        positionMs = this[ProgressLogTable.positionMs],
        at = this[ProgressLogTable.at],
    )

context(_: Transaction)
fun ProgressLogTable.insert(row: ProgressLogRow): Uuid {
    insert {
        it[id] = row.id
        it[user] = row.user
        it[book] = row.book
        it[library] = row.library
        it[positionMs] = row.positionMs
        it[at] = row.at
    }
    return row.id
}
