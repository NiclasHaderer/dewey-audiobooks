package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateLibrary(
    val combineFileScannerFields: Boolean,
    val combineMetadataAgentFields: Boolean,
    val fileScanners: List<FileScanner>,
    val folders: List<String>,
    val icon: String? = null,
    val language: MetadataLanguage,
    val metadataAgents: List<NamedMetadataAgent>,
    val name: String,
    val preferEmbeddedMetadata: Boolean,
    val region: MetadataRegion,
)
