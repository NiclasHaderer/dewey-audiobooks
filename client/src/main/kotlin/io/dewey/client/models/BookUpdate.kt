package io.dewey.client.models

import io.dewey.openapi.common.Patch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BookUpdate(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val authors: Patch<List<Uuid>> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val chapters: Patch<List<ChapterMark>> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val cover: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val description: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val genres: Patch<List<String>?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val isbn: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val language: Patch<MetadataLanguage?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val narrators: Patch<List<String>?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val provider: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val providerID: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val providerRating: Patch<Float?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val publisher: Patch<String?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val releaseDate: Patch<LocalDate?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val series: Patch<List<Uuid>?> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val title: Patch<String> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unlock: Patch<List<BookField>> = Patch.Absent,
)
