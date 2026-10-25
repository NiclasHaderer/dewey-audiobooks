package io.dewey.server.api

import io.dewey.metadata.MetadataAgents
import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import io.dewey.models.FileScanner
import io.dewey.models.NamedMetadataAgent
import io.dewey.openapi.common.Patch
import io.dewey.openapi.common.ifSet
import io.dewey.openapi.common.orElse
import io.dewey.openapi.ktor.ValidateObject
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.openapi.serializion.kotlin.OpenApiJson
import io.dewey.server.file.analyzer.AudioFileAnalyzers
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import org.koin.ktor.ext.get

@Serializable
data class UpdateLibrary(
    val name: String,
    val icon: String?,
    val folders: List<String>,
    val preferEmbeddedMetadata: Boolean,
    val metadataAgents: List<NamedMetadataAgent>,
    val combineMetadataAgentFields: Boolean,
    val fileScanners: List<FileScanner>,
    val combineFileScannerFields: Boolean,
    var language: MetadataLanguage,
    var region: MetadataRegion,
) : ValidateObject {
    override suspend fun RoutingContext.validateBody() {
        requireNotEmpty(folders, "folder")
        requireNotEmpty(fileScanners, "file scanner")
        requireRegistered(metadataAgents, fileScanners)
    }
}

@Serializable
data class PartialUpdateLibrary(
    val name: Patch<String> = Patch.Absent,
    val icon: Patch<String?> = Patch.Absent,
    val folders: Patch<List<String>> = Patch.Absent,
    val preferEmbeddedMetadata: Patch<Boolean> = Patch.Absent,
    val metadataAgents: Patch<List<NamedMetadataAgent>> = Patch.Absent,
    val combineMetadataAgentFields: Patch<Boolean> = Patch.Absent,
    val fileScanners: Patch<List<FileScanner>> = Patch.Absent,
    val combineFileScannerFields: Patch<Boolean> = Patch.Absent,
    val language: Patch<MetadataLanguage> = Patch.Absent,
    val region: Patch<MetadataRegion> = Patch.Absent,
) : ValidateObject {
    override suspend fun RoutingContext.validateBody() {
        folders.ifSet { requireNotEmpty(it, "folder") }
        fileScanners.ifSet { requireNotEmpty(it, "file scanner") }
        requireRegistered(metadataAgents.orElse(emptyList()), fileScanners.orElse(emptyList()))
    }
}

private fun requireNotEmpty(
    values: List<*>,
    what: String,
) {
    if (values.isEmpty()) throw ErrorResponse.userError("Library must have at least one $what")
}

private fun RoutingContext.requireRegistered(
    metadataAgents: List<NamedMetadataAgent>,
    fileScanners: List<FileScanner>,
) {
    requireKnownNames(
        kind = "metadata agent",
        requested = metadataAgents.map { it.name },
        available = call.application.get<MetadataAgents>().map { it.name },
    )
    requireKnownNames(
        kind = "file scanner",
        requested = fileScanners.map { it.name },
        available = call.application.get<AudioFileAnalyzers>().map { it.name },
    )
}

private fun requireKnownNames(
    kind: String,
    requested: List<String>,
    available: List<String>,
) {
    val unknown = requested.filterNot { it in available }.distinct()
    if (unknown.isNotEmpty()) {
        throw ErrorResponse.userError(
            "Unknown $kind: ${unknown.joinToString()}",
            OpenApiJson.encodeToJsonElement(mapOf("unknown" to unknown, "available" to available)),
        )
    }
}
