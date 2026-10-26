package io.dewey.server.file.analyzer.impl

import io.dewey.server.file.analyzer.AudioFileAnalysisResult
import io.dewey.server.file.analyzer.AudioFileAnalysisResultImpl
import io.dewey.server.file.analyzer.AudioFileAnalyzer
import io.dewey.server.file.tagger.ReadonlyFileTagger
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

class AudioTagScanner : AudioFileAnalyzer {
    override val name: String = "AudioTagScanner"

    override fun analyze(
        filePath: Path,
        attrs: BasicFileAttributes,
        tags: ReadonlyFileTagger,
        libraryPath: Path,
    ): AudioFileAnalysisResult? {
        if (tags.authors == null || tags.book == null) return null
        return AudioFileAnalysisResultImpl(
            title = tags.title,
            authors = tags.authors!!,
            book = tags.book!!,
            description = tags.description,
            date = tags.date,
            language = tags.language,
            trackNr = tags.trackNr,
            narrators = tags.narrators,
            series = tags.series,
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
