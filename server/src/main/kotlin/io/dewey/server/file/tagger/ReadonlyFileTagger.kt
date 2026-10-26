package io.dewey.server.file.tagger

import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.server.common.extensions.canonicalString
import io.dewey.server.common.extensions.lastModifiedInstant
import io.dewey.server.database.tables.TrackChapter
import io.dewey.taglib.TagLibFile
import kotlinx.datetime.LocalDate
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Instant

class ReadonlyFileTagger(
    filePath: Path,
) {
    constructor(path: String) : this(Path.of(path))

    private val properties: Map<String, List<String>>

    val cover: ByteArray?
    val chapters: List<TrackChapter>
    val durationMs: Long
    val path: String = filePath.canonicalString()
    val lastModified: Instant = filePath.lastModifiedInstant()

    init {
        TagLibFile(filePath).use { file ->
            properties = file.properties()
            cover = file.pictures().firstOrNull()?.data
            chapters = file.chapters().map { TrackChapter(it.title?.ifBlank { null }, it.startMs) }
            durationMs = file.lengthInMilliseconds.toLong()
        }
    }

    val title: String
        get() = first("TITLE") ?: Path.of(path).nameWithoutExtension

    val description: String?
        get() = first("PODCASTDESC") ?: first("COMMENT")

    val date: LocalDate?
        get() = parseDate(first("ORIGINALDATE") ?: first("RELEASEDATE") ?: first("DATE"))

    /** TagLib splits multi-valued fields for us, replacing the NUL separated string jaudiotagger produced. */
    val authors: List<String>?
        get() = properties["ARTIST"]?.filter { it.isNotBlank() }?.ifEmpty { null }

    val book: String?
        get() = first("ALBUM")

    val genres: List<String>
        get() = properties["GENRE"].orEmpty().mapNotNull { it.trim().ifBlank { null } }.distinct()

    val language: MetadataLanguage?
        get() = MetadataLanguage.fromTag(first("LANGUAGE"))

    val trackNr: Int?
        get() = first("TRACKNUMBER")?.substringBefore('/')?.trim()?.toIntOrNull()

    val narrators: List<String>
        get() =
            properties["COMPOSER"]
                .orEmpty()
                .flatMap { it.split(",") }
                .mapNotNull { it.trim().ifBlank { null } }
                .distinct()

    val series: String?
        get() = first("WORK") ?: first("GROUPING") ?: first("SERIES")

    val seriesIndex: Float?
        get() = (first("CATALOGNUMBER") ?: first("PART"))?.toFloatOrNull()

    val asin: String?
        get() = first("ASIN") ?: first("AUDIBLE_ASIN")

    private fun first(key: String): String? = properties[key]?.firstOrNull()?.ifBlank { null }

    private companion object {
        /** Tags carry either a full date or a bare year, depending on the format and tagger. */
        fun parseDate(value: String?): LocalDate? {
            val date = value?.trim()?.ifEmpty { null } ?: return null
            runCatching { return LocalDate.parse(date.take(10)) }
            return date.take(4).toIntOrNull()?.let { LocalDate(it, 1, 1) }
        }
    }
}
