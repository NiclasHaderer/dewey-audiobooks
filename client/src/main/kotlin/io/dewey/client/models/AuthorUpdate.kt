package io.dewey.client.models

import io.dewey.openapi.common.Patch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AuthorUpdate(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val biography: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val birthDate: Patch<LocalDate?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val books: Patch<List<Uuid>> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val bornIn: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val deathDate: Patch<LocalDate?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val image: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: Patch<String> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val provider: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val providerID: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unlock: Patch<List<AuthorField>> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val website: Patch<String?> = Patch.Absent,
)
