package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class MetadataSearchAuthor(
    val id: MetadataAgentID,
    val link: String,
    val name: String? = null,
)
