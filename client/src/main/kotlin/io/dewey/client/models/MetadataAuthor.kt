package io.dewey.client.models

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class MetadataAuthor(
    val biography: String? = null,
    val birthDate: LocalDate? = null,
    val bornIn: String? = null,
    val deathDate: LocalDate? = null,
    val id: MetadataAgentID,
    val imageURL: String? = null,
    val link: String,
    val name: String? = null,
    val website: String? = null,
)
