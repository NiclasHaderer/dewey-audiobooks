package io.dewey.server.database.tables

import io.dewey.models.ChangeType
import io.dewey.models.TitledId
import io.dewey.models.Track
import io.dewey.server.database.extensions.json
import io.dewey.server.database.extensions.timestampMillis
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

object TrackTable : UuidTable("track") {
    val title = text("title")
    val durationMs = long("duration_ms")
    val fileModifiedAt = timestampMillis("file_modified_at")
    val path = text("path").uniqueIndex()
    val book = reference("book_id", BookTable, onDelete = ReferenceOption.CASCADE).index()
    val library = reference("library_id", LibraryTable, onDelete = ReferenceOption.CASCADE).index()
    val scanIndex = ulong("scan_index")
    val trackNr = integer("track_nr").nullable()
    val chapters = json<List<TrackChapter>>("chapters").default(emptyList())
}

@Serializable
data class TrackChapter(
    val title: String?,
    val startMs: Long,
)

data class TrackRow(
    val id: Uuid,
    val title: String,
    val durationMs: Long,
    val fileModifiedAt: Instant,
    val path: String,
    val book: Uuid,
    val library: Uuid,
    val scanIndex: ULong,
    val trackNr: Int?,
    val chapters: List<TrackChapter> = emptyList(),
) {
    fun toModel(
        book: TitledId,
        trackNr: Int,
    ): Track =
        Track(
            id = id,
            title = title,
            trackNr = trackNr,
            durationMs = durationMs,
            fileModifiedAt = fileModifiedAt,
            book = book,
        )
}

fun ResultRow.toTrackRow(): TrackRow =
    TrackRow(
        id = this[TrackTable.id].value,
        title = this[TrackTable.title],
        durationMs = this[TrackTable.durationMs],
        fileModifiedAt = this[TrackTable.fileModifiedAt],
        path = this[TrackTable.path],
        book = this[TrackTable.book].value,
        library = this[TrackTable.library].value,
        scanIndex = this[TrackTable.scanIndex],
        trackNr = this[TrackTable.trackNr],
        chapters = this[TrackTable.chapters],
    )

context(_: Transaction)
fun TrackTable.insert(row: TrackRow): Uuid {
    insert { write(it, row) }
    return row.id
}

context(_: Transaction)
fun TrackTable.update(row: TrackRow) {
    val previousBook = select(book).where { TrackTable.id eq row.id }.single()[book].value
    update({ TrackTable.id eq row.id }) { write(it, row) }
    BookTable.record(ChangeType.UPDATED, setOf(previousBook, row.book))
}

context(_: Transaction)
fun TrackTable.remove(where: Op<Boolean>) {
    val books = select(book).where { where }.withDistinct().map { it[book].value }
    deleteWhere { where }
    BookTable.record(ChangeType.UPDATED, books)
}

private fun write(
    stmt: UpdateBuilder<*>,
    row: TrackRow,
) {
    stmt[TrackTable.id] = row.id
    stmt[TrackTable.title] = row.title
    stmt[TrackTable.durationMs] = row.durationMs
    stmt[TrackTable.fileModifiedAt] = row.fileModifiedAt
    stmt[TrackTable.path] = row.path
    stmt[TrackTable.book] = row.book
    stmt[TrackTable.library] = row.library
    stmt[TrackTable.scanIndex] = row.scanIndex
    stmt[TrackTable.trackNr] = row.trackNr
    stmt[TrackTable.chapters] = row.chapters
}
