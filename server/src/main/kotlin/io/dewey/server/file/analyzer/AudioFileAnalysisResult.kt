package io.dewey.server.file.analyzer

import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.server.database.tables.TrackChapter
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

class AudioFileAnalysisResultImpl(
    override val title: String,
    override val authors: List<String>,
    override val book: String,
    override val durationMs: Long,
    override val path: String,
    override val lastModified: Instant,
    override val description: String? = null,
    override val date: LocalDate? = null,
    override val language: MetadataLanguage? = null,
    override val trackNr: Int? = null,
    override val narrators: List<String> = emptyList(),
    override val series: String? = null,
    override val seriesIndex: Float? = null,
    override val genres: List<String> = emptyList(),
    override val cover: ByteArray? = null,
    override val chapters: List<TrackChapter> = emptyList(),
) : AudioFileAnalysisResult

interface AudioFileAnalysisResult {
    val title: String
    val authors: List<String>
    val book: String
    val description: String?
    val date: LocalDate?
    val language: MetadataLanguage?
    val trackNr: Int?
    val narrators: List<String>
    val series: String?
    val seriesIndex: Float?
    val genres: List<String>
    val cover: ByteArray?
    val chapters: List<TrackChapter>
    val durationMs: Long
    val path: String
    val lastModified: Instant
}
