package io.dewey.metadata.responses

import io.dewey.models.ChapterMark
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class MetadataSearchBook(
    val id: MetadataAgentID,
    val title: String?,
    val link: String?,
    val authors: List<MetadataSearchAuthor>?,
    val series: List<MetadataBookSeries>,
    val language: MetadataLanguage?,
    val releaseDate: LocalDate?,
    val coverURL: String?,
    val narrators: List<String>,
)

// The timings belong to the provider's edition of the book. Its runtime is what tells whether they fit the files at
// hand, an abridged or differently mastered edition would put every chapter in the wrong place.
@Serializable
data class MetadataChapters(
    val runtimeMs: Long,
    val chapters: List<ChapterMark>,
)

@Serializable
data class MetadataBook(
    val id: MetadataAgentID,
    val title: String?,
    val link: String?,
    val authors: List<MetadataSearchAuthor>?,
    val series: List<MetadataBookSeries>,
    val releaseDate: LocalDate?,
    val coverURL: String?,
    val description: String?,
    val narrators: List<String>,
    val providerRating: Float?,
    val publisher: String?,
    val language: MetadataLanguage?,
    val isbn: String?,
)
