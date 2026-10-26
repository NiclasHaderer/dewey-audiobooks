package io.dewey.client.models

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class MetadataBook(
    val asin: String? = null,
    val authors: List<MetadataSearchAuthor>? = null,
    val coverURL: String? = null,
    val description: String? = null,
    val id: MetadataAgentID,
    val isbn: String? = null,
    val language: MetadataLanguage? = null,
    val link: String? = null,
    val narrators: List<String>,
    val providerRating: Float? = null,
    val publisher: String? = null,
    val releaseDate: LocalDate? = null,
    val series: List<MetadataBookSeries>,
    val title: String? = null,
)
