package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class MetadataBookSeries(
    val id: MetadataAgentID,
    val index: Float? = null,
    val link: String,
    val title: String? = null,
)
