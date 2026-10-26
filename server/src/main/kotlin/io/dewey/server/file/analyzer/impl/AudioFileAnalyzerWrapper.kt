package io.dewey.server.file.analyzer.impl

import io.dewey.server.file.analyzer.AudioFileAnalysisResult
import io.dewey.server.file.analyzer.AudioFileAnalysisResultImpl
import io.dewey.server.file.analyzer.AudioFileAnalyzer
import io.dewey.server.file.tagger.ReadonlyFileTagger
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.absolute

class AudioFileAnalyzerWrapper(
    private val analyzers: List<AudioFileAnalyzer>,
    private val combineFields: Boolean = false,
) {
    private val log = logger {}

    fun analyze(
        filePath: Path,
        attrs: BasicFileAttributes,
        libraryPath: Path,
    ): AudioFileAnalysisResult? {
        val tags = ReadonlyFileTagger(filePath)
        var merged: AudioFileAnalysisResult? = null
        for (analyzer in analyzers) {
            val result =
                try {
                    analyzer.analyze(filePath, attrs, tags, libraryPath)
                } catch (e: Exception) {
                    log.error(e) { "Could not analyze file ${filePath.absolute()}" }
                    null
                } ?: continue

            if (!combineFields) return result
            merged = merged?.fillFrom(result) ?: result
        }
        return merged
    }
}

private fun AudioFileAnalysisResult.fillFrom(other: AudioFileAnalysisResult): AudioFileAnalysisResult =
    AudioFileAnalysisResultImpl(
        title = title.ifBlank { other.title },
        authors = authors.ifEmpty { other.authors },
        book = book.ifBlank { other.book },
        durationMs = durationMs,
        path = path,
        lastModified = lastModified,
        description = description ?: other.description,
        date = date ?: other.date,
        language = language ?: other.language,
        trackNr = trackNr ?: other.trackNr,
        narrators = narrators.ifEmpty { other.narrators },
        series = series ?: other.series,
        seriesIndex = seriesIndex ?: other.seriesIndex,
        asin = asin ?: other.asin,
        genres = genres.ifEmpty { other.genres },
        cover = cover ?: other.cover,
        chapters = chapters,
    )
