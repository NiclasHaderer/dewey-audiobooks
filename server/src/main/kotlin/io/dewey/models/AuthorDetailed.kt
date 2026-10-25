package io.dewey.models

import io.dewey.server.database.tables.AuthorField
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
class AuthorDetailed(
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
    val books: List<Book>,
    val series: List<Series>,
    val locked: List<AuthorField>,
) {
    companion object {
        fun fromModel(
            author: Author,
            books: List<Book>,
            series: List<Series>,
            locked: List<AuthorField>,
        ) = AuthorDetailed(
            id = author.id,
            libraryId = author.libraryId,
            name = author.name,
            biography = author.biography,
            imageID = author.imageID,
            website = author.website,
            bornIn = author.bornIn,
            birthDate = author.birthDate,
            deathDate = author.deathDate,
            books = books,
            series = series,
            provider = author.provider,
            providerID = author.providerID,
            locked = locked,
        )
    }
}
