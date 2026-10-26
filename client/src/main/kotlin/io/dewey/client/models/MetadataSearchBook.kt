package io.dewey.client.models

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class MetadataSearchBook(
    val authors: List<MetadataSearchAuthor>? = null,
    val coverURL: String? = null,
    val id: MetadataAgentID,
    val language: MetadataLanguage? = null,
    val link: String? = null,
    val narrators: List<String>,
    val releaseDate: LocalDate? = null,
    val series: List<MetadataBookSeries>,
    val title: String? = null,
)
