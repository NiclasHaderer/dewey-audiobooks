package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class MetadataAgentID(
    val itemID: String,
    val provider: String,
)
