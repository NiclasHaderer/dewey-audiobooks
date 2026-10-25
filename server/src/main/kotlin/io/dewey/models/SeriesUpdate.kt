package io.dewey.models

import io.dewey.openapi.common.Patch
import io.dewey.openapi.common.ifSet
import io.dewey.openapi.ktor.ValidateObject
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.database.tables.SeriesField
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SeriesUpdate(
    val title: Patch<String> = Patch.Absent,
    val books: Patch<List<Uuid>> = Patch.Absent,
    val provider: Patch<String?> = Patch.Absent,
    val providerID: Patch<String?> = Patch.Absent,
    val totalBooks: Patch<Int?> = Patch.Absent,
    val primaryWorks: Patch<Int?> = Patch.Absent,
    val cover: Patch<String?> = Patch.Absent,
    val description: Patch<String?> = Patch.Absent,
    val unlock: Patch<List<SeriesField>> = Patch.Absent,
) : ValidateObject {
    override suspend fun RoutingContext.validateBody() {
        title.ifSet { if (it.isBlank()) throw ErrorResponse.userError("A series title cannot be empty") }
        books.ifSet { if (it.isEmpty()) throw ErrorResponse.userError("A series must have at least one book") }
    }
}
