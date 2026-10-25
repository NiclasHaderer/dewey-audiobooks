package io.dewey.client.models

import io.dewey.openapi.common.Patch
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeriesUpdate(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val books: Patch<List<Uuid>> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val cover: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val description: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val primaryWorks: Patch<Int?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val provider: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val providerID: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val title: Patch<String> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val totalBooks: Patch<Int?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unlock: Patch<List<SeriesField>> = Patch.Absent,
)
