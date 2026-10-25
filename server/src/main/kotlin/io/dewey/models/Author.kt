package io.dewey.models

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
open class Author(
    val id: Uuid,
    val libraryId: Uuid,
    val name: String,
    val provider: String?,
    val providerID: String?,
    val biography: String?,
    val imageID: Uuid?,
    val website: String?,
    val bornIn: String?,
    val birthDate: LocalDate?,
    val deathDate: LocalDate?,
)
