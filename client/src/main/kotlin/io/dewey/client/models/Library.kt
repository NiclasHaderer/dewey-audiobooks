package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Library(
    val bookCount: Long,
    val combineFileScannerFields: Boolean,
    val combineMetadataAgentFields: Boolean,
    val fileScanners: List<FileScanner>,
    val folders: List<String>,
    val icon: String? = null,
    val id: Uuid,
    val language: MetadataLanguage,
    val metadataAgents: List<NamedMetadataAgent>,
    val name: String,
    val preferEmbeddedMetadata: Boolean,
    val region: MetadataRegion,
)
