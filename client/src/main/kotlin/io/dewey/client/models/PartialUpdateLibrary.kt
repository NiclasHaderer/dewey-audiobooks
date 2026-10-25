package io.dewey.client.models

import io.dewey.openapi.common.Patch
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PartialUpdateLibrary(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val combineFileScannerFields: Patch<Boolean> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val combineMetadataAgentFields: Patch<Boolean> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fileScanners: Patch<List<FileScanner>> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val folders: Patch<List<String>> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val icon: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val language: Patch<MetadataLanguage> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val metadataAgents: Patch<List<NamedMetadataAgent>> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: Patch<String> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val preferEmbeddedMetadata: Patch<Boolean> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val region: Patch<MetadataRegion> = Patch.Absent,
)
