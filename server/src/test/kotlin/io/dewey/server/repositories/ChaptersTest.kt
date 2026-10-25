package io.dewey.server.repositories

import io.dewey.models.ChapterMark
import io.dewey.server.database.tables.TrackChapter
import io.dewey.server.database.tables.TrackRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ChaptersTest {
    private fun track(
        title: String,
        durationMs: Long,
        vararg chapters: TrackChapter,
    ) = TrackRow(
        id = Uuid.random(),
        title = title,
        durationMs = durationMs,
        fileModifiedAt = Instant.fromEpochMilliseconds(0),
        path = "/$title.mp3",
        book = Uuid.random(),
        library = Uuid.random(),
        scanIndex = 0uL,
        trackNr = null,
        chapters = chapters.toList(),
    )

    @Test
    fun `files without markers become one chapter per track`() {
        val tracks = listOf(track("Part 1", 1000), track("Part 2", 2000))

        val chapters = buildChapters(fileChapterMarks(tracks), tracks)

        assertEquals(listOf("Part 1", "Part 2"), chapters.map { it.title })
        assertEquals(listOf(0L to 1000L, 1000L to 3000L), chapters.map { it.startMs to it.endMs })
        assertEquals(tracks.map { it.id }, chapters.map { it.trackId })
    }

    @Test
    fun `markers without end times end where the next one starts`() {
        val m4b = track("Book", 6000, TrackChapter("One", 0), TrackChapter("Two", 2000), TrackChapter("Three", 4000))

        val chapters = buildChapters(fileChapterMarks(listOf(m4b)), listOf(m4b))

        assertEquals(listOf(0L to 2000L, 2000L to 4000L, 4000L to 6000L), chapters.map { it.startMs to it.endMs })
        assertEquals(listOf("One", "Two", "Three"), chapters.map { it.title })
    }

    @Test
    fun `markers of later files are shifted by the files before them`() {
        val first = track("Disc 1", 3000, TrackChapter("One", 0), TrackChapter("Two", 1500))
        val second = track("Disc 2", 3000, TrackChapter("Three", 0))

        val chapters = buildChapters(fileChapterMarks(listOf(first, second)), listOf(first, second))

        assertEquals(listOf(0L, 1500L, 3000L), chapters.map { it.startMs })
        assertEquals(listOf(first.id, first.id, second.id), chapters.map { it.trackId })
    }

    @Test
    fun `one file without markers falls back to one chapter per track`() {
        val tracks =
            listOf(track("Disc 1", 3000, TrackChapter("One", 0), TrackChapter("Two", 1500)), track("Disc 2", 3000))

        assertEquals(listOf("Disc 1", "Disc 2"), fileChapterMarks(tracks).map { it.title })
    }

    @Test
    fun `untitled markers have no title`() {
        val m4b = track("Book", 4000, TrackChapter(null, 0), TrackChapter(null, 2000))

        val chapters = buildChapters(fileChapterMarks(listOf(m4b)), listOf(m4b))

        assertEquals(listOf(null, null), chapters.map { it.title })
    }

    @Test
    fun `an edited mark points at the track it starts in`() {
        val tracks = listOf(track("Part 1", 1000), track("Part 2", 2000))

        val chapters = buildChapters(listOf(ChapterMark("Intro", 0), ChapterMark("Main", 1500)), tracks)

        assertEquals(listOf(tracks[0].id, tracks[1].id), chapters.map { it.trackId })
        assertEquals(listOf(1500L, 3000L), chapters.map { it.endMs })
    }

    @Test
    fun `marks past the end of the book are dropped`() {
        val tracks = listOf(track("Part 1", 1000))

        val chapters = buildChapters(listOf(ChapterMark("Intro", 0), ChapterMark("Gone", 1000)), tracks)

        assertEquals(listOf("Intro" to 1000L), chapters.map { it.title to it.endMs })
    }
}
