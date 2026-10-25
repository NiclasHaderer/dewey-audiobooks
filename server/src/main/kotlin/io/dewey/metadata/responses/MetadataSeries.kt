package io.dewey.metadata.responses

import kotlinx.serialization.Serializable

@Serializable
data class MetadataBookSeries(
    val id: MetadataAgentID,
    val title: String?,
    val link: String,
    val index: Float?,
)

@Serializable
data class MetadataSeries(
    val id: MetadataAgentID,
    val title: String?,
    val authors: List<String>?,
    val link: String,
    val coverURL: String?,
    val description: String?,
    val totalBooks: Int?,
    val primaryWorks: Int?,
    val books: List<MetadataSearchBook>?,
)
