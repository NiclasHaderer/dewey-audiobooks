package io.dewey.server.file.analyzer

import io.dewey.server.file.tagger.ReadonlyFileTagger
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.isDirectory
import kotlin.io.path.readAttributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class AudioFileAnalyzersTest {
    private val testResources: Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("test-resources") }
            .first { it.isDirectory() }

    private val file: Path =
        testResources
            .resolve("Terry Pratchett")
            .resolve("Good Omens")
            .resolve("Good Omens.mp3")

    private val attrs = file.readAttributes<BasicFileAttributes>()

    private class StubAnalyzer(
        override val name: String,
        private val result: AudioFileAnalysisResult?,
    ) : AudioFileAnalyzer {
        var calls = 0
            private set

        override fun analyze(
            filePath: Path,
            attrs: BasicFileAttributes,
            tags: ReadonlyFileTagger,
            libraryPath: Path,
        ): AudioFileAnalysisResult? {
            calls++
            return result
        }
    }

    private fun result(
        book: String,
        series: String? = null,
        narrators: List<String> = emptyList(),
        description: String? = null,
    ) = AudioFileAnalysisResultImpl(
        title = "title",
        authors = listOf("author"),
        book = book,
        durationMs = 1,
        path = "/track.mp3",
        lastModified = Instant.fromEpochMilliseconds(0),
        series = series,
        narrators = narrators,
        description = description,
    )

    private fun analyzers(vararg items: AudioFileAnalyzer) = AudioFileAnalyzers(items.toList())

    @Test
    fun `the requested order decides which analyzer wins`() {
        val tags = StubAnalyzer("tags", result("from tags"))
        val folders = StubAnalyzer("folders", result("from folders"))
        val registered = analyzers(tags, folders)

        assertEquals(
            "from folders",
            registered.forNames(listOf("folders", "tags")).analyze(file, attrs, testResources)?.book,
        )
        assertEquals(
            "from tags",
            registered.forNames(listOf("tags", "folders")).analyze(file, attrs, testResources)?.book,
        )
    }

    @Test
    fun `an unknown name is dropped`() {
        val tags = StubAnalyzer("tags", result("from tags"))

        val analyzed = analyzers(tags).forNames(listOf("nope", "tags")).analyze(file, attrs, testResources)

        assertEquals("from tags", analyzed?.book)
    }

    @Test
    fun `without combining the first result is used whole`() {
        val tags = StubAnalyzer("tags", result("from tags"))
        val folders = StubAnalyzer("folders", result("from folders", series = "Discworld"))

        val analyzed =
            analyzers(tags, folders).forNames(listOf("tags", "folders")).analyze(file, attrs, testResources)

        assertNull(analyzed?.series)
        assertEquals(0, folders.calls, "the lower priority analyzer must not even run")
    }

    @Test
    fun `combining fills the empty fields and leaves the others alone`() {
        val tags = StubAnalyzer("tags", result("from tags", description = "from tags"))
        val folders =
            StubAnalyzer(
                "folders",
                result("from folders", series = "Discworld", narrators = listOf("Stephen Briggs"), description = "no"),
            )

        val analyzed =
            analyzers(tags, folders)
                .forNames(listOf("tags", "folders"), combineFields = true)
                .analyze(file, attrs, testResources)

        assertEquals("from tags", analyzed?.book, "the winner keeps the fields it filled")
        assertEquals("from tags", analyzed?.description)
        assertEquals("Discworld", analyzed?.series, "an empty field is taken from the next analyzer")
        assertEquals(listOf("Stephen Briggs"), analyzed?.narrators)
    }

    @Test
    fun `combining walks past an analyzer without a result`() {
        val tags = StubAnalyzer("tags", null)
        val folders = StubAnalyzer("folders", result("from folders", series = "Discworld"))

        val analyzed =
            analyzers(tags, folders)
                .forNames(listOf("tags", "folders"), combineFields = true)
                .analyze(file, attrs, testResources)

        assertEquals("from folders", analyzed?.book)
        assertEquals("Discworld", analyzed?.series)
    }

    @Test
    fun `nothing to analyze with has no result`() {
        val analyzed = analyzers().forNames(listOf("tags")).analyze(file, attrs, testResources)

        assertNull(analyzed)
    }
}
