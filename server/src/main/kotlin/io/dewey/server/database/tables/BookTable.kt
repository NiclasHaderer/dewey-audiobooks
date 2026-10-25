package io.dewey.server.database.tables

import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.models.ChangeType
import io.dewey.models.ChangedEntity
import io.dewey.models.ChapterMark
import io.dewey.server.database.extensions.json
import io.dewey.server.events.recordChange
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.uuid.Uuid

enum class BookField : MetadataField {
    TITLE,
    AUTHORS,
    SERIES,
    PROVIDER,
    PROVIDER_ID,
    PROVIDER_RATING,
    RELEASE_DATE,
    PUBLISHER,
    LANGUAGE,
    DESCRIPTION,
    NARRATORS,
    GENRES,
    ISBN,
    COVER_ID,
    CHAPTERS,
}

object BookTable : LibraryEntityTable("book", BookField.TITLE.column, ChangedEntity.BOOK) {
    val title get() = name
    val releaseDate = date(BookField.RELEASE_DATE.column).nullable()
    val publisher = varchar(BookField.PUBLISHER.column, 255).nullable()
    val language = enumerationByName<MetadataLanguage>(BookField.LANGUAGE.column, 255).nullable()
    val description = text(BookField.DESCRIPTION.column).nullable()
    val isbn = varchar(BookField.ISBN.column, 255).nullable()
    val provider = varchar(BookField.PROVIDER.column, 255).nullable()
    val providerId = varchar(BookField.PROVIDER_ID.column, 255).nullable()
    val providerRating = float(BookField.PROVIDER_RATING.column).nullable()
    val coverId = reference(BookField.COVER_ID.column, ImageTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val genres = json<List<String>>(BookField.GENRES.column).nullable()
    val narrators = json<List<String>>(BookField.NARRATORS.column).nullable()

    // Null while nobody said anything about the chapters: they are then derived from the tracks, which a scan
    // never writes down here because they depend on all tracks of the book
    val chapters = json<List<ChapterMark>>(BookField.CHAPTERS.column).nullable()
    val locked = json<Set<BookField>>("locked").default(emptySet())

    // A book is also announced together with the series it is in
    context(_: Transaction)
    override fun record(
        type: ChangeType,
        ids: Collection<Uuid>,
    ) {
        super.record(type, ids)
        ids.chunked(500).forEach { chunk ->
            val series =
                SeriesBookTable
                    .select(SeriesBookTable.series)
                    .where { SeriesBookTable.book inList chunk }
                    .map { it[SeriesBookTable.series].value }
            SeriesTable.record(ChangeType.UPDATED, series)
        }
    }

    // A library shows how many books it has, so it changes with every book that appears or disappears
    context(_: Transaction)
    override fun announce(
        type: ChangeType,
        id: Uuid,
        libraryId: Uuid,
    ) {
        super.announce(type, id, libraryId)
        if (type != ChangeType.UPDATED) recordChange(ChangedEntity.LIBRARY, ChangeType.UPDATED, libraryId, libraryId)
    }
}
