package io.dewey.server.repositories

import io.dewey.metadata.MetadataAgentWrapper
import io.dewey.metadata.MetadataAgents
import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import io.dewey.models.ChangeType
import io.dewey.models.Series
import io.dewey.models.SeriesDetailed
import io.dewey.models.SeriesUpdate
import io.dewey.openapi.common.ifSet
import io.dewey.openapi.common.map
import io.dewey.openapi.common.orElse
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.common.ImageDownloader
import io.dewey.server.common.exposed.unless
import io.dewey.server.common.extensions.escape
import io.dewey.server.common.extensions.ilike
import io.dewey.server.database.access.getOrCreateImage
import io.dewey.server.database.rows.BookRow
import io.dewey.server.database.rows.SeriesRow
import io.dewey.server.database.rows.booksToModels
import io.dewey.server.database.rows.lockBookField
import io.dewey.server.database.rows.seriesToModels
import io.dewey.server.database.rows.toBookRow
import io.dewey.server.database.rows.toModel
import io.dewey.server.database.rows.toSeriesRow
import io.dewey.server.database.rows.update
import io.dewey.server.database.tables.AuthorBookTable
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookField
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.SeriesBookTable
import io.dewey.server.database.tables.SeriesField
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.bookIdsLinkedToSeries
import io.dewey.server.database.tables.create
import io.dewey.server.database.tables.seriesLinksWithBooks
import io.dewey.server.schedules.AutoMatchRequest
import io.dewey.server.schedules.AutoMatcher
import io.dewey.server.schedules.MatchableEntity
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface SeriesRepository : Repository<SeriesRow, Series, SeriesDetailed, SeriesUpdate> {
    fun findByTaggedName(
        seriesTitle: String,
        libraryId: Uuid,
    ): SeriesRow?

    fun getOrCreate(
        seriesName: String,
        libraryId: Uuid,
    ): SeriesRow

    fun create(
        seriesName: String,
        libraryId: Uuid,
    ): SeriesRow

    fun createManual(
        seriesName: String,
        libraryId: Uuid,
    ): SeriesRow
}

