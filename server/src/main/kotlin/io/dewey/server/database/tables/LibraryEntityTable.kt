package io.dewey.server.database.tables

import io.dewey.models.ChangeType
import io.dewey.models.ChangedEntity
import io.dewey.server.database.extensions.timestampMillis
import io.dewey.server.events.recordChange
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface MetadataField {
    val name: String

    val column: String get() = name.lowercase()
}

sealed class LibraryEntityTable(
    table: String,
    nameColumn: String,
    val entity: ChangedEntity,
) : UuidTable(table) {
    val library = reference("library_id", LibraryTable, onDelete = ReferenceOption.CASCADE).index()
    val deferDeletionUntil = timestampMillis("defer_deletion_until").nullable()
    val name = text(nameColumn, collate = "NOCASE")

    // What the files called the entity when a scan created it, so one that a user or a metadata agent renamed is
    // still recognised by the next file that carries the old name. Never rewritten: a file that joined through the
    // current name would otherwise replace it, and the files that carry the old name would split off on rescan.
    val taggedName = text("tagged_name", collate = "NOCASE").nullable()

    val visible get() = deferDeletionUntil.isNull()

    context(_: Transaction)
    open fun record(
        type: ChangeType,
        ids: Collection<Uuid>,
    ) = ids.chunked(500).forEach { chunk ->
        // Sqlite has a max nr of parameters
        val listed = id inList chunk
        select(id, library)
            .where { if (type == ChangeType.DELETED) listed else listed and visible }
            .forEach { announce(type, it[id].value, it[library].value) }
    }

    context(_: Transaction)
    protected open fun announce(
        type: ChangeType,
        id: Uuid,
        libraryId: Uuid,
    ) = recordChange(entity, type, id, libraryId)

    init {
        // deferDeletionUntil sits behind the sort column on purpose. In front of it SQLite seeks on both
        // constraints, but then has to sort the whole library whenever showInvisible drops the predicate.
        index(false, library, name, deferDeletionUntil)
        index(false, library, taggedName)
    }
}

context(_: Transaction)
fun LibraryEntityTable.create(
    libraryId: Uuid,
    name: String,
    taggedName: String? = null,
    deferDeletionUntil: Instant? = null,
): Uuid =
    insertAndGetId {
        it[library] = libraryId
        it[this.name] = name
        it[this.taggedName] = taggedName
        it[this.deferDeletionUntil] = deferDeletionUntil
    }.value.also {
        record(ChangeType.CREATED, listOf(it))
    }

context(_: Transaction)
fun LibraryEntityTable.hide(
    where: Op<Boolean>,
    until: Instant,
) {
    val visibleOnes = where and visible
    val ids = idsWhere(visibleOnes)
    update({ visibleOnes }) { it[deferDeletionUntil] = until }
    record(ChangeType.DELETED, ids)
}

context(_: Transaction)
fun LibraryEntityTable.unhide(where: Op<Boolean>) {
    val hiddenOnes = where and deferDeletionUntil.isNotNull()
    val ids = idsWhere(hiddenOnes)
    update({ hiddenOnes }) { it[deferDeletionUntil] = null }
    record(ChangeType.CREATED, ids)
}

context(_: Transaction)
private fun LibraryEntityTable.idsWhere(where: Op<Boolean>): List<Uuid> =
    select(id).where { where }.map { it[id].value }
