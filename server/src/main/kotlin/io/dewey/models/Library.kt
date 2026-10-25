package io.dewey.models

import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Library(
    val id: Uuid,
    val name: String,
    val icon: String?,
    val preferEmbeddedMetadata: Boolean,
    val folders: List<String>,
    val metadataAgents: List<NamedMetadataAgent>,
    val combineMetadataAgentFields: Boolean,
    val fileScanners: List<FileScanner>,
    val combineFileScannerFields: Boolean,
    val language: MetadataLanguage,
    val region: MetadataRegion,
    val bookCount: Long,
)
