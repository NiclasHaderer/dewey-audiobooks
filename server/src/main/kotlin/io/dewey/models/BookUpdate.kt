package io.dewey.models

import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.openapi.common.Patch
import io.dewey.openapi.common.ifSet
import io.dewey.openapi.ktor.ValidateObject
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.database.tables.BookField
import io.ktor.server.routing.RoutingContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Op
import kotlin.uuid.Uuid

@Serializable
data class BookUpdate(
    val title: Patch<String> = Patch.Absent,
    val authors: Patch<List<Uuid>> = Patch.Absent,
    val series: Patch<List<Uuid>?> = Patch.Absent,
    val provider: Patch<String?> = Patch.Absent,
    val providerID: Patch<String?> = Patch.Absent,
    val providerRating: Patch<Float?> = Patch.Absent,
    val releaseDate: Patch<LocalDate?> = Patch.Absent,
    val publisher: Patch<String?> = Patch.Absent,
    val language: Patch<MetadataLanguage?> = Patch.Absent,
    val description: Patch<String?> = Patch.Absent,
    val narrators: Patch<List<String>?> = Patch.Absent,
    val genres: Patch<List<String>?> = Patch.Absent,
    val isbn: Patch<String?> = Patch.Absent,
    val asin: Patch<String?> = Patch.Absent,
    val cover: Patch<String?> = Patch.Absent,
    val chapters: Patch<List<ChapterMark>> = Patch.Absent,
    val unlock: Patch<List<BookField>> = Patch.Absent,
) : ValidateObject {
    override suspend fun RoutingContext.validateBody() {
        title.ifSet { if (it.isBlank()) throw ErrorResponse.userError("A book title cannot be empty") }
        authors.ifSet { if (it.isEmpty()) throw ErrorResponse.userError("A book must have at least one author") }
        chapters.ifSet { marks ->
            if (marks.isEmpty()) {
                throw ErrorResponse.userError(
                    "A book needs at least one chapter, unlock CHAPTERS to use the files' chapters",
                )
            }
            if (marks.first().startMs < 0) throw ErrorResponse.userError("A chapter cannot start before the book")
            if (marks.zipWithNext().any { (a, b) -> b.startMs <= a.startMs }) {
                throw ErrorResponse.userError(
                    "Chapters must be sorted by start time, without two starting at the same time",
                )
            }
        }
    }
}
