package io.dewey.server

import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import io.dewey.models.FileScanner
import io.dewey.models.LibraryPermissionLevel
import io.dewey.models.NamedMetadataAgent
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.LibraryRow
import io.dewey.server.database.tables.LibraryTable
import io.dewey.server.database.tables.LibraryUserTable
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.TrackChapter
import io.dewey.server.database.tables.TrackRow
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.database.tables.UserRow
import io.dewey.server.database.tables.UserTable
import io.dewey.server.database.tables.create
import io.dewey.server.database.tables.insert
import io.dewey.server.database.tables.replaceBookAuthors
import io.dewey.server.database.tables.replaceBookSeries
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

// Cover art is only stored if it sniffs as a real image, so test art needs a valid header
fun pngBytes(vararg payload: Byte): ByteArray =
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + payload

fun newLibrary(
    name: String,
    folders: List<String> = listOf("/media/$name"),
    scanIndex: ULong = 0uL,
    fileScanners: List<FileScanner> = listOf(FileScanner("AudioFolderScanner")),
    combineFileScannerFields: Boolean = true,
    preferEmbeddedMetadata: Boolean = false,
    region: MetadataRegion = MetadataRegion.US,
    language: MetadataLanguage = MetadataLanguage.English,
    metadataAgents: List<NamedMetadataAgent> = listOf(NamedMetadataAgent("audible")),
    combineMetadataAgentFields: Boolean = true,
): Uuid =
    transaction {
        LibraryTable.insert(
            LibraryRow(
                id = Uuid.random(),
                name = name,
                icon = null,
                scanIndex = scanIndex,
                folders = folders,
                preferEmbeddedMetadata = preferEmbeddedMetadata,
                metadataAgents = metadataAgents,
                combineMetadataAgentFields = combineMetadataAgentFields,
                fileScanners = fileScanners,
                combineFileScannerFields = combineFileScannerFields,
                language = language,
                region = region,
            ),
        )
    }

fun newAuthor(
    name: String,
    libraryId: Uuid,
    renamedTo: String? = null,
): Uuid = transaction { AuthorTable.create(libraryId, renamedTo ?: name, taggedName = name) }

fun newSeries(
    title: String,
    libraryId: Uuid,
): Uuid = transaction { SeriesTable.create(libraryId, title, taggedName = title) }

fun newBook(
    title: String,
    libraryId: Uuid,
    authors: List<Uuid> = emptyList(),
    series: List<Uuid> = emptyList(),
    narrators: List<String>? = null,
    genres: List<String>? = null,
): Uuid =
    transaction {
        val id = BookTable.create(libraryId, title, taggedName = title)
        BookTable.update({ BookTable.id eq id }) {
            it[BookTable.narrators] = narrators
            it[BookTable.genres] = genres
        }
        replaceBookAuthors(id, authors)
        replaceBookSeries(id, series.associateWith { null })
        id
    }

fun newTrack(
    title: String,
    path: String,
    bookId: Uuid,
    libraryId: Uuid,
    fileModifiedAt: Instant = Instant.fromEpochMilliseconds(0),
    scanIndex: ULong = 0uL,
    trackNr: Int? = null,
    durationMs: Long = 60_000,
    chapters: List<TrackChapter> = emptyList(),
): Uuid =
    transaction {
        TrackTable.insert(
            TrackRow(
                id = Uuid.random(),
                title = title,
                durationMs = durationMs,
                fileModifiedAt = fileModifiedAt,
                path = path,
                book = bookId,
                library = libraryId,
                scanIndex = scanIndex,
                trackNr = trackNr,
                chapters = chapters,
            ),
        )
    }

fun newUser(
    username: String,
    admin: Boolean = false,
    libraries: Map<Uuid, LibraryPermissionLevel> = emptyMap(),
): Uuid =
    transaction {
        val id =
            UserTable.insert(
                UserRow(
                    id = Uuid.random(),
                    username = username,
                    passwordHash = "hash",
                    admin = admin,
                    tokenVersion = 0,
                ),
            )
        libraries.forEach { (libraryId, level) ->
            LibraryUserTable.insert {
                it[user] = id
                it[library] = libraryId
                it[permissions] = level
            }
        }
        id
    }
