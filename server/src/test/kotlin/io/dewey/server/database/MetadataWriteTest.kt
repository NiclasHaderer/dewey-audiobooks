package io.dewey.server.database

import io.dewey.metadata.FakeMetadataAgent
import io.dewey.metadata.MetadataAgents
import io.dewey.metadata.searchHit
import io.dewey.metadata.testAuthor
import io.dewey.metadata.testBook
import io.dewey.metadata.testSeries
import io.dewey.models.AuthorUpdate
import io.dewey.models.BookUpdate
import io.dewey.models.NamedMetadataAgent
import io.dewey.models.SeriesUpdate
import io.dewey.openapi.common.Patch
import io.dewey.openapi.common.orAbsent
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.DeweyTest
import io.dewey.server.database.rows.bookSeries
import io.dewey.server.database.tables.AuthorField
import io.dewey.server.database.tables.BookField
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.SeriesBookTable
import io.dewey.server.database.tables.SeriesField
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.file.TrackManager
import io.dewey.server.file.analyzer.AudioFileAnalysisResultImpl
import io.dewey.server.file.scanner.LibraryCleanup
import io.dewey.server.newAuthor
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.newSeries
import io.dewey.server.newUser
import io.dewey.server.repositories.AuthorRepository
import io.dewey.server.repositories.BookRepository
import io.dewey.server.repositories.DEFER_DELETION_GRACE
import io.dewey.server.repositories.SeriesRepository
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MetadataWriteTest : DeweyTest() {
    private val bookRepository by lazy { getKoin().get<BookRepository>() }
    private val authorRepository by lazy { getKoin().get<AuthorRepository>() }
    private val seriesRepository by lazy { getKoin().get<SeriesRepository>() }
    private val trackManager by lazy { getKoin().get<TrackManager>() }

    private var libId: Uuid = Uuid.random()
    private var userId: Uuid = Uuid.random()

    @BeforeTest
    fun createLibrary() {
        libId = newLibrary("lib", folders = listOf("/media/books"), metadataAgents = listOf(NamedMetadataAgent("fake")))
        userId = newUser("test-user", admin = true)
    }

    private fun scan(
        authors: List<String> = listOf("An Author"),
        description: String? = null,
        narrators: List<String> = emptyList(),
        series: String? = null,
        seriesIndex: Float? = null,
        asin: String? = null,
        book: String = "A Book",
        path: String = "/media/books/A Book/01.mp3",
    ) = AudioFileAnalysisResultImpl(
        title = "Chapter 1",
        authors = authors,
        book = book,
        series = series,
        seriesIndex = seriesIndex,
        asin = asin,
        durationMs = 60_000,
        path = path,
        lastModified = Instant.fromEpochMilliseconds(0),
        description = description,
        narrators = narrators,
    )

    private fun bookId() = transaction { bookRepository.findByTaggedName("A Book", emptyList(), libId)!!.id }

    @Test
    fun `a rescan keeps an edited field but still picks up the retagged ones`() {
        trackManager.insert(scan(description = "From the tags", narrators = listOf("First")), libId)
        val id = bookId()

        bookRepository.modify(userId, id, libId, bookUpdate(description = "Mine"))
        trackManager.insert(scan(description = "Retagged", narrators = listOf("Second")), libId)

        val book = bookRepository.raw(id, libId)
        assertEquals("Mine", book.description, "a rescan must not overwrite an edit")
        assertEquals(listOf("Second"), book.narrators, "a field nobody edited must follow the tags")
        assertEquals(setOf(BookField.DESCRIPTION), book.locked)
    }

    @Test
    fun `the ASIN in the tags is stored on the book`() {
        trackManager.insert(scan(asin = "B017V4IM1G"), libId)

        assertEquals("B017V4IM1G", bookRepository.raw(bookId(), libId).asin)
    }

    @Test
    fun `unlocking keeps the value until the next scan writes the field`() {
        trackManager.insert(scan(description = "From the tags"), libId)
        val id = bookId()
        bookRepository.modify(userId, id, libId, bookUpdate(description = "Mine"))

        bookRepository.modify(userId, id, libId, BookUpdate(unlock = Patch.Set(listOf(BookField.DESCRIPTION))))
        assertEquals("Mine", bookRepository.raw(id, libId).description, "unlocking alone changes no value")
        assertEquals(emptySet(), bookRepository.raw(id, libId).locked)

        trackManager.insert(scan(description = "Retagged"), libId)
        assertEquals("Retagged", bookRepository.raw(id, libId).description)
    }

    @Test
    fun `the files only fill the gaps of a matched book unless the library prefers them`() {
        val preferring = newLibrary("prefer", folders = listOf("/media/prefer"), preferEmbeddedMetadata = true)
        val agentWins = matchedBook(libId, "/media/books/A Book/01.mp3")
        val fileWins = matchedBook(preferring, "/media/prefer/A Book/01.mp3")

        trackManager.insert(
            scan(description = "Retagged", narrators = listOf("Tagged"), path = "/media/books/A Book/01.mp3"),
            libId,
        )
        trackManager.insert(
            scan(description = "Retagged", narrators = listOf("Tagged"), path = "/media/prefer/A Book/01.mp3"),
            preferring,
        )

        assertEquals("From the agent", bookRepository.raw(agentWins, libId).description)
        assertEquals(listOf("Tagged"), bookRepository.raw(agentWins, libId).narrators, "an empty field is a gap")
        assertEquals("Retagged", bookRepository.raw(fileWins, preferring).description)
    }

    // What an agent match leaves behind, without going through one
    private fun matchedBook(
        libraryId: Uuid,
        path: String,
    ): Uuid {
        trackManager.insert(scan(description = "From the tags", path = path), libraryId)
        val id = transaction { bookRepository.findByTaggedName("A Book", emptyList(), libraryId)!!.id }
        transaction {
            BookTable.update({ BookTable.id eq id }) {
                it[provider] = "fake"
                it[providerId] = "a-book"
                it[description] = "From the agent"
            }
        }
        return id
    }

    @Test
    fun `a match neither touches a locked field nor overwrites the files the library prefers`() {
        val hit = searchHit("A Book", authors = listOf("An Author"))
        val agent =
            FakeMetadataAgent(
                hits = listOf(hit),
                resolveBook = {
                    testBook(it).copy(
                        title = "Agent Title",
                        authors = hit.authors,
                        description = "From the agent",
                        isbn = "123",
                        asin = "B000000123",
                    )
                },
            )
        useAgent(agent)
        val preferring =
            newLibrary(
                "prefer",
                folders = listOf("/media/prefer"),
                preferEmbeddedMetadata = true,
                metadataAgents = listOf(NamedMetadataAgent("fake")),
            )
        trackManager.insert(scan(description = "From the tags"), libId)
        trackManager.insert(scan(description = "From the tags", path = "/media/prefer/A Book/01.mp3"), preferring)
        val locked = bookId()
        val fileWins = transaction { bookRepository.findByTaggedName("A Book", emptyList(), preferring)!!.id }
        bookRepository.modify(userId, locked, libId, bookUpdate(title = "Mine"))

        bookRepository.autoMatch(userId, locked, libId)
        bookRepository.autoMatch(userId, fileWins, preferring)

        val matched = bookRepository.raw(locked, libId)
        assertEquals("Mine", matched.title, "a locked field is never matched over")
        assertEquals(setOf(BookField.TITLE), matched.locked)
        assertEquals("From the agent", matched.description)
        assertEquals("fake", matched.provider)
        val preferred = bookRepository.raw(fileWins, preferring)
        assertEquals("From the tags", preferred.description, "the files are preferred, the agent only fills gaps")
        assertEquals("123", preferred.isbn)
        assertEquals("B000000123", preferred.asin)
        assertEquals("fake", preferred.provider, "the match itself is always recorded")
    }

    @Test
    fun `an author list the user chose survives a rescan`() {
        trackManager.insert(scan(authors = listOf("Tagged Author")), libId)
        val id = bookId()
        assertEquals(listOf("Tagged Author"), authorNames(id), "sanity: the tags name an author")
        val chosen = newAuthor("Chosen Author", libId)

        bookRepository.modify(userId, id, libId, bookUpdate(authors = listOf(chosen)))
        trackManager.insert(scan(authors = listOf("Tagged Author")), libId)

        assertEquals(listOf("Chosen Author"), authorNames(id), "the tags must not add their author back")
    }

    @Test
    fun `a file whose album tag changed moves to another book`() {
        trackManager.insert(scan(), libId)
        val original = bookId()

        trackManager.insert(scan(book = "Another Book"), libId)

        assertEquals(2L, bookCount())
        assertEquals(0L, trackCount(original), "the file moved to the new book, leaving the old one empty")
    }

    @Test
    fun `a new file of a book renamed by the user joins that book`() {
        trackManager.insert(scan(), libId)
        val id = bookId()
        bookRepository.modify(userId, id, libId, bookUpdate(title = "My Better Title"))

        trackManager.insert(scan(path = "/media/books/A Book/02.mp3"), libId)

        assertEquals("My Better Title", bookRepository.raw(id, libId).title)
        assertEquals(2L, trackCount(id), "the files still name the old title, which the book remembers")
        assertEquals(1, bookRepository.getAll(userId, libId, SortOrder.ASC).size)
    }

    @Test
    fun `a file tagged with the new title does not split off the files tagged with the old one`() {
        trackManager.insert(scan(), libId)
        val id = bookId()
        bookRepository.modify(userId, id, libId, bookUpdate(title = "My Better Title"))
        trackManager.insert(scan(book = "My Better Title", path = "/media/books/A Book/02.mp3"), libId)

        trackManager.insert(scan(description = "Retagged"), libId)

        assertEquals(1L, bookCount(), "both files belong to the one book")
        assertEquals(2L, trackCount(id))
        assertEquals("A Book", bookRepository.raw(id, libId).taggedName)
    }

    @Test
    fun `an author and a series renamed by the user are still found by their tagged names`() {
        trackManager.insert(scan(authors = listOf("Tagged Author"), series = "Tagged Series"), libId)
        val book = bookRepository.get(userId, bookId(), libId)
        val authorId = book.authors.single().id
        val seriesId = book.series.single().id
        authorRepository.modify(userId, authorId, libId, AuthorUpdate(name = Patch.Set("Renamed")))
        seriesRepository.modify(userId, seriesId, libId, SeriesUpdate(title = Patch.Set("Renamed")))

        assertEquals(authorId, authorRepository.findByTaggedName("Tagged Author", libId)?.id)
        assertEquals(seriesId, seriesRepository.findByTaggedName("Tagged Series", libId)?.id)
    }

    @Test
    fun `the book a retagged file leaves behind is cleaned up with its edits`() {
        trackManager.insert(scan(), libId)
        val original = bookId()
        bookRepository.modify(userId, original, libId, bookUpdate(description = "Mine"))

        trackManager.insert(scan(book = "Another Book"), libId)
        val cleanup = getKoin().get<LibraryCleanup>()
        cleanup.removeOrphans(libId)
        // The first cleanup only gives the orphan a deadline; the second one deletes it once it passed
        transaction {
            BookTable.update({ BookTable.deferDeletionUntil.isNotNull() }) {
                it[deferDeletionUntil] = Clock.System.now() - DEFER_DELETION_GRACE - 60.seconds
            }
        }
        cleanup.removeOrphans(libId)

        assertEquals(1L, bookCount(), "nothing on disk backs the old book any more")
        assertFailsWith<ErrorResponse> { bookRepository.raw(original, libId) }
    }

    @Test
    fun `a series stays one series across the authors of its books`() {
        trackManager.insert(
            scan(
                authors = listOf("First Author"),
                series = "Shared",
                book = "Book One",
                path = "/media/books/1/01.mp3",
            ),
            libId,
        )
        trackManager.insert(
            scan(
                authors = listOf("Second Author"),
                series = "Shared",
                book = "Book Two",
                path = "/media/books/2/01.mp3",
            ),
            libId,
        )

        assertEquals(1L, transaction { SeriesTable.selectAll().count() }, "one series, two authors")
        val repo = getKoin().get<SeriesRepository>()
        val id = transaction { repo.findByTaggedName("Shared", libId)!!.id }
        assertEquals(
            listOf("First Author", "Second Author"),
            repo
                .get(userId, id, libId)
                .authors
                .map { it.name }
                .sorted(),
        )
    }

    @Test
    fun `an author the user attached by hand survives orphan cleanup`() {
        trackManager.insert(scan(), libId)
        val id = bookId()
        val extra = newAuthor("Hand Picked", libId)

        bookRepository.modify(userId, id, libId, bookUpdate(authors = listOf(extra)))
        getKoin().get<LibraryCleanup>().removeOrphans(libId)

        assertEquals(listOf("Hand Picked"), authorNames(id))
    }

    @Test
    fun `a blanked field stays blank through a rescan until it is set`() {
        trackManager.insert(scan(description = "From the tags"), libId)
        val id = bookId()

        bookRepository.modify(userId, id, libId, BookUpdate(description = Patch.Set(null)))
        trackManager.insert(scan(description = "Retagged"), libId)
        assertNull(bookRepository.raw(id, libId).description, "a rescan must not fill a blanked field")

        bookRepository.modify(userId, id, libId, bookUpdate(title = "Other field"))
        assertNull(bookRepository.raw(id, libId).description, "editing another field must keep the blank")

        bookRepository.modify(userId, id, libId, bookUpdate(description = "Mine"))
        assertEquals("Mine", bookRepository.raw(id, libId).description)
    }

    @Test
    fun `resubmitting the current values locks nothing`() {
        trackManager.insert(scan(description = "From the tags"), libId)
        val id = bookId()
        val authors = bookRepository.get(userId, id, libId).authors.map { it.id }

        bookRepository.modify(
            userId,
            id,
            libId,
            bookUpdate(title = "A Book", description = "From the tags", authors = authors),
        )
        trackManager.insert(scan(description = "Retagged"), libId)

        assertEquals(emptySet(), bookRepository.raw(id, libId).locked)
        assertEquals("Retagged", bookRepository.raw(id, libId).description)
    }

    // TODO a rescan no longer finds a book whose authors a user changed: the tags still name the old authors, so
    //  the file moves to a new book and this one is left to the cleanup. Enable once a known file stays with its book.
    @Ignore
    @Test
    fun `unlocking hand picked authors lets the next scan bring back the tagged ones`() {
        trackManager.insert(scan(), libId)
        val id = bookId()
        bookRepository.modify(userId, id, libId, bookUpdate(authors = listOf(newAuthor("Hand Picked", libId))))

        bookRepository.modify(userId, id, libId, BookUpdate(unlock = Patch.Set(listOf(BookField.AUTHORS))))
        assertEquals(listOf("Hand Picked"), authorNames(id))

        trackManager.insert(scan(), libId)
        assertEquals(listOf("An Author"), authorNames(id))
    }

    @Test
    fun `a blanked series list stays empty through a rescan`() {
        trackManager.insert(scan(series = "Tagged Series"), libId)
        val id = bookId()
        assertEquals(1, bookRepository.get(userId, id, libId).series.size, "sanity: the tags name a series")

        bookRepository.modify(userId, id, libId, BookUpdate(series = Patch.Set(null)))
        trackManager.insert(scan(series = "Tagged Series"), libId)

        assertEquals(emptyList(), bookRepository.get(userId, id, libId).series.map { it.title })
    }

    @Test
    fun `a user moving a book between series keeps the index the tags gave it`() {
        trackManager.insert(scan(series = "Tagged Series", seriesIndex = 3f), libId)
        val id = bookId()
        val series = bookRepository
            .get(userId, id, libId)
            .series
            .single()
            .id
        val other = newSeries("Other Series", libId)

        bookRepository.modify(userId, id, libId, bookUpdate(series = listOf(series, other)))

        val index =
            transaction {
                SeriesBookTable
                    .selectAll()
                    .where { SeriesBookTable.series eq series }
                    .single()[SeriesBookTable.seriesIndex]
            }
        assertEquals(3f, index)
    }

    @Test
    fun `unlocking one field leaves the other locks alone`() {
        trackManager.insert(scan(), libId)
        val id = bookId()
        bookRepository.modify(userId, id, libId, bookUpdate(title = "Mine", description = "Mine"))

        bookRepository.modify(userId, id, libId, BookUpdate(unlock = Patch.Set(listOf(BookField.DESCRIPTION))))

        assertEquals(setOf(BookField.TITLE), bookRepository.raw(id, libId).locked)
    }

    @Test
    fun `a tag that disappears from the files leaves the value it wrote`() {
        trackManager.insert(scan(description = "From the tags", narrators = listOf("Tagged")), libId)
        val id = bookId()

        trackManager.insert(scan(), libId)

        val book = bookRepository.raw(id, libId)
        assertEquals("From the tags", book.description)
        assertEquals(listOf("Tagged"), book.narrators)
    }

    @Test
    fun `an edited field survives a match and follows the next one once unlocked`() {
        val hit = searchHit("A Book", authors = listOf("An Author"))
        useAgent(
            FakeMetadataAgent(
                hits = listOf(hit),
                resolveBook = { testBook(it).copy(authors = hit.authors, description = "From the agent") },
            ),
        )
        trackManager.insert(scan(description = "From the tags"), libId)
        val id = bookId()
        bookRepository.modify(userId, id, libId, bookUpdate(description = "Mine"))

        bookRepository.autoMatch(userId, id, libId)
        assertEquals("Mine", bookRepository.raw(id, libId).description)
        assertEquals(setOf(BookField.DESCRIPTION), bookRepository.raw(id, libId).locked, "a match releases no lock")

        bookRepository.modify(userId, id, libId, BookUpdate(unlock = Patch.Set(listOf(BookField.DESCRIPTION))))
        bookRepository.autoMatch(userId, id, libId)
        assertEquals("From the agent", bookRepository.raw(id, libId).description)
    }

    @Test
    fun `unlocking a hand picked series lets the next scan bring back the tagged one`() {
        trackManager.insert(scan(series = "Tagged Series"), libId)
        val id = bookId()
        bookRepository.modify(userId, id, libId, bookUpdate(series = listOf(newSeries("Hand Picked", libId))))
        trackManager.insert(scan(series = "Tagged Series"), libId)
        assertEquals(listOf("Hand Picked"), seriesTitles(id), "sanity: the lock holds through a rescan")

        bookRepository.modify(userId, id, libId, BookUpdate(unlock = Patch.Set(listOf(BookField.SERIES))))
        trackManager.insert(scan(series = "Tagged Series"), libId)

        assertEquals(listOf("Tagged Series"), seriesTitles(id))
    }

    @Test
    fun `a book taken out of a series from the side of the series stays out through a rescan`() {
        trackManager.insert(scan(series = "Tagged Series"), libId)
        val id = bookId()
        val seriesId = seriesRepository.findByTaggedName("Tagged Series", libId)!!.id
        val other = newBook("Other Book", libId)

        seriesRepository.modify(userId, seriesId, libId, SeriesUpdate(books = Patch.Set(listOf(other))))
        trackManager.insert(scan(series = "Tagged Series"), libId)

        assertEquals(emptyList(), seriesTitles(id))
        assertEquals(listOf("Tagged Series"), seriesTitles(other))
        assertEquals(setOf(BookField.SERIES), bookRepository.raw(id, libId).locked)
    }

    @Test
    fun `an author added from the side of the author stays through a rescan`() {
        trackManager.insert(scan(), libId)
        val id = bookId()
        val added = newAuthor("Added Author", libId)

        authorRepository.modify(userId, added, libId, AuthorUpdate(books = Patch.Set(listOf(id))))
        trackManager.insert(scan(), libId)

        assertEquals(listOf("Added Author", "An Author"), authorNames(id).sorted())
        assertEquals(setOf(BookField.AUTHORS), bookRepository.raw(id, libId).locked)
    }

    @Test
    fun `an edited author field survives a match and follows the next one once unlocked`() {
        useAgent(
            FakeMetadataAgent(
                hits = listOf(searchHit("A Book", authors = listOf("An Author"))),
                resolveAuthor = { testAuthor(it).copy(biography = "From the agent") },
            ),
        )
        trackManager.insert(scan(), libId)
        val id = authorRepository.findByTaggedName("An Author", libId)!!.id
        authorRepository.modify(userId, id, libId, AuthorUpdate(biography = Patch.Set("Mine")))

        authorRepository.autoMatch(userId, id, libId)
        assertEquals("Mine", authorRepository.raw(id, libId).biography)
        assertEquals("fake", authorRepository.raw(id, libId).provider)

        authorRepository.modify(userId, id, libId, AuthorUpdate(unlock = Patch.Set(listOf(AuthorField.BIOGRAPHY))))
        assertEquals("Mine", authorRepository.raw(id, libId).biography, "unlocking alone changes no value")
        authorRepository.autoMatch(userId, id, libId)
        assertEquals("From the agent", authorRepository.raw(id, libId).biography)
    }

    @Test
    fun `an edited series field survives a match and follows the next one once unlocked`() {
        useAgent(
            FakeMetadataAgent(
                hits =
                    listOf(searchHit("A Book", authors = listOf("An Author"), series = listOf("Tagged Series"))),
                resolveSeries = { testSeries(it).copy(description = "From the agent") },
            ),
        )
        trackManager.insert(scan(series = "Tagged Series"), libId)
        val id = seriesRepository.findByTaggedName("Tagged Series", libId)!!.id
        seriesRepository.modify(userId, id, libId, SeriesUpdate(description = Patch.Set("Mine")))

        seriesRepository.autoMatch(userId, id, libId)
        assertEquals("Mine", seriesRepository.raw(id, libId).description)
        assertEquals("fake", seriesRepository.raw(id, libId).provider)

        seriesRepository.modify(userId, id, libId, SeriesUpdate(unlock = Patch.Set(listOf(SeriesField.DESCRIPTION))))
        seriesRepository.autoMatch(userId, id, libId)
        assertEquals("From the agent", seriesRepository.raw(id, libId).description)
    }

    @Test
    fun `a match does not rename an author the user created`() {
        useAgent(
            FakeMetadataAgent(
                hits = listOf(searchHit("A Book", authors = listOf("My Author"))),
                resolveAuthor = { testAuthor(it).copy(name = "Agent Name", biography = "From the agent") },
            ),
        )
        val id = authorRepository.createManual("My Author", libId).id

        authorRepository.autoMatch(userId, id, libId)

        val author = authorRepository.raw(id, libId)
        assertEquals("My Author", author.name)
        assertEquals("From the agent", author.biography)
    }

    private fun useAgent(agent: FakeMetadataAgent) =
        getKoin().loadModules(listOf(module { single { MetadataAgents(listOf(agent)) } }), allowOverride = true)

    // Not through the repository: it hides a book whose files the pipeline found missing, and these files never
    // existed
    private fun seriesTitles(bookId: Uuid) =
        transaction { bookSeries(listOf(bookId))[bookId].orEmpty().map { it.title } }

    private fun bookCount() = transaction { BookTable.selectAll().count() }

    private fun trackCount(bookId: Uuid) =
        transaction { TrackTable.selectAll().where { TrackTable.book eq bookId }.count() }

    private fun authorNames(bookId: Uuid) = bookRepository.get(userId, bookId, libId).authors.map { it.name }

    private fun bookUpdate(
        title: String? = null,
        description: String? = null,
        authors: List<Uuid>? = null,
        series: List<Uuid>? = null,
    ) = BookUpdate(
        title = title.orAbsent(),
        authors = authors.orAbsent(),
        series = series.orAbsent(),
        description = description.orAbsent(),
    )
}
