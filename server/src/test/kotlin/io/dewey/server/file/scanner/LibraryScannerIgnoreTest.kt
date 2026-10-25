package io.dewey.server.file.scanner

import io.dewey.models.FileScanner
import io.dewey.server.DeweyTest
import io.dewey.server.common.extensions.canonical
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.newLibrary
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.mp.KoinPlatform.getKoin
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.isDirectory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class LibraryScannerIgnoreTest : DeweyTest() {
    private val pipeline by lazy { getKoin().get<LibraryImportPipeline>() }

    private lateinit var libraryRoot: Path
    private var libId: Uuid = Uuid.random()

    private val sourceMp3: Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("test-resources") }
            .first { it.isDirectory() }
            .resolve("Terry Pratchett/Good Omens/Good Omens.mp3")

    @BeforeTest
    fun createLibrary() {
        libraryRoot = dataDir.resolve("library").also { it.createDirectories() }
        book("Kept Author", "Kept Book")
        book("Dropped Author", "Dropped Book")
        libId =
            newLibrary(
                "lib",
                folders = listOf(libraryRoot.absolutePathString()),
                fileScanners = listOf(FileScanner("AudioTagScanner"), FileScanner("AudioFolderScanner")),
            )
    }

    private fun book(
        author: String,
        title: String,
    ): Path {
        val folder = libraryRoot.resolve(author).resolve(title).also { it.createDirectories() }
        sourceMp3.copyTo(folder.resolve("$title.mp3"), overwrite = true)
        return folder
    }

    private fun scan() = pipeline.scanLibrary(libId)

    private fun titles() =
        transaction {
            BookTable
                .selectAll()
                .where { BookTable.visible }
                .mapNotNull { it[BookTable.title] }
                .sorted()
        }

    private fun counts() =
        transaction {
            Triple(
                TrackTable.selectAll().count(),
                BookTable.selectAll().where { BookTable.deferDeletionUntil.isNull() }.count(),
                AuthorTable.selectAll().where { AuthorTable.deferDeletionUntil.isNull() }.count(),
            )
        }

    @Test
    fun `a folder marked before the first scan is never imported`() {
        libraryRoot.resolve("Dropped Author").resolve(IGNORE_FILE).createFile()

        scan()

        assertEquals(listOf("Kept Book"), titles())
    }

    @Test
    fun `marking a folder invalidates the books already imported from it`() {
        scan()
        assertEquals(listOf("Dropped Book", "Kept Book"), titles(), "sanity: both books import first")

        libraryRoot.resolve("Dropped Author").resolve(IGNORE_FILE).createFile()
        scan()

        assertEquals(listOf("Kept Book"), titles(), "the book behind the new marker must be dropped")
        assertEquals(
            Triple(1L, 1L, 1L),
            counts(),
            "its track goes at once, and its book and now bookless author are hidden",
        )
    }

    @Test
    fun `removing the marker brings the books back`() {
        val marker = libraryRoot.resolve("Dropped Author").resolve(IGNORE_FILE).createFile()
        scan()
        assertEquals(listOf("Kept Book"), titles(), "sanity: the marked book is absent")

        marker.deleteExisting()
        scan()

        assertEquals(listOf("Dropped Book", "Kept Book"), titles())
    }

    @Test
    fun `a file added inside an ignored folder is not imported`() {
        libraryRoot.resolve("Dropped Author").resolve(IGNORE_FILE).createFile()
        scan()

        book("Dropped Author", "Sneaky Book")
        scan()

        assertEquals(listOf("Kept Book"), titles())
    }

    @Test
    fun `a subtree walk started below a marked folder still honours the marker`() {
        scan()
        libraryRoot.resolve("Dropped Author").resolve(IGNORE_FILE).createFile()
        val sneaky = book("Dropped Author", "Sneaky Book")

        pipeline.enqueue(sneaky.canonical())

        // Nothing should ever show up, so give the pool longer than a passing case would need
        Thread.sleep(2000)
        assertEquals(listOf("Dropped Book", "Kept Book"), titles(), "the new book must not be imported")
    }
}
