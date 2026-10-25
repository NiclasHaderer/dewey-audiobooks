package io.dewey.client.models

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class AuthorDetailed(
    val biography: String? = null,
    val birthDate: LocalDate? = null,
    val books: List<Book>,
    val bornIn: String? = null,
    val deathDate: LocalDate? = null,
    val id: Uuid,
    val imageID: Uuid? = null,
    val libraryId: Uuid,
    val locked: List<AuthorField>,
    val name: String,
    val provider: String? = null,
    val providerID: String? = null,
    val series: List<Series>,
    val website: String? = null,
)
