package io.dewey.server.database.tables

import io.dewey.models.ChangedEntity
import io.dewey.server.database.extensions.json
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.date

enum class AuthorField : MetadataField {
    NAME,
    PROVIDER,
    PROVIDER_ID,
    BIOGRAPHY,
    IMAGE_ID,
    WEBSITE,
    BORN_IN,
    BIRTH_DATE,
    DEATH_DATE,
}

object AuthorTable : LibraryEntityTable("author", AuthorField.NAME.column, ChangedEntity.AUTHOR) {
    val biography = text(AuthorField.BIOGRAPHY.column).nullable()
    val website = varchar(AuthorField.WEBSITE.column, 255).nullable()
    val birthDate = date(AuthorField.BIRTH_DATE.column).nullable()
    val bornIn = varchar(AuthorField.BORN_IN.column, 255).nullable()
    val deathDate = date(AuthorField.DEATH_DATE.column).nullable()
    val provider = varchar(AuthorField.PROVIDER.column, 255).nullable()
    val providerId = varchar(AuthorField.PROVIDER_ID.column, 255).nullable()
    val imageId = reference(AuthorField.IMAGE_ID.column, ImageTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val locked = json<Set<AuthorField>>("locked").default(emptySet())
}
