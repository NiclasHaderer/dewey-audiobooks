package io.dewey.server.file.analyzer.impl

import io.dewey.server.common.extensions.countParents
import io.dewey.server.common.extensions.grandGrandParentName
import io.dewey.server.common.extensions.grandParentName
import io.dewey.server.common.extensions.parentName
import io.dewey.server.common.extensions.relativeToBase
import io.dewey.server.common.extensions.replaceAll
import io.dewey.server.file.analyzer.AudioFileAnalysisResult
import io.dewey.server.file.analyzer.AudioFileAnalysisResultImpl
import io.dewey.server.file.analyzer.AudioFileAnalyzer
import io.dewey.server.file.tagger.ReadonlyFileTagger
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

class AudioFolderScanner : AudioFileAnalyzer {
    override val name = "AudioFolderScanner"
    private val bookPrefixes = listOf("^((Book|Volume|Vol) ?)?\\d\\d? ?[.\\-: ]+ ?".toRegex(), "^\\d\\d? - ".toRegex())

    override fun analyze(
        filePath: Path,
        attrs: BasicFileAttributes,
        tags: ReadonlyFileTagger,
        libraryPath: Path,
    ): AudioFileAnalysisResult? {
        val cleanPath = filePath.relativeToBase(libraryPath) ?: return null
        val parentCount = cleanPath.countParents()
        if (parentCount != 2 && parentCount != 3) return null

        return this.getInformation(cleanPath, parentCount, tags)
    }

    private fun getInformation(
        path: Path,
        parentCount: Int,
        tags: ReadonlyFileTagger,
    ): AudioFileAnalysisResult {
        val book = path.parentName().replaceAll(bookPrefixes, "")
        val author = if (parentCount == 2) path.grandParentName() else path.grandGrandParentName()
        val series = if (parentCount == 2) null else path.grandParentName()
        return AudioFileAnalysisResultImpl(
            // Uses the filename as fallback
            title = tags.title,
            authors = tags.authors ?: listOf(author),
            book = tags.book ?: book,
            series = tags.series ?: series,
            description = tags.description,
            date = tags.date,
            language = tags.language,
            trackNr = tags.trackNr,
            narrators = tags.narrators,
            seriesIndex = tags.seriesIndex,
            asin = tags.asin,
            genres = tags.genres,
            cover = tags.cover,
            chapters = tags.chapters,
            durationMs = tags.durationMs,
            path = tags.path,
            lastModified = tags.lastModified,
        )
    }
}
