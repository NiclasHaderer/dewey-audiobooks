package io.dewey.server.repositories

import io.dewey.models.AuthorUpdate
import io.dewey.models.BookUpdate
import io.dewey.models.SeriesUpdate
import io.dewey.openapi.common.Patch
import io.dewey.openapi.common.orAbsent
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.DeweyTest
import io.dewey.server.database.access.getOrCreateImage
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.file.scanner.LibraryCleanup
import io.dewey.server.newAuthor
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.newSeries
import io.dewey.server.newTrack
import io.dewey.server.newUser
import io.dewey.server.pngBytes
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.mp.KoinPlatform.getKoin
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RepositoryTest : DeweyTest() {
    @Test
    fun `a hidden book, author and series are listed only with showInvisible`() {
        val author = newAuthor("Hidden Author")
        val series = newSeries("Hidden Series", libId)
        val book = newBook("Hidden Book", libId, authors = listOf(author), series = listOf(series))
        newTrack("Track", "/media/books/track.mp3", book, libId)
        getKoin().get<LibraryCleanup>().removeOrphans(libId)
        assertEquals(
            1,
            bookRepository.getAll(userId, libId, SortOrder.ASC).size,
            "sanity: visible while it has a track",
        )

        transaction { TrackTable.deleteWhere { TrackTable.book eq book } }
        getKoin().get<LibraryCleanup>().removeOrphans(libId)

        assertEquals(emptyList(), bookRepository.getAll(userId, libId, SortOrder.ASC), "hidden by default")
        assertEquals(emptyList(), authorRepository.getAll(userId, libId, SortOrder.ASC))
        assertEquals(emptyList(), seriesRepository.getAll(userId, libId, SortOrder.ASC))
        assertEquals(
            listOf(0L, 0L, 0L),
            listOf(bookRepository, authorRepository, seriesRepository).map { it.total(libId) },
        )

        assertEquals(
            listOf(book),
            bookRepository.getAll(userId, libId, SortOrder.ASC, showInvisible = true).map { it.id },
            "listed on request",
        )
        assertEquals(
            listOf(author),
            authorRepository.getAll(userId, libId, SortOrder.ASC, showInvisible = true).map { it.id },
        )
        assertEquals(
            listOf(series),
            seriesRepository.getAll(userId, libId, SortOrder.ASC, showInvisible = true).map { it.id },
        )
        assertEquals(
            listOf(1L, 1L, 1L),
            listOf(bookRepository, authorRepository, seriesRepository).map { it.total(libId, showInvisible = true) },
        )
        assertEquals(1, bookRepository.getAll(userId, libId, SortOrder.ASC, showInvisible = true).size)
    }

    @Test
    fun `a manually created series is hidden until a book joins it`() {
        val series = seriesRepository.createManual("Discworld", libId).id

        assertEquals(emptyList(), seriesRepository.getAll(userId, libId, SortOrder.ASC), "no book hangs off it yet")
        assertEquals(
            listOf(series),
            seriesRepository.getAll(userId, libId, SortOrder.ASC, showInvisible = true).map { it.id },
        )

        val book = newBook("Mort", libId)
        bookRepository.modify(userId, book, libId, bookAssignedTo(series = listOf(series)))

        assertEquals(
            listOf(series),
            seriesRepository.getAll(userId, libId, SortOrder.ASC).map { it.id },
            "the book edit un-hides it",
        )
    }

    @Test
    fun `an author the last book was taken away from is hidden again`() {
        val author = authorRepository.createManual("Terry Pratchett", libId).id
        val book = newBook("Mort", libId)
        bookRepository.modify(userId, book, libId, bookAssignedTo(authors = listOf(author)))
        assertEquals(
            listOf(author),
            authorRepository.getAll(userId, libId, SortOrder.ASC).map { it.id },
            "sanity: visible with a book",
        )

        bookRepository.modify(userId, book, libId, bookAssignedTo(authors = emptyList()))

        assertEquals(emptyList(), authorRepository.getAll(userId, libId, SortOrder.ASC))
    }

    @Test
    fun `an author the book was moved away from is hidden, not left empty`() {
        val tagged = newAuthor("Tagged Author", libId)
        val chosen = newAuthor("Chosen Author", libId)
        val book = newBook("Mort", libId, authors = listOf(tagged))

        bookRepository.modify(userId, book, libId, bookAssignedTo(authors = listOf(chosen)))

        assertEquals(
            listOf(chosen),
            authorRepository.getAll(userId, libId, SortOrder.ASC).map { it.id },
            "the author the tags name is hidden, since no book is linked to them any more",
        )
        assertEquals(
            listOf(chosen, tagged).sorted(),
            authorRepository.getAll(userId, libId, SortOrder.ASC, showInvisible = true).map { it.id }.sorted(),
            "they are only hidden: the row is still there for a scan to link again",
        )
    }

    @Test
    fun `a series the book was moved out of is hidden, not left empty`() {
        val tagged = newSeries("Tagged Series", libId)
        val chosen = newSeries("Chosen Series", libId)
        val book = newBook("Mort", libId, authors = listOf(newAuthor("Pratchett")), series = listOf(tagged))

        bookRepository.modify(userId, book, libId, bookAssignedTo(series = listOf(chosen)))

        assertEquals(
            listOf("Mort"),
            seriesRepository.get(userId, chosen, libId).books.map { it.title },
            "the series the user chose has the book",
        )
        assertEquals(
            listOf(chosen),
            seriesRepository.getAll(userId, libId, SortOrder.ASC).map { it.id },
            "the series the tags name is hidden, since no book is linked to it any more",
        )
        assertEquals(
            listOf(chosen, tagged).sorted(),
            seriesRepository.getAll(userId, libId, SortOrder.ASC, showInvisible = true).map { it.id }.sorted(),
            "it is only hidden: the row is still there for a scan to link again",
        )
    }

    private val libraryRepository by lazy { getKoin().get<LibraryRepository>() as LibraryRepositoryImpl }
    private val authorRepository by lazy { getKoin().get<AuthorRepository>() }
    private val bookRepository by lazy { getKoin().get<BookRepository>() }
    private val seriesRepository by lazy { getKoin().get<SeriesRepository>() }
    private val narratorRepository by lazy { getKoin().get<NarratorRepository>() }
    private val genreRepository by lazy { getKoin().get<GenreRepository>() }

    private var libId: Uuid = Uuid.random()
    private var userId: Uuid = Uuid.random()

    @BeforeTest
    fun createLibrary() {
        libId = newLibrary("lib", folders = listOf("/media/books"))
        userId = newUser("test-user", admin = true)
    }

    private fun newAuthor(authorName: String) = newAuthor(authorName, libId)

    private fun authorCount() = transaction { AuthorTable.selectAll().where { AuthorTable.library eq libId }.count() }

    @Test
    fun `author findByTaggedName matches ignoring case`() {
        newAuthor("Stephen King")
        assertNotNull(authorRepository.findByTaggedName("stephen king", libId))
        assertNotNull(authorRepository.findByTaggedName("STEPHEN KING", libId))
    }

    @Test
    fun `author findByTaggedName treats like wildcards as literals`() {
        newAuthor("Stephen King")
        assertNull(authorRepository.findByTaggedName("Stephen_King", libId), "'_' must not act as a wildcard")
        assertNull(authorRepository.findByTaggedName("%", libId), "'%' must not act as a wildcard")
        assertNull(authorRepository.findByTaggedName("Stephen%", libId), "trailing '%' must not act as a wildcard")
    }

    @Test
    fun `author search matches ignoring case and escapes wildcards`() {
        newAuthor("Brandon Sanderson")
        newAuthor("100% Author")
        assertEquals(listOf("Brandon Sanderson"), authorRepository.search(userId, "sanderson", libId).map { it.name })
        assertEquals(listOf("100% Author"), authorRepository.search(userId, "100%", libId).map { it.name })
        assertEquals(emptyList(), authorRepository.search(userId, "Brandon_Sanderson", libId).map { it.name })
    }

    @Test
    fun `author search is scoped to the given library`() {
        newAuthor("Brandon Sanderson")
        val otherLib = newLibrary("other", folders = listOf("/media/other"))
        assertEquals(emptyList(), authorRepository.search(userId, "sanderson", otherLib).map { it.name })
        assertEquals(listOf("Brandon Sanderson"), authorRepository.search(userId, "sanderson", libId).map { it.name })
    }

    @Test
    fun `author getOrCreate reuses an author that differs only in casing`() {
        val first = authorRepository.getOrCreate("Terry Pratchett", libId).id
        val second = authorRepository.getOrCreate("terry pratchett", libId).id
        assertEquals(first, second)
        assertEquals(1L, authorCount())
    }

    @Test
    fun `renaming an author leaves the name the next scan matches on untouched`() {
        val id = newAuthor("Terry Pratchet")
        val renamed = authorRepository.modify(userId, id, libId, authorRenamedTo("Terry Pratchett"))

        assertEquals("Terry Pratchett", renamed.name, "the API must show the new name")
        assertEquals(
            id,
            authorRepository.getOrCreate("Terry Pratchet", libId).id,
            "a rescan finding the old name in the files must reuse the author instead of creating a second one",
        )
        assertEquals(1L, authorCount())
    }

    @Test
    fun `search only matches the name the user is shown`() {
        val id = newAuthor("Unknown Author")
        // Search only turns up visible authors, so this one needs a book
        newBook("Discworld", libId, authors = listOf(id))
        authorRepository.modify(userId, id, libId, authorRenamedTo("Terry Pratchett"))

        assertEquals(listOf("Terry Pratchett"), authorRepository.search(userId, "Pratchett", libId).map { it.name })
        assertEquals(
            emptyList(),
            authorRepository.search(userId, "Unknown", libId).map { it.name },
            "a spelling that only the files know is never displayed, so it must not produce a hit",
        )
    }

    @Test
    fun `a scan whose tags carry the renamed author reuses it`() {
        val id = newAuthor("Terry Pratchet")
        authorRepository.modify(userId, id, libId, authorRenamedTo("Terry Pratchett"))

        // The tags on disk were corrected too, so the scan now sees the name the user gave, not the tagged one
        assertEquals(id, authorRepository.getOrCreate("Terry Pratchett", libId).id)
        assertEquals(1L, authorCount())
    }

    @Test
    fun `renaming a series keeps it matchable under both titles`() {
        val id = seriesRepository.create("Diskworld", libId).id
        val renamed = seriesRepository.modify(userId, id, libId, seriesRenamedTo("Discworld"))

        assertEquals("Discworld", renamed.title, "the API must show the new title")
        assertEquals(id, seriesRepository.getOrCreate("Diskworld", libId).id)
        assertEquals(id, seriesRepository.getOrCreate("Discworld", libId).id)
        assertEquals(1L, transaction { SeriesTable.selectAll().count() })
    }

    @Test
    fun `renaming a book keeps it matchable under both titles`() {
        val id = bookRepository.create("Guards Guards", libId, emptyList(), emptyList()).id
        val renamed = bookRepository.modify(userId, id, libId, bookRenamedTo("Guards! Guards!"))

        assertEquals("Guards! Guards!", renamed.title, "the API must show the new title")
        assertEquals(id, bookRepository.findByTaggedName("Guards Guards", emptyList(), libId)?.id)
        assertEquals(id, bookRepository.findByTaggedName("Guards! Guards!", emptyList(), libId)?.id)
    }

    private fun seriesRenamedTo(newTitle: String) = SeriesUpdate(title = Patch.Set(newTitle))

    private fun bookAssignedTo(
        authors: List<Uuid>? = null,
        series: List<Uuid>? = null,
    ) = bookRenamedTo("Mort").copy(authors = authors.orAbsent(), series = series.orAbsent())

    private fun bookRenamedTo(newTitle: String) = BookUpdate(title = Patch.Set(newTitle))

    private fun authorRenamedTo(newName: String) = AuthorUpdate(name = Patch.Set(newName))

    @Test
    fun `book findByTaggedName finds a book that has no authors`() {
        val created = bookRepository.create("Orphan Book", libId, emptyList(), emptyList()).id
        val found = bookRepository.findByTaggedName("Orphan Book", emptyList(), libId)
        assertNotNull(found, "a book without authors must still be findable by title")
        assertEquals(created, found.id)
    }

    @Test
    fun `book findByTaggedName stays scoped to the given authors`() {
        val wanted = authorRepository.getOrCreate("Wanted", libId)
        val other = authorRepository.getOrCreate("Other", libId)
        val book = bookRepository.create("Shared Title", libId, listOf(wanted.id), emptyList())
        assertEquals(book.id, bookRepository.findByTaggedName("shared title", listOf(wanted.id), libId)?.id)
        assertNull(bookRepository.findByTaggedName("Shared Title", listOf(other.id), libId))
    }

    @Test
    fun `book findByTaggedName treats like wildcards as literals`() {
        val authorId = authorRepository.getOrCreate("Author", libId).id
        bookRepository.create("Book One", libId, listOf(authorId), emptyList())
        assertNotNull(bookRepository.findByTaggedName("book one", listOf(authorId), libId), "sanity: the book exists")
        assertNull(bookRepository.findByTaggedName("Book_One", listOf(authorId), libId))
        assertNull(bookRepository.findByTaggedName("Book%", listOf(authorId), libId))
        assertNull(bookRepository.findByTaggedName("Book_One", emptyList(), libId))
    }

    @Test
    fun `modify treats the book's own cover id as unchanged`() {
        val id = bookRepository.create("Covered", libId, emptyList(), emptyList()).id
        val cover = transaction { getOrCreateImage(pngBytes(1, 2, 3), null)!! }
        transaction { BookTable.update({ BookTable.id eq id }) { it[coverId] = cover } }

        val result =
            bookRepository.modify(
                userId,
                id,
                libId,
                bookRenamedTo("Covered").copy(cover = Patch.Set(cover.toString())),
            )

        assertEquals(cover, result.coverID, "echoing the current cover id back must not touch the image")
    }

    @Test
    fun `modify rejects an image id instead of linking someone else's image`() {
        val id = bookRepository.create("Plain", libId, emptyList(), emptyList()).id
        val foreignImage = transaction { getOrCreateImage(pngBytes(9, 9, 9), null)!! }

        assertFails("an image id that is not the book's own must not be linkable") {
            bookRepository.modify(
                userId,
                id,
                libId,
                bookRenamedTo("Plain").copy(cover = Patch.Set(foreignImage.toString())),
            )
        }
        assertEquals(null, bookRepository.raw(id, libId).coverID)
    }

    @Test
    fun `book search orders results by title`() {
        bookRepository.create("The Zebra Mystery", libId, emptyList(), emptyList())
        bookRepository.create("The Antelope Mystery", libId, emptyList(), emptyList())
        bookRepository.create("The Mule Mystery", libId, emptyList(), emptyList())
        assertEquals(
            listOf("The Antelope Mystery", "The Mule Mystery", "The Zebra Mystery"),
            bookRepository.search(userId, "mystery", libId).map { it.title },
        )
    }

    private fun bookWithTracks(
        bookTitle: String,
        vararg tracks: Pair<String, Int?>,
    ): Uuid {
        val book = bookRepository.create(bookTitle, libId, emptyList(), emptyList())
        tracks.forEach { (fileName, number) ->
            newTrack(
                title = fileName,
                path = "/media/books/$bookTitle/$fileName",
                bookId = book.id,
                libraryId = libId,
                trackNr = number,
            )
        }
        return book.id
    }

    @Test
    fun `tracks are returned in track number order`() {
        val id = bookWithTracks("Numbered", "b.mp3" to 2, "c.mp3" to 10, "a.mp3" to 1)
        assertEquals(listOf(1, 2, 10), bookRepository.get(userId, id, libId).tracks.map { it.trackNr })
    }

    @Test
    fun `tracks without numbers fall back to the natural order of their file names`() {
        val id = bookWithTracks("Unnumbered", "Chapter 10.mp3" to null, "Chapter 2.mp3" to null)
        assertEquals(
            listOf("Chapter 2.mp3", "Chapter 10.mp3"),
            bookRepository.get(userId, id, libId).tracks.map { it.title },
            "'10' must not sort before '2'",
        )
    }

    @Test
    fun `a single missing track number makes the whole book fall back to file names`() {
        val id = bookWithTracks("Partly numbered", "Chapter 10.mp3" to 1, "Chapter 2.mp3" to null)
        assertEquals(
            listOf("Chapter 2.mp3", "Chapter 10.mp3"),
            bookRepository.get(userId, id, libId).tracks.map { it.title },
        )
    }

    @Test
    fun `series raw works without a surrounding transaction`() {
        val id = seriesRepository.create("Mistborn", libId).id
        assertEquals(id, seriesRepository.raw(id, libId).id)
    }

    @Test
    fun `series getOrCreate works without a surrounding transaction`() {
        val id = seriesRepository.getOrCreate("Stormlight", libId).id
        assertEquals(id, seriesRepository.getOrCreate("Stormlight", libId).id)
        assertEquals(1L, transaction { SeriesTable.selectAll().count() })
    }

    @Test
    fun `a series is credited to the authors of its books, without duplicates`() {
        val author = authorRepository.getOrCreate("Sanderson", libId)
        val series = seriesRepository.getOrCreate("Stormlight", libId)
        newBook("The Way of Kings", libId, authors = listOf(author.id), series = listOf(series.id))
        newBook("Words of Radiance", libId, authors = listOf(author.id), series = listOf(series.id))

        assertEquals(
            listOf("Sanderson"),
            seriesRepository.get(userId, series.id, libId).authors.map { it.name },
            "two books by one author must credit them once",
        )
    }

    @Test
    fun `narrators are counted across books, ignoring case`() {
        newBook("One", libId, narrators = listOf("Jim Dale", "Stephen Fry"))
        newBook("Two", libId, narrators = listOf("jim dale"))
        newBook("Three", newLibrary("other", folders = listOf("/media/other")), narrators = listOf("Rob Inglis"))

        assertEquals(
            listOf("Jim Dale" to 2, "Stephen Fry" to 1),
            narratorRepository.getAll(libId, SortOrder.ASC).map { it.name to it.bookCount },
        )
        val detailed = narratorRepository.get(userId, "JIM DALE", libId)
        assertEquals("Jim Dale", detailed.name, "the answer uses the library's spelling, not the request's")
        assertEquals(listOf("One", "Two"), detailed.books.map { it.title })
        assertFailsWith<ErrorResponse> { narratorRepository.get(userId, "Rob Inglis", libId) }
    }

    @Test
    fun `genres are counted across books`() {
        newBook("One", libId, genres = listOf("Fantasy", "Sci-Fi"))
        newBook("Two", libId, genres = listOf("Fantasy"))

        assertEquals(
            listOf("Fantasy" to 2, "Sci-Fi" to 1),
            genreRepository.getAll(libId, SortOrder.ASC).map { it.name to it.bookCount },
        )
        assertEquals(listOf("One"), genreRepository.get(userId, "Sci-Fi", libId).books.map { it.title })
    }

    @Test
    fun `deleting a library takes its content with it`() {
        newBook("Doomed", libId)
        libraryRepository.delete(libId)

        assertFailsWith<ErrorResponse> { libraryRepository.get(libId) }
        assertEquals(0L, transaction { BookTable.selectAll().count() })
    }

    @Test
    fun `library folders overlapping an existing library are rejected in both directions`() {
        assertTrue(
            libraryRepository.overlappingFolders(null, listOf("/media/books/scifi")).first,
            "a folder inside an existing library folder overlaps",
        )
        assertTrue(
            libraryRepository.overlappingFolders(null, listOf("/media")).first,
            "a folder containing an existing library folder overlaps",
        )
        assertTrue(
            libraryRepository.overlappingFolders(null, listOf("/media/books")).first,
            "an identical folder overlaps",
        )
    }

    @Test
    fun `library folder overlap ignores unrelated folders and the library itself`() {
        assertFalse(libraryRepository.overlappingFolders(null, listOf("/other")).first)
        assertFalse(
            libraryRepository.overlappingFolders(libId, listOf("/media/books")).first,
            "a library must not overlap with itself",
        )
        assertFalse(
            libraryRepository.overlappingFolders(null, listOf("/media/booksomething")).first,
            "a sibling with a shared name prefix is not nested",
        )
    }

    @Test
    fun `auto match reports a 404 when no agent has a match`() {
        // A library without agents can never match, which keeps the assertion off the live metadata APIs
        val agentless = newLibrary("agentless", folders = listOf("/media/agentless"), metadataAgents = emptyList())
        val author = newAuthor("Nobody", agentless)
        val series = newSeries("No Series", agentless)
        val book = newBook("No Book", agentless, authors = listOf(author), series = listOf(series))

        listOf(
            "No Book" to { bookRepository.autoMatch(userId, book, agentless) },
            "Nobody" to { authorRepository.autoMatch(userId, author, agentless) },
            "No Series" to { seriesRepository.autoMatch(userId, series, agentless) },
        ).forEach { (searchedFor, match) ->
            val error = assertFailsWith<ErrorResponse>(searchedFor) { match() }
            assertEquals(HttpStatusCode.NotFound, error.status)
            assertEquals("No metadata agent of the library had a match for '$searchedFor'", error.error)
        }
    }
}
