package io.dewey.metadata.responses

import kotlinx.serialization.Serializable

@Serializable
data class MetadataAgentID(
    val provider: String,
    val itemID: String,
)
