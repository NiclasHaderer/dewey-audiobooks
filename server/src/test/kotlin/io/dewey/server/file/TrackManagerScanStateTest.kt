package io.dewey.server.file

import io.dewey.server.DeweyTest
import io.dewey.server.common.extensions.canonicalString
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.newTrack
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.mp.KoinPlatform.getKoin
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class TrackManagerScanStateTest : DeweyTest() {
    private val trackManager by lazy { getKoin().get<TrackManager>() }

    private fun fileWithMtime(mtimeMillis: Long): Path {
        val file = dataDir.resolve("track-${Uuid.random()}.mp3").createFile()
        Files.setLastModifiedTime(file, FileTime.fromMillis(mtimeMillis))
        return file
    }

    private fun trackAt(
        path: Path,
        trackLibrary: Uuid,
        bookLibrary: Uuid = trackLibrary,
        fileModifiedAt: Instant = Instant.fromEpochMilliseconds(1000),
    ): Uuid {
        val book = newBook("A Book", bookLibrary)
        return newTrack(
            title = "A Track",
            path = path.canonicalString(),
            bookId = book,
            libraryId = trackLibrary,
            fileModifiedAt = fileModifiedAt,
        )
    }

    @Test
    fun `an unchanged file needs no analysis`() {
        val path = fileWithMtime(1000)
        trackAt(path, newLibrary("lib"), fileModifiedAt = Instant.fromEpochMilliseconds(1000))

        assertFalse(trackManager.needsAnalysis(path))
    }

    @Test
    fun `a file with a newer mtime needs analysis`() {
        val path = fileWithMtime(2000)
        trackAt(path, newLibrary("lib"), fileModifiedAt = Instant.fromEpochMilliseconds(1000))

        assertTrue(trackManager.needsAnalysis(path))
    }

    @Test
    fun `an unknown file needs analysis`() {
        assertTrue(trackManager.needsAnalysis(fileWithMtime(1000)))
    }

    @Test
    fun `touch stamps the scan index of the track's own library`() {
        val trackLibrary = newLibrary("tracks", scanIndex = 7uL)
        val bookLibrary = newLibrary("books", scanIndex = 99uL)
        val path = fileWithMtime(1000)
        val track = trackAt(path, trackLibrary, bookLibrary = bookLibrary)

        trackManager.touch(listOf(path), trackLibrary)

        val scanIndex =
            transaction {
                TrackTable
                    .select(TrackTable.scanIndex)
                    .where { TrackTable.id eq track }
                    .single()[TrackTable.scanIndex]
            }
        assertEquals(7uL, scanIndex)
    }
}
