package io.dewey.server.file

import io.dewey.server.common.extensions.canonicalString
import io.dewey.server.common.extensions.lastModifiedInstant
import io.dewey.server.database.access.getOrCreateImage
import io.dewey.server.database.rows.toBookRow
import io.dewey.server.database.rows.update
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookField
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.LibraryRow
import io.dewey.server.database.tables.LibraryTable
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.TrackRow
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.database.tables.create
import io.dewey.server.database.tables.insert
import io.dewey.server.database.tables.remove
import io.dewey.server.database.tables.replaceBookAuthors
import io.dewey.server.database.tables.replaceBookSeries
import io.dewey.server.database.tables.toLibraryRow
import io.dewey.server.database.tables.toTrackRow
import io.dewey.server.database.tables.unhide
import io.dewey.server.database.tables.update
import io.dewey.server.file.analyzer.AudioFileAnalysisResult
import io.dewey.server.file.analyzer.AudioFileAnalyzers
import io.dewey.server.file.scanner.LibraryEntityModel
import io.dewey.server.repositories.AuthorRepository
import io.dewey.server.repositories.AutomaticWrite
import io.dewey.server.repositories.BookRepository
import io.dewey.server.repositories.SeriesRepository
import io.dewey.server.schedules.AutoMatchRequest
import io.dewey.server.schedules.AutoMatcher
import io.dewey.server.schedules.MatchableEntity
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.absolute
import kotlin.io.path.readAttributes
import kotlin.uuid.Uuid

class TrackManager : KoinComponent {
    private val bookRepository by inject<BookRepository>()
    private val seriesRepository by inject<SeriesRepository>()
    private val authorRepository by inject<AuthorRepository>()
    private val analyzers by inject<AudioFileAnalyzers>()
    private val autoMatcher by inject<AutoMatcher>()

    private val log = logger {}

    fun needsAnalysis(path: Path): Boolean =
        transaction {
            val known =
                TrackTable
                    .select(TrackTable.fileModifiedAt)
                    .where { TrackTable.path eq path.canonicalString() }
                    .firstOrNull()
                    ?.get(TrackTable.fileModifiedAt)
                    ?: return@transaction true
            known < path.lastModifiedInstant()
        }

    fun analyze(
        path: Path,
        library: LibraryEntityModel,
    ): AudioFileAnalysisResult? {
        val attrs =
            try {
                path.readAttributes<BasicFileAttributes>()
            } catch (_: Throwable) {
                // Disappeared between being queued and being picked up, which is normal operation
                return null
            }
        if (!attrs.isRegularFile) return null

        val root = library.folders.firstOrNull { path.startsWith(it) }
        if (root == null) {
            log.error { "'${path.absolute()}' is not under any folder of library '${library.name}'" }
            return null
        }

        return analyzers.forNames(library.fileScanners, library.combineFileScannerFields).analyze(path, attrs, root)
    }

    fun insert(
        scan: AudioFileAnalysisResult,
        libraryId: Uuid,
    ) = transaction {
        val library = libraryRow(libraryId)
        val track = TrackTable
            .selectAll()
            .where { TrackTable.path eq scan.path }
            .firstOrNull()
            ?.toTrackRow()
        val authorIds = getOrCreateAuthors(scan, library)
        val bookId = getOrCreateBook(scan, authorIds, library)
        writeFileMetadata(bookId, scan, authorIds, library)
        if (track != null) {
            TrackTable.update(
                track.copy(
                    title = scan.title,
                    durationMs = scan.durationMs,
                    fileModifiedAt = scan.lastModified,
                    path = scan.path,
                    book = bookId,
                    trackNr = scan.trackNr,
                    scanIndex = library.scanIndex,
                    chapters = scan.chapters,
                ),
            )
        } else {
            TrackTable.insert(
                TrackRow(
                    id = Uuid.random(),
                    title = scan.title,
                    durationMs = scan.durationMs,
                    fileModifiedAt = scan.lastModified,
                    path = scan.path,
                    book = bookId,
                    library = library.id,
                    scanIndex = library.scanIndex,
                    trackNr = scan.trackNr,
                    chapters = scan.chapters,
                ),
            )
        }
    }

    fun touch(
        paths: List<Path>,
        libraryId: Uuid,
    ) = transaction {
        val scanIndex = libraryRow(libraryId).scanIndex
        TrackTable.update({
            (TrackTable.library eq libraryId) and (TrackTable.path inList paths.map { it.canonicalString() })
        }) {
            it[TrackTable.scanIndex] = scanIndex
        }
    }

