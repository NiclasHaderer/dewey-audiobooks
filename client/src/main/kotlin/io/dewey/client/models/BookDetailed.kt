package io.dewey.client.models

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class BookDetailed(
    val asin: String? = null,
    val authors: List<NamedId>,
    val chapters: List<Chapter>,
    val coverID: Uuid? = null,
    val description: String? = null,
    val durationMs: Long,
    val genres: List<String>,
    val id: Uuid,
    val isbn: String? = null,
    val language: MetadataLanguage? = null,
    val libraryId: Uuid,
    val locked: List<BookField>,
    val narrators: List<String>,
    val positionMs: Long,
    val provider: String? = null,
    val providerID: String? = null,
    val providerRating: Float? = null,
    val publisher: String? = null,
    val releaseDate: LocalDate? = null,
    val series: List<TitledId>,
    val status: PlayStatus,
    val title: String,
    val tracks: List<Track>,
)
