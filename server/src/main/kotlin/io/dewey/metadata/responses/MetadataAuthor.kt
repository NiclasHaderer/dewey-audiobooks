package io.dewey.metadata.responses

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class MetadataSearchAuthor(
    val id: MetadataAgentID,
    val name: String?,
    val link: String,
)

@Serializable
data class MetadataAuthor(
    val id: MetadataAgentID,
    val name: String?,
    val link: String,
    val imageURL: String?,
    val biography: String?,
    val website: String?,
    val bornIn: String?,
    val birthDate: LocalDate?,
    val deathDate: LocalDate?,
)
