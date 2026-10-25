package io.dewey.server.file.scanner

import io.dewey.models.BookUpdate
import io.dewey.models.FileScanner
import io.dewey.openapi.common.Patch
import io.dewey.server.DeweyTest
import io.dewey.server.common.extensions.canonical
import io.dewey.server.config.DeweyConfig
import io.dewey.server.database.sqliteUrl
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookField
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.LibraryTable
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.newLibrary
import io.dewey.server.newUser
import io.dewey.server.repositories.BookRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.mp.KoinPlatform.getKoin
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendBytes
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class LibraryImportPipelineTest : DeweyTest() {
    private val pipeline by lazy { getKoin().get<LibraryImportPipeline>() }

    override fun configure(dataDir: Path) = DeweyConfig(dataDir = dataDir, settleMillis = 50)

    private val testResources: Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("test-resources") }
            .first { it.isDirectory() }

    private val sourceMp3: Path = testResources.resolve("Terry Pratchett/Good Omens/Good Omens.mp3")

    private var restorePermissions: Path? = null

    @AfterTest
    fun restoreAccess() {
        // Or the temp directory cannot be deleted and the next test inherits the mess
        restorePermissions?.let { Files.setPosixFilePermissions(it, PosixFilePermission.entries.toSet()) }
    }

    private fun library(
        root: Path,
        scanners: List<FileScanner> =
            listOf(FileScanner("AudioTagScanner"), FileScanner("AudioFolderScanner")),
    ): Uuid =
        newLibrary(
            "lib-${root.fileName}",
            folders = listOf(root.absolutePathString()),
            fileScanners = scanners,
        )

    private fun book(
        root: Path,
        author: String,
        title: String,
        copies: Int = 1,
    ): Path {
        val folder = root.resolve(author).resolve(title).also { it.createDirectories() }
        repeat(copies) { index -> sourceMp3.copyTo(folder.resolve("$title $index.mp3"), overwrite = true) }
        return folder
    }

    private fun scan(id: Uuid) = pipeline.scanLibrary(id)

    private fun titles() =
        transaction {
            BookTable
                .selectAll()
                .where { BookTable.visible }
                .mapNotNull { it[BookTable.title] }
                .sorted()
        }

    private fun tracks() = transaction { TrackTable.selectAll().count() }

    private fun trackTitles() = transaction { TrackTable.selectAll().map { it[TrackTable.title] }.sorted() }

    private fun eventually(
        timeout: Duration = 15.seconds,
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
    fun `a scan imports every book in the tree`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        book(root, "An Author", "A Book")
        book(root, "An Author", "B Book")
        book(root, "Another Author", "C Book")

        scan(library(root))

        assertEquals(listOf("A Book", "B Book", "C Book"), titles())
        assertEquals(3L, tracks())
    }

    @Test
    fun `only a scan that asks for it re-reads a file that has not changed`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        book(root, "An Author", "A Book")
        val id = library(root)
        scan(id)
        val imported = trackTitles()

        // Stands for an analysis that is stale for a reason the file cannot show: the scanners changed
        transaction { TrackTable.update({ TrackTable.library eq id }) { it[title] = "Stale" } }

        scan(id)
        assertEquals(listOf("Stale"), trackTitles(), "an unchanged file must not be read again")

        pipeline.scanLibrary(id, reanalyze = true)
        assertEquals(imported, trackTitles(), "a re-analyzing scan must read it regardless of its mtime")
    }

    @Test
    fun `unlocking a field re-reads the files of the book`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        book(root, "An Author", "A Book")
        val id = library(root)
        scan(id)
        val bookId = transaction { BookTable.selectAll().single()[BookTable.id].value }
        val books = getKoin().get<BookRepository>()
        val userId = newUser("test-user", admin = true)
        books.modify(userId, bookId, id, BookUpdate(title = Patch.Set("Mine")))
        assertEquals(listOf("Mine"), titles())

        books.modify(userId, bookId, id, BookUpdate(unlock = Patch.Set(listOf(BookField.TITLE))))

        eventually(describe = { "the title of the files to come back, titles are ${titles()}" }) {
            titles() == listOf("A Book")
        }
    }

    @Test
    fun `many tracks of one series import as one author and one series`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        // Two books sharing an author and a series, analyzed in parallel by every worker at once. Any design
        // that writes from more than one thread duplicates the author or the series here, because neither
        // table has a unique index to fall back on.
        testResources.resolve("Dan Brown").toFile().copyRecursively(root.resolve("Dan Brown").toFile())

        scan(library(root))

        transaction {
            assertEquals(1L, AuthorTable.selectAll().count(), "the author must not be duplicated")
            assertEquals(1L, SeriesTable.selectAll().count(), "the series must not be duplicated")
            assertEquals(3L, BookTable.selectAll().count())
            assertEquals(5L, TrackTable.selectAll().count())
        }
    }

    @Test
    fun `a failed listing does not sweep anything`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        book(root, "Readable Author", "A Book")
        val unreadable = book(root, "Locked Author", "B Book").parent
        val libId = library(root)
        scan(libId)
        assertEquals(2L, tracks(), "sanity: both books import first")

        restorePermissions = unreadable
        Files.setPosixFilePermissions(unreadable, emptySet())
        scan(libId)

        assertEquals(
            2L,
            tracks(),
            "a folder that could not be listed says nothing about whether its tracks are still there",
        )
    }

    @Test
    fun `an empty root does not sweep anything`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        val folder = book(root, "An Author", "A Book")
        val libId = library(root)
        scan(libId)
        assertEquals(1L, tracks(), "sanity: the book imports first")

        @OptIn(kotlin.io.path.ExperimentalPathApi::class)
        folder.parent.deleteRecursively()
        scan(libId)

        assertEquals(1L, tracks(), "an empty root is far more likely to be a broken mount than an empty library")
    }

    @Test
    fun `a missing root does not sweep anything`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        book(root, "An Author", "A Book")
        val libId = library(root)
        scan(libId)
        assertEquals(1L, tracks(), "sanity: the book imports first")

        @OptIn(kotlin.io.path.ExperimentalPathApi::class)
        root.deleteRecursively()
        scan(libId)

        assertEquals(1L, tracks())
    }

    @Test
    fun `a folder that gains an ignore marker is swept without an explicit removal`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        book(root, "Kept Author", "A Book")
        book(root, "Dropped Author", "B Book")
        val libId = library(root)
        scan(libId)
        assertEquals(listOf("A Book", "B Book"), titles(), "sanity: both books import first")

        root.resolve("Dropped Author").resolve(IGNORE_FILE).createFile()
        scan(libId)

        // Not descending is the whole removal: the tracks behind the marker never get stamped and the sweep
        // collects them, so scan mode needs no RemoveSubtree of its own.
        assertEquals(listOf("A Book"), titles())
        assertEquals(1L, tracks())
    }

    @Test
    fun `a broken file does not stop the rest of the tree from importing`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        book(root, "Good Author", "A Book")
        book(root, "Good Author", "B Book")
        val broken = root.resolve("Bad Author").resolve("Broken Book").also { it.createDirectories() }
        broken.resolve("empty.mp3").createFile()
        broken.resolve("garbage.mp3").writeText("this is not an audio file")
        // A directory that looks like a track, which the walk has to treat as a directory
        root.resolve("Bad Author").resolve("directory.mp3").createDirectories()

        scan(library(root))

        // AudioFolderScanner falls back to the folder names, so an unreadable file still yields a title;
        // what matters is that every good file lands and the walk did not stop at the broken folder.
        assertTrue(titles().containsAll(listOf("A Book", "B Book")), "saw ${titles()}")
        assertEquals(
            2L,
            transaction {
                TrackTable
                    .selectAll()
                    .count { it[TrackTable.title].contains("Book") && !it[TrackTable.path].contains("Bad Author") }
            }.toLong(),
            "both good tracks must be imported",
        )
    }

    @Test
    fun `a watch event raised during a scan is applied once the scan is over`() {
        val scanned = dataDir.resolve("scanned").also { it.createDirectories() }
        val watched = dataDir.resolve("watched").also { it.createDirectories() }
        // Big enough that the scan is still running when the watch item is queued
        repeat(60) { index -> book(scanned, "An Author", "Book $index", copies = 3) }
        library(scanned)
        library(watched)
        val late = book(watched, "Late Author", "Late Book")

        val scannedId =
            transaction {
                LibraryTable
                    .select(LibraryTable.id)
                    .where { LibraryTable.name eq "lib-scanned" }
                    .first()[LibraryTable.id]
                    .value
            }
        val scanning = Thread { scan(scannedId) }.also { it.start() }
        // The pool drains one queue at a time, so this sits in the watch queue until the scan is done
        while (!pipeline.scanning && scanning.isAlive) Thread.sleep(1)
        val caughtMidScan = pipeline.scanning
        pipeline.enqueue(late.canonical())
        val importedDuringScan = pipeline.scanning && titles().contains("Late Book")
        scanning.join()

        // Without this the test quietly degrades into an ordinary watch import whenever the scan wins the race
        assertTrue(caughtMidScan, "the scan finished before the event was queued, so nothing was buffered")
        assertFalse(importedDuringScan, "watch items must not be handled while a scan owns the pool")
        eventually(describe = { "the buffered watch event, saw ${titles()}" }) { titles().contains("Late Book") }
        assertEquals(181L, tracks(), "the scanned tracks and the buffered one")
    }

    @Test
    fun `an in-progress transfer extension is dropped outright`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        val folder = root.resolve("An Author").resolve("A Book").also { it.createDirectories() }
        library(root)

        folder.resolve("A Book.mp3.part").writeText("half a file")
        pipeline.enqueue(folder.resolve("A Book.mp3.part").canonical())

        Thread.sleep(1000)
        assertEquals(emptyList(), titles(), "an in-progress transfer must never be read")
        // Positive control: the pool is still alive, so the assertion above means something
        sourceMp3.copyTo(folder.resolve("A Book.mp3"))
        pipeline.enqueue(folder.resolve("A Book.mp3").canonical())
        eventually(describe = { "the finished file, saw ${titles()}" }) { titles() == listOf("A Book") }
    }

    @Test
    fun `a settled file is imported even while its path is revisited constantly`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        val folder = root.resolve("An Author").resolve("A Book").also { it.createDirectories() }
        library(root)
        val target = folder.resolve("A Book.mp3")
        sourceMp3.copyTo(target)
        val resolved = target.canonical()

        // Re-queued far more often than the settle interval. If the interval is measured from the last visit
        // rather than from the last actual change, this file never settles and is never imported.
        val revisiting = AtomicBoolean(true)
        val revisits =
            Thread {
                while (revisiting.get()) {
                    pipeline.enqueue(resolved)
                    Thread.sleep(5)
                }
            }.also { it.start() }
        try {
            eventually(describe = { "the settled file, saw ${titles()}" }) { titles() == listOf("A Book") }
        } finally {
            revisiting.set(false)
            revisits.join()
        }
    }

    @Test
    fun `a file whose size keeps growing is not read until it stops`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        val folder = root.resolve("An Author").resolve("A Book").also { it.createDirectories() }
        library(root)
        val target = folder.resolve("A Book.mp3")
        val complete = sourceMp3.readBytes()

        target.writeBytes(complete.copyOfRange(0, complete.size / 2))
        // Grows faster than the settle interval, so no two samples can ever agree on its size
        val growing = AtomicBoolean(true)
        val writing =
            Thread {
                while (growing.get()) {
                    target.appendBytes(byteArrayOf(0))
                    Thread.sleep(20)
                }
            }.also { it.start() }

        pipeline.enqueue(target.canonical())
        Thread.sleep(1500)
        // Read now, asserted after the writer is stopped, or a failure leaves the thread running
        val analyzedWhileGrowing = titles()
        growing.set(false)
        writing.join()
        target.writeBytes(complete)

        assertEquals(emptyList(), analyzedWhileGrowing, "a file still being written must not be analyzed")
        eventually(describe = { "the settled file, saw ${titles()}" }) { titles() == listOf("A Book") }
    }

    @Test
    fun `a scan whose writes fail does not sweep anything`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        book(root, "An Author", "A Book")
        book(root, "An Author", "B Book")
        val libId = library(root)
        scan(libId)
        assertEquals(2L, tracks(), "sanity: both books import first")

        // Every file now looks changed, so the scan has to re-analyze and write
        transaction { TrackTable.update { it[fileModifiedAt] = Instant.fromEpochMilliseconds(0) } }
        val snapshot = getKoin().get<LibraryRoots>().of(libId)!!

        // A competing connection holding the write lock for the whole scan, which is the real shape of the
        // hazard: busy_timeout and the writer's own retries both run out and the stamps are never written.
        DriverManager.getConnection(sqliteUrl(config.sqliteFile)).use { blocker ->
            blocker.createStatement().use { it.execute("BEGIN EXCLUSIVE") }
            try {
                pipeline.walkLibrary(snapshot)
            } finally {
                blocker.createStatement().use { it.execute("ROLLBACK") }
            }
        }

        assertEquals(2L, tracks(), "a scan that could not write must not conclude the tracks are gone")
    }

    @Test
    fun `a retried write does not lose the batch it had already coalesced`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        repeat(150) { index -> book(root, "An Author", "Book $index") }
        val libId = library(root)
        scan(libId)
        assertEquals(150L, tracks(), "sanity: everything imports first")

        // Bumped here rather than through scanLibrary, so the locked window below contains nothing but the
        // scan's own writes. An unchanged rescan is all batched touches, which is where coalescing happens.
        transaction {
            val current =
                LibraryTable
                    .select(LibraryTable.scanIndex)
                    .where { LibraryTable.id eq libId }
                    .single()[LibraryTable.scanIndex]
            LibraryTable.update({ LibraryTable.id eq libId }) { it[scanIndex] = current + 1uL }
        }
        val snapshot = getKoin().get<LibraryRoots>().of(libId)!!

        // Held from the start so the first batch fails, and released well inside the retry budget so a later
        // attempt succeeds. Whatever the failed attempt had already coalesced has to survive into that retry.
        // The hold has to stay under the total backoff, because these conflicts come back immediately rather
        // than going through busy_timeout - which is the whole reason the writer backs off itself.
        val blocker = DriverManager.getConnection(sqliteUrl(config.sqliteFile))
        blocker.createStatement().use { it.execute("BEGIN EXCLUSIVE") }
        val scanning = Thread { pipeline.walkLibrary(snapshot) }.also { it.start() }
        Thread.sleep(120)
        blocker.createStatement().use { it.execute("ROLLBACK") }
        blocker.close()
        scanning.join(120_000)

        assertEquals(150L, tracks(), "every unchanged file must still be stamped after a retried batch")
    }

    @Test
    fun `a file whose analysis throws does not sweep anything`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        val folder = book(root, "An Author", "A Book")
        book(root, "An Author", "B Book")
        val libId = library(root)
        scan(libId)
        assertEquals(2L, tracks(), "sanity: both books import first")

        // Unreadable file, readable parent: the listing still succeeds, so only the per-file failure path can
        // notice. TagLib opens the file in a constructor outside any handler, so this throws out of the worker.
        val track = folder.resolve("A Book 0.mp3")
        restorePermissions = track
        Files.setPosixFilePermissions(track, emptySet())
        transaction { TrackTable.update { it[fileModifiedAt] = Instant.fromEpochMilliseconds(0) } }
        scan(libId)

        assertEquals(2L, tracks(), "a file that could not be read must not be mistaken for a deleted one")
    }

    @Test
    fun `a library whose scanners cannot analyze anything does not sweep`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        book(root, "An Author", "A Book")
        val libId = library(root)
        scan(libId)
        assertEquals(1L, tracks(), "sanity: the book imports first")

        // A misconfigured library analyzes nothing at all, which must read as "cannot tell", not "all gone"
        transaction {
            LibraryTable.update({ LibraryTable.id eq libId }) {
                it[fileScanners] = listOf(FileScanner("NoSuchScanner"))
            }
        }
        transaction { TrackTable.update { it[fileModifiedAt] = Instant.fromEpochMilliseconds(0) } }
        scan(libId)

        assertEquals(1L, tracks(), "a library that analyzed nothing must not have everything reaped")
    }

    @Test
    fun `stopping mid-scan returns promptly and sweeps nothing`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        repeat(60) { index -> book(root, "An Author", "Book $index", copies = 3) }
        val libId = library(root)
        scan(libId)
        assertEquals(180L, tracks(), "sanity: everything imports first")

        val scanning = Thread { scan(libId) }.also { it.start() }
        while (!pipeline.scanning && scanning.isAlive) Thread.sleep(1)
        val start = System.nanoTime()
        pipeline.stop()
        val took = (System.nanoTime() - start) / 1_000_000

        scanning.join(30_000)
        assertTrue(took < 20_000, "stop took ${took}ms")
        assertEquals(180L, tracks(), "an interrupted scan must lose time, not tracks")
    }

    @Test
    fun `a watch event for a path that cannot be stated does not delete anything`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        val folder = book(root, "An Author", "A Book")
        val libId = library(root)
        scan(libId)
        assertEquals(1L, tracks(), "sanity: the book imports first")

        // Resolved before the chmod: toRealPath needs search permission, and a path that fails to canonicalise
        // matches no library root and would be dropped long before the check under test
        val resolvedTrack = folder.resolve("A Book 0.mp3").canonical()

        // Unreadable, not absent. Files.exists() cannot tell the two apart, and treating this as a deletion
        // is how a chmod or a brief mount error wipes a shelf.
        restorePermissions = folder.parent
        Files.setPosixFilePermissions(folder.parent, emptySet())
        // Only the file: a Folder item for an ancestor would subsume it in the queue
        pipeline.enqueue(resolvedTrack)

        Thread.sleep(2000)
        assertEquals(1L, tracks(), "an unreadable file says nothing about whether its track is still there")
    }

    @Test
    fun `a watch event for a folder that cannot be stated does not delete anything`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        val folder = book(root, "An Author", "A Book")
        val libId = library(root)
        scan(libId)
        assertEquals(1L, tracks(), "sanity: the book imports first")

        val resolvedFolder = folder.canonical()
        restorePermissions = folder.parent
        Files.setPosixFilePermissions(folder.parent, emptySet())
        pipeline.enqueue(resolvedFolder)

        Thread.sleep(2000)
        assertEquals(1L, tracks(), "an unreadable folder must not be treated as a deleted one")
    }

    @Test
    fun `a deleted file seen by the watcher is still removed`() {
        val root = dataDir.resolve("library").also { it.createDirectories() }
        val folder = book(root, "An Author", "A Book")
        val libId = library(root)
        scan(libId)
        assertEquals(1L, tracks(), "sanity: the book imports first")

        // The other side of the previous test: genuinely gone has to keep working
        val track = folder.resolve("A Book 0.mp3")
        val resolved = track.canonical()
        Files.delete(track)
        pipeline.enqueue(resolved)

        eventually(describe = { "the track to be removed, saw ${tracks()}" }) { tracks() == 0L }
    }
}