class SeriesRepositoryImpl :
    SeriesRepository,
    KoinComponent {
    private val authorRepository by inject<AuthorRepository>()
    private val bookRepository by inject<BookRepository>()
    private val libraryRepository by inject<LibraryRepository>()
    private val metadataAgents by inject<MetadataAgents>()
    private val imageDownloader by inject<ImageDownloader>()
    private val autoMatcher by inject<AutoMatcher>()

    private companion object {
        val log = logger {}
    }

    override fun findByTaggedName(
        seriesTitle: String,
        libraryId: Uuid,
    ): SeriesRow? =
        transaction {
            idOfTaggedName(escape(seriesTitle), libraryId)?.let { raw(it, libraryId) }
        }

    override fun raw(
        id: Uuid,
        libraryId: Uuid,
    ): SeriesRow =
        transaction {
            SeriesTable
                .selectAll()
                .where { SeriesTable.id eq id and (SeriesTable.library eq libraryId) }
                .firstOrNull()
                ?.toSeriesRow()
                ?: throw ErrorResponse.notFound("Series", id)
        }

    override fun get(
        userId: Uuid,
        id: Uuid,
        libraryId: Uuid,
        showInvisible: Boolean,
    ): SeriesDetailed =
        transaction {
            val series = raw(id = id, libraryId = libraryId)
            if (!showInvisible) requireVisible(SeriesTable, "Series", id)

            SeriesDetailed.fromModel(
                series = series.toModel(),
                books = booksToModels(linkedBooks(id), userId),
                locked = series.locked.sorted(),
            )
        }

    override fun getAll(
        userId: Uuid,
        libraryId: Uuid,
        order: SortOrder,
        limit: Int,
        offset: Long,
        showInvisible: Boolean,
    ): List<Series> =
        transaction {
            val rows =
                SeriesTable
                    .selectAll()
                    .where {
                        (SeriesTable.library eq libraryId) and SeriesTable.visible.unless(showInvisible)
                    }.orderBy(SeriesTable.title to order)
                    .offset(offset)
                    .limit(limit)
                    .map { it.toSeriesRow() }
            seriesToModels(rows)
        }

    override fun search(
        userId: Uuid,
        query: String,
        libraryId: Uuid,
    ): List<Series> =
        transaction {
            val rows =
                SeriesTable
                    .selectAll()
                    .where {
                        matchesTitle(query) and (SeriesTable.library eq libraryId) and
                            SeriesTable.visible
                    }.orderBy(SeriesTable.title to SortOrder.ASC)
                    .limit(searchLimit)
                    .map { it.toSeriesRow() }
            seriesToModels(rows)
        }

    override fun getOrCreate(
        seriesName: String,
        libraryId: Uuid,
    ): SeriesRow = transaction { findByTaggedName(seriesName, libraryId) ?: create(seriesName, libraryId) }

    override fun create(
        seriesName: String,
        libraryId: Uuid,
    ): SeriesRow =
        transaction {
            log.info { "Created series: $seriesName" }
            val id = SeriesTable.create(libraryRepository.raw(libraryId).id, seriesName, taggedName = seriesName)
            autoMatcher.matchOnCommit(AutoMatchRequest(MatchableEntity.SERIES, id, libraryId))
            raw(id, libraryId)
        }

    override fun createManual(
        seriesName: String,
        libraryId: Uuid,
    ): SeriesRow =
        transaction {
            // Born an orphan: hidden and on the clock until the caller attaches books. The user named it, so a
            // later match must not rename it.
            val id =
                SeriesTable.create(
                    libraryRepository.raw(libraryId).id,
                    seriesName,
                    deferDeletionUntil = Clock.System.now().plus(DEFER_DELETION_GRACE),
                )
            SeriesTable.update({ SeriesTable.id eq id }) { it[locked] = setOf(SeriesField.TITLE) }
            raw(id, libraryId)
        }

    override fun modify(
        userId: Uuid,
        id: Uuid,
        libraryId: Uuid,
        partial: SeriesUpdate,
    ): Series {
        val currentCover = raw(id, libraryId).coverID
        val newCover =
            imageDownloader.download(partial.cover.orElse(null)?.takeUnless { it == currentCover?.toString() })
        return transaction {
            val series = raw(id, libraryId)
            val edit = UserEdit(series.locked, partial.unlock)
            val cover = partial.cover.map { it?.let { getOrCreateImage(newCover, currentImageID = currentCover) } }
            SeriesTable.update(
                series,
                series.copy(
                    title = edit.value(SeriesField.TITLE, series.title, partial.title),
                    provider = edit.value(SeriesField.PROVIDER, series.provider, partial.provider),
                    providerID = edit.value(SeriesField.PROVIDER_ID, series.providerID, partial.providerID),
                    totalBooks = edit.value(SeriesField.TOTAL_BOOKS, series.totalBooks, partial.totalBooks),
                    primaryWorks = edit.value(SeriesField.PRIMARY_WORKS, series.primaryWorks, partial.primaryWorks),
                    coverID = edit.value(SeriesField.COVER_ID, series.coverID, cover),
                    description = edit.value(SeriesField.DESCRIPTION, series.description, partial.description),
                    locked = edit.locked,
                ),
            )

            partial.books.ifSet { books ->
                setBooks(id, books.map { bookRepository.raw(it, libraryId).id }.toSet())
            }

            refreshSeriesDeferral(listOf(id))
            raw(id, libraryId).toModel()
        }
    }

    context(_: Transaction)
    private fun setBooks(
        seriesId: Uuid,
        wanted: Set<Uuid>,
    ) {
        val current = bookIdsLinkedToSeries(seriesId).toSet()
        val removed = current - wanted
        val added = wanted - current
        SeriesBookTable.deleteWhere { (series eq seriesId) and (book inList removed) }
        added.forEach { bookId ->
            SeriesBookTable.insert {
                it[book] = bookId
                it[series] = seriesId
            }
        }
        // Dropping a book from the series locks its series, or the next scan of a file that still names it puts it
        // straight back.
        (removed + added).forEach { lockBookField(it, BookField.SERIES) }
        SeriesTable.record(ChangeType.UPDATED, listOf(seriesId))
    }

    context(_: Transaction)
    private fun linkedBooks(seriesId: Uuid): List<BookRow> =
        seriesLinksWithBooks
            .selectAll()
            .where { (SeriesBookTable.series eq seriesId) and BookTable.visible }
            .orderBy(BookTable.title to SortOrder.ASC)
            .map { it.toBookRow() }

    override fun autoMatch(
        userId: Uuid,
        id: Uuid,
        libraryId: Uuid,
    ): Series {
        val (metadataWrapper, title, region, authorName, language, preferFile) =
            transaction {
                val series = raw(id, libraryId)
                val library = libraryRepository.raw(libraryId)
                AutoMatchQuery(
                    metadataAgents.forLibrary(library),
                    series.title,
                    library.region,
                    seriesAuthorNames(id).joinToString(", "),
                    library.language,
                    library.preferEmbeddedMetadata,
                )
            }

        val seriesMetadata =
            runBlocking {
                metadataWrapper.bestSeriesMatch(title, region, authorName, language)
            } ?: throw noMatch(title)

        val newCover = imageDownloader.download(seriesMetadata.coverURL)
        return transaction {
            val series = raw(id, libraryId)
            val write = AutomaticWrite(series.locked, onlyFillEmpty = preferFile)
            SeriesTable.update(
                series,
                series.copy(
                    title = write.value(SeriesField.TITLE, series.title) { seriesMetadata.title },
                    provider = write.overwrite(SeriesField.PROVIDER, series.provider) { seriesMetadata.id.provider },
                    providerID =
                        write.overwrite(SeriesField.PROVIDER_ID, series.providerID) { seriesMetadata.id.itemID },
                    totalBooks = write.value(SeriesField.TOTAL_BOOKS, series.totalBooks) { seriesMetadata.totalBooks },
                    primaryWorks =
                        write.value(SeriesField.PRIMARY_WORKS, series.primaryWorks) { seriesMetadata.primaryWorks },
                    description =
                        write.value(SeriesField.DESCRIPTION, series.description) { seriesMetadata.description },
                    coverID = write.value(SeriesField.COVER_ID, series.coverID) { getOrCreateImage(newCover, it) },
                ),
            )
            raw(id, libraryId).toModel()
        }
    }

    private fun seriesAuthorNames(seriesId: Uuid): List<String> =
        SeriesBookTable
            .join(AuthorBookTable, JoinType.INNER, SeriesBookTable.book, AuthorBookTable.book)
            .join(AuthorTable, JoinType.INNER, AuthorBookTable.author, AuthorTable.id)
            .select(AuthorTable.name)
            .where { SeriesBookTable.series eq seriesId }
            .map { it[AuthorTable.name] }
            .distinct()

    private data class AutoMatchQuery(
        val metadataWrapper: MetadataAgentWrapper,
        val title: String,
        val region: MetadataRegion,
        val authorName: String,
        val language: MetadataLanguage,
        val preferFile: Boolean,
    )

    override fun total(
        libraryId: Uuid,
        showInvisible: Boolean,
    ): Long =
        transaction {
            SeriesTable
                .selectAll()
                .where {
                    (SeriesTable.library eq libraryId) and SeriesTable.deferDeletionUntil.isNull().unless(showInvisible)
                }.count()
        }
}

context(_: Transaction)
private fun idOfTaggedName(
    pattern: String,
    libraryId: Uuid,
): Uuid? =
    SeriesTable
        .select(SeriesTable.id)
        .where {
            ((SeriesTable.title ilike pattern) or (SeriesTable.taggedName ilike pattern)) and
                (SeriesTable.library eq libraryId)
        }.firstOrNull()
        ?.get(SeriesTable.id)
        ?.value

private fun matchesTitle(query: String): Op<Boolean> = SeriesTable.title ilike "%${escape(query)}%"
