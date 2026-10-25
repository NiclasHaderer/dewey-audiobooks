package io.dewey.models

import io.dewey.openapi.common.Patch
import io.dewey.openapi.common.ifSet
import io.dewey.openapi.ktor.ValidateObject
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.database.tables.AuthorField
import io.ktor.server.routing.RoutingContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class AuthorUpdate(
    val name: Patch<String> = Patch.Absent,
    val provider: Patch<String?> = Patch.Absent,
    val providerID: Patch<String?> = Patch.Absent,
    val biography: Patch<String?> = Patch.Absent,
    val image: Patch<String?> = Patch.Absent,
    val website: Patch<String?> = Patch.Absent,
    val bornIn: Patch<String?> = Patch.Absent,
    val birthDate: Patch<LocalDate?> = Patch.Absent,
    val deathDate: Patch<LocalDate?> = Patch.Absent,
    val books: Patch<List<Uuid>> = Patch.Absent,
    val unlock: Patch<List<AuthorField>> = Patch.Absent,
) : ValidateObject {
    override suspend fun RoutingContext.validateBody() {
        name.ifSet { if (it.isBlank()) throw ErrorResponse.userError("An author name cannot be empty") }
        books.ifSet { if (it.isEmpty()) throw ErrorResponse.userError("An author must have at least one book") }
    }
}
