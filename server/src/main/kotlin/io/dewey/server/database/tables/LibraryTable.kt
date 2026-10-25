package io.dewey.server.database.tables

import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import io.dewey.models.ChangeType
import io.dewey.models.ChangedEntity
import io.dewey.models.FileScanner
import io.dewey.models.Library
import io.dewey.models.NamedMetadataAgent
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.database.extensions.json
import io.dewey.server.events.recordChange
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

object LibraryTable : UuidTable("library") {
    val name = varchar("name", 255)
    val icon = text("icon").nullable()
    val scanIndex = ulong("scan_index").default(0uL)
    val folders =
        json<List<String>>("folders") {
            if (it.isEmpty()) {
                throw ErrorResponse.userError("folders must have at least one element")
            }
        }

    // Decides who may overwrite whom when a scan or a match writes, so flipping it leaves existing values alone
    val preferEmbeddedMetadata = bool("prefer_embedded_metadata").default(false)

    // Deliberately unconstrained: a library with no agents just does no online metadata lookups.
    val metadataAgents = json<List<NamedMetadataAgent>>("metadata_agents")
    val combineMetadataAgentFields = bool("combine_metadata_agent_fields").default(true)
    val fileScanners =
        json<List<FileScanner>>("file_scanners") {
            if (it.isEmpty()) {
                throw ErrorResponse.userError("fileScanners must have at least one element")
            }
        }
    val combineFileScannerFields = bool("combine_file_scanner_fields").default(true)

    val language = enumerationByName<MetadataLanguage>("language", 255)
    val region = enumerationByName<MetadataRegion>("region", 255)
}

data class LibraryRow(
    val id: Uuid,
    val name: String,
    val icon: String?,
    val scanIndex: ULong,
    val folders: List<String>,
    val preferEmbeddedMetadata: Boolean,
    val metadataAgents: List<NamedMetadataAgent>,
    val combineMetadataAgentFields: Boolean,
    val fileScanners: List<FileScanner>,
    val combineFileScannerFields: Boolean,
    val language: MetadataLanguage,
    val region: MetadataRegion,
) {
    fun toModel(bookCount: Long): Library =
        Library(
            id = id,
            name = name,
            icon = icon,
            preferEmbeddedMetadata = preferEmbeddedMetadata,
            folders = folders,
            metadataAgents = metadataAgents,
            combineMetadataAgentFields = combineMetadataAgentFields,
            fileScanners = fileScanners,
            combineFileScannerFields = combineFileScannerFields,
            language = language,
            region = region,
            bookCount = bookCount,
        )
}

fun ResultRow.toLibraryRow(): LibraryRow =
    LibraryRow(
        id = this[LibraryTable.id].value,
        name = this[LibraryTable.name],
        icon = this[LibraryTable.icon],
        scanIndex = this[LibraryTable.scanIndex],
        folders = this[LibraryTable.folders],
        preferEmbeddedMetadata = this[LibraryTable.preferEmbeddedMetadata],
        metadataAgents = this[LibraryTable.metadataAgents],
        combineMetadataAgentFields = this[LibraryTable.combineMetadataAgentFields],
        fileScanners = this[LibraryTable.fileScanners],
        combineFileScannerFields = this[LibraryTable.combineFileScannerFields],
        language = this[LibraryTable.language],
        region = this[LibraryTable.region],
    )

context(_: Transaction)
fun LibraryTable.insert(row: LibraryRow): Uuid {
    insert { write(it, row) }
    record(ChangeType.CREATED, row.id)
    return row.id
}

context(_: Transaction)
fun LibraryTable.update(row: LibraryRow) {
    update({ LibraryTable.id eq row.id }) { write(it, row) }
    record(ChangeType.UPDATED, row.id)
}

context(_: Transaction)
fun LibraryTable.delete(libraryId: Uuid): Boolean =
    (deleteWhere { LibraryTable.id eq libraryId } > 0).also { deleted ->
        if (deleted) record(ChangeType.DELETED, libraryId)
    }

context(_: Transaction)
private fun record(
    type: ChangeType,
    libraryId: Uuid,
) = recordChange(ChangedEntity.LIBRARY, type, libraryId, libraryId)

private fun write(
    stmt: UpdateBuilder<*>,
    row: LibraryRow,
) {
    stmt[LibraryTable.id] = row.id
    stmt[LibraryTable.name] = row.name
    stmt[LibraryTable.icon] = row.icon
    stmt[LibraryTable.scanIndex] = row.scanIndex
    stmt[LibraryTable.folders] = row.folders
    stmt[LibraryTable.preferEmbeddedMetadata] = row.preferEmbeddedMetadata
    stmt[LibraryTable.metadataAgents] = row.metadataAgents
    stmt[LibraryTable.combineMetadataAgentFields] = row.combineMetadataAgentFields
    stmt[LibraryTable.fileScanners] = row.fileScanners
    stmt[LibraryTable.combineFileScannerFields] = row.combineFileScannerFields
    stmt[LibraryTable.language] = row.language
    stmt[LibraryTable.region] = row.region
}
