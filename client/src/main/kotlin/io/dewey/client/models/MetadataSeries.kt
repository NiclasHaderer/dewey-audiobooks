package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class MetadataSeries(
    val authors: List<String>? = null,
    val books: List<MetadataSearchBook>? = null,
    val coverURL: String? = null,
    val description: String? = null,
    val id: MetadataAgentID,
    val link: String,
    val primaryWorks: Int? = null,
    val title: String? = null,
    val totalBooks: Int? = null,
)
