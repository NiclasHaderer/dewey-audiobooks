package io.dewey.models

import io.dewey.metadata.responses.MetadataLanguage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
open class Book(
    val id: Uuid,
    val libraryId: Uuid,
    val authors: List<NamedId>,
    val series: List<TitledId>,
    val title: String,
    val provider: String?,
    val providerID: String?,
    val providerRating: Float?,
    val releaseDate: LocalDate?,
    val publisher: String?,
    val language: MetadataLanguage?,
    val description: String?,
    val narrators: List<String>,
    val isbn: String?,
    val coverID: Uuid?,
    val genres: List<String>,
    val durationMs: Long,
    val positionMs: Long,
    val status: PlayStatus,
)
