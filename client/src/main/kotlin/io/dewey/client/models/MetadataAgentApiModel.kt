package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class MetadataAgentApiModel(
    val name: String,
    val supportedRegions: List<MetadataRegion>,
)