    // Stamps a whole subtree as seen, for the parts of the tree a scan could not read: not knowing whether a
    // file is still there must not read as knowing that it is gone
    fun touchFolder(
        path: Path,
        libraryId: Uuid,
    ) = transaction {
        val scanIndex = libraryRow(libraryId).scanIndex
        val target = path.canonicalString()
        val subtree = LikePattern.ofLiteral(target + File.separator) + "%"
        TrackTable.update({
            ((TrackTable.path eq target) or (TrackTable.path like subtree)) and
                (TrackTable.library eq libraryId)
        }) {
            it[TrackTable.scanIndex] = scanIndex
        }
    }

    fun removeFile(
        path: Path,
        libraryId: Uuid,
    ) = transaction {
        TrackTable.remove((TrackTable.path eq path.canonicalString()) and (TrackTable.library eq libraryId))
    }

    fun removeFolder(
        path: Path,
        libraryId: Uuid,
    ) = transaction {
        // Rows hold normalised paths, and the separator keeps "/books/Dune" from also matching "/books/Dune 2"
        val target = path.canonicalString()
        val subtree = LikePattern.ofLiteral(target + File.separator) + "%"
        TrackTable.remove(
            ((TrackTable.path eq target) or (TrackTable.path like subtree)) and (TrackTable.library eq libraryId),
        )
    }

    context(_: Transaction)
    private fun libraryRow(libraryId: Uuid): LibraryRow =
        LibraryTable
            .selectAll()
            .where { LibraryTable.id eq libraryId }
            .single()
            .toLibraryRow()

    context(_: Transaction)
    private fun getOrCreateBook(
        scan: AudioFileAnalysisResult,
        authorIds: List<Uuid>,
        library: LibraryRow,
    ): Uuid =
        bookRepository.findByTaggedName(bookTitle = scan.book, authorIds = authorIds, libraryId = library.id)?.id
            ?: run {
                log.info { "Created new book: ${scan.book}" }
                BookTable.create(library.id, scan.book, taggedName = scan.book).also {
                    autoMatcher.matchOnCommit(AutoMatchRequest(MatchableEntity.BOOK, it, library.id))
                }
            }

    // Tracks of one book that disagree about their tags leave it up to whichever is imported last
    context(_: Transaction)
    private fun writeFileMetadata(
        bookId: Uuid,
        scan: AudioFileAnalysisResult,
        authorIds: List<Uuid>,
        library: LibraryRow,
    ) {
        val book = BookTable
            .selectAll()
            .where { BookTable.id eq bookId }
            .single()
            .toBookRow()
        // An unmatched book has no agent data that preferring the agent could protect
        val write =
            AutomaticWrite(
                book.locked,
                onlyFillEmpty = !library.preferEmbeddedMetadata && book.provider != null,
            )
        BookTable.update(
            book,
            book.copy(
                title = write.value(BookField.TITLE, book.title) { scan.book },
                coverID = write.value(BookField.COVER_ID, book.coverID) { getOrCreateImage(scan.cover, it) },
                language = write.value(BookField.LANGUAGE, book.language) { scan.language },
                description = write.value(BookField.DESCRIPTION, book.description) { scan.description },
                narrators = write.value(BookField.NARRATORS, book.narrators) { scan.narrators.ifEmpty { null } },
                releaseDate = write.value(BookField.RELEASE_DATE, book.releaseDate) { scan.date },
                genres = write.value(BookField.GENRES, book.genres) { normalizeGenres(scan.genres).ifEmpty { null } },
            ),
        )
        // Agents never name authors or series, so for these the files are the only source and only a lock stops them
        val linkedAuthors =
            if (write.mayWrite(BookField.AUTHORS)) authorIds.also { replaceBookAuthors(bookId, it) } else emptyList()
        val linkedSeries =
            if (write.mayWrite(BookField.SERIES)) {
                val seriesId = scan.series?.let { seriesRepository.getOrCreate(it, library.id).id }
                replaceBookSeries(bookId, listOfNotNull(seriesId).associateWith { scan.seriesIndex })
                listOfNotNull(seriesId)
            } else {
                emptyList()
            }
        // Unset deletion marker, since the book has a track again
        BookTable.unhide(BookTable.id eq bookId)
        AuthorTable.unhide(AuthorTable.id inList linkedAuthors)
        SeriesTable.unhide(SeriesTable.id inList linkedSeries)
    }

    // The same genre spelled differently by two files is one genre
    private fun normalizeGenres(names: List<String>): List<String> = names.distinctBy { it.lowercase() }

    private fun getOrCreateAuthors(
        scan: AudioFileAnalysisResult,
        library: LibraryRow,
    ): List<Uuid> =
        scan.authors.map { author ->
            authorRepository.findByTaggedName(author, library.id)?.id ?: run {
                log.info { "Creating author: $author" }
                authorRepository.create(author, library.id).id
            }
        }
}
