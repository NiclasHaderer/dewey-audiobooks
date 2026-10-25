package io.dewey.server.file.scanner

import io.dewey.models.FileScanner
import io.dewey.openapi.common.Patch
import io.dewey.server.DeweyTest
import io.dewey.server.api.PartialUpdateLibrary
import io.dewey.server.common.extensions.canonical
import io.dewey.server.common.scheduling.Scheduler
import io.dewey.server.config.DeweyConfig
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.newLibrary
import io.dewey.server.repositories.LibraryRepository
import io.methvin.watcher.DirectoryChangeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.mp.KoinPlatform.getKoin
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class LibraryWatcherTest : DeweyTest() {
    private val watcher by lazy { getKoin().get<LibraryWatcher>() as LibraryWatcherImpl }

    // An overflow is answered by dispatching a scan, so the scheduler has to actually be running
    private val scheduler by lazy { getKoin().get<Scheduler>() }
    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var libraryRoot: Path
    private var libId: Uuid = Uuid.random()

    // Settling costs one extra round trip per file, so keep it short or every assertion waits on it
    override fun configure(dataDir: Path) = DeweyConfig(dataDir = dataDir, settleMillis = 50)

    private val sourceMp3: Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("test-resources") }
            .first { it.isDirectory() }
            .resolve("Terry Pratchett/Good Omens/Good Omens.mp3")

    @BeforeTest
    fun startWatcher() {
        libraryRoot = dataDir.resolve("library").also { it.createDirectories() }
        libId =
            newLibrary(
                "lib",
                folders = listOf(libraryRoot.absolutePathString()),
                fileScanners = listOf(FileScanner("AudioTagScanner"), FileScanner("AudioFolderScanner")),
            )
        schedulerScope.launch { scheduler.start() }
        // Blocks until the tree is registered; watchAsync still has to get its event loop going after that
        watcher.start()
        Thread.sleep(300)
    }

    @AfterTest
    fun stopWatcher() {
        watcher.stop()
        scheduler.stop()
        schedulerScope.cancel()
    }

    private fun addBook(
        author: String,
        title: String,
    ): Path {
        val folder = libraryRoot.resolve(author).resolve(title).also { it.createDirectories() }
        sourceMp3.copyTo(folder.resolve("$title.mp3"), overwrite = true)
        return folder
    }

    private fun titles() =
        transaction {
            BookTable
                .selectAll()
                .where { BookTable.visible }
                .mapNotNull { it[BookTable.title] }
                .sorted()
        }

    private fun tracks() = transaction { TrackTable.selectAll().count() }

    private fun eventually(
        timeout: Duration = 10.seconds,
        describe: () -> String,
        until: () -> Boolean,
    ) = runBlocking {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            if (until()) return@runBlocking
            delay(50)
        }
        throw AssertionError("Timed out waiting for: ${describe()}")
    }

    @Test
    fun `a new file is imported without a rescan`() {
        addBook("An Author", "A Book")

        eventually(describe = { "the book to be imported, saw ${titles()}" }) { titles() == listOf("A Book") }
    }

    @Test
    fun `a library that flips which source it prefers re-reads its files`() {
        addBook("An Author", "A Book")
        eventually(describe = { "the book to import first" }) { titles() == listOf("A Book") }
        val imported = transaction { TrackTable.selectAll().single()[TrackTable.title] }
        transaction { TrackTable.update({ TrackTable.library eq libId }) { it[title] = "Stale" } }

        getKoin()
            .get<LibraryRepository>()
            .modify(libId, PartialUpdateLibrary(preferEmbeddedMetadata = Patch.Set(true)))

        eventually(describe = { "the file to be read again although it did not change" }) {
            transaction { TrackTable.selectAll().single()[TrackTable.title] } == imported
        }
    }

    @Test
    fun `a deleted file is removed`() {
        val folder = addBook("An Author", "A Book")
        eventually(describe = { "the book to import first" }) { titles() == listOf("A Book") }

        @OptIn(kotlin.io.path.ExperimentalPathApi::class)
        folder.deleteRecursively()

        eventually(describe = { "the book to be dropped, saw ${titles()}" }) { titles().isEmpty() }
    }

    @Test
    fun `a burst of files is coalesced into one import pass`() {
        repeat(5) { index -> addBook("An Author", "Book $index") }

        eventually(describe = { "all five books, saw ${titles()}" }) { tracks() == 5L }
        assertEquals(5, titles().size)
    }

    @Test
    fun `adding an ignore marker invalidates the books already imported`() {
        addBook("An Author", "A Book")
        eventually(describe = { "the book to import first" }) { titles() == listOf("A Book") }

        libraryRoot.resolve("An Author").resolve(IGNORE_FILE).createFile()

        eventually(describe = { "the book to be invalidated, saw ${titles()}" }) { titles().isEmpty() }
        assertEquals(0L, tracks(), "its track must go too")
    }

    @Test
    fun `removing the ignore marker brings the books back`() {
        addBook("An Author", "A Book")
        eventually(describe = { "the book to import first" }) { titles() == listOf("A Book") }
        val marker = libraryRoot.resolve("An Author").resolve(IGNORE_FILE).createFile()
        eventually(describe = { "the book to be invalidated" }) { titles().isEmpty() }
        // The OS coalesces changes over a short window, so a marker created and deleted inside the same one
        // nets out to no event at all and the watcher never learns the folder came back
        Thread.sleep(1000)

        marker.deleteExisting()

        eventually(describe = { "the book to come back, saw ${titles()}" }) { titles() == listOf("A Book") }
    }

    @Test
    fun `an overflow event triggers a library scan, not a subtree walk`() {
        addBook("An Author", "A Book")
        eventually(describe = { "the book to import first" }) { titles() == listOf("A Book") }

        // Deleted with the watcher down, which is what a dropped event leaves behind: nothing is queued, and
        // a subtree walk could never find the file to reap it. Only a scan and its sweep can.
        watcher.stop()
        @OptIn(kotlin.io.path.ExperimentalPathApi::class)
        libraryRoot.resolve("An Author").resolve("A Book").deleteRecursively()
        addBook("Another Author", "Another Book")
        assertEquals(listOf("A Book"), titles(), "sanity: nothing changed while the watcher was down")

        watcher.onEvent(
            DirectoryChangeEvent(
                DirectoryChangeEvent.EventType.OVERFLOW,
                false,
                null,
                null,
                1,
                libraryRoot.canonical(),
            ),
        )

        eventually(describe = { "the overflow rescan, saw ${titles()}" }) { titles() == listOf("Another Book") }
    }

    @Test
    fun `an overflow event without a root is ignored`() {
        watcher.onEvent(
            DirectoryChangeEvent(DirectoryChangeEvent.EventType.OVERFLOW, false, null, null, 1, null),
        )

        Thread.sleep(300)
        assertEquals(emptyList(), titles())
    }

    @Test
    fun `concurrent restarts leave no watcher behind after stop`() {
        List(8) { Thread { watcher.restart() }.also { it.start() } }.forEach { it.join() }
        Thread.sleep(1000)

        watcher.stop()

        addBook("An Author", "A Book")
        Thread.sleep(2000)
        assertEquals(emptyList(), titles(), "a leaked watcher would still be importing")
    }

    @Test
    fun `a file added under an ignored folder is never imported`() {
        libraryRoot
            .resolve("An Author")
            .also { it.createDirectories() }
            .resolve(IGNORE_FILE)
            .createFile()
        Thread.sleep(300)

        addBook("An Author", "A Book")

        // Nothing should ever show up, so give the watcher longer than a passing case would need
        Thread.sleep(2000)
        assertEquals(emptyList(), titles(), "the marked folder must stay out of the library")
    }
}
