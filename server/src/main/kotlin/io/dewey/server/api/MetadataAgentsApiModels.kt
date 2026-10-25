package io.dewey.server.api

import io.dewey.metadata.responses.MetadataRegion
import kotlinx.serialization.Serializable

@Serializable
data class MetadataAgentApiModel(
    val name: String,
    val supportedRegions: List<MetadataRegion>,
)
