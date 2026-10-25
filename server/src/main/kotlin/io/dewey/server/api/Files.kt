package io.dewey.server.api

import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.responses.BinaryResponse
import io.dewey.openapi.ktor.responses.FileResponse
import io.dewey.openapi.ktor.responses.binaryResponse
import io.dewey.openapi.ktor.responses.fileResponse
import io.dewey.server.common.audioContentType
import io.dewey.server.common.imageContentType
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.ImageTable
import io.dewey.server.database.tables.LibraryEntityTable
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.database.tables.TrackTable
import io.dewey.server.plugins.auth.assertLibraryPermissions
import io.dewey.server.plugins.auth.deweyPrincipal
import io.dewey.server.plugins.sandbox
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.routing.Routing
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.unionAll
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.uuid.Uuid

fun Routing.audioRouting() {
    get<Api.Files.Audio.Id, FileResponse> { (id) ->
        val (track, libraryId) =
            transaction {
                val row =
                    TrackTable
                        .select(TrackTable.path, TrackTable.library)
                        .where { TrackTable.id eq id }
                        .firstOrNull()
                        ?: throw ErrorResponse.notFound("Track", id)
                row[TrackTable.path] to row[TrackTable.library].value
            }
        assertLibraryPermissions(libraryId)
        val path = Path.of(track)
        if (!path.exists() || !path.isRegularFile()) {
            throw ErrorResponse.notFound(
                "File",
                path.name,
                JsonPrimitive("Database out of sync. Please start a rescan."),
            )
        }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, path.name).toString(),
        )
        call.sandbox()
        fileResponse(path, audioContentType(path))
    }
}

fun Routing.imageRouting() {
    get<Api.Files.Images.Id, BinaryResponse> { (id) ->
        val permissions = deweyPrincipal().permissions
        call.sandbox()
        transaction {
            val allowed = permissions.libraries.mapTo(mutableSetOf()) { it.id }
            val covers =
                listOf(
                    cover(BookTable, BookTable.coverId, id, allowed),
                    cover(SeriesTable, SeriesTable.coverId, id, allowed),
                    cover(AuthorTable, AuthorTable.imageId, id, allowed),
                )
            val image =
                covers
                    .reduce { acc: AbstractQuery<*>, query -> acc.unionAll(query) }
                    .limit(1)
                    .firstOrNull() ?: throw ErrorResponse.notFound("Image", id)
            val bytes = image[ImageTable.image].bytes
            binaryResponse(
                bytes,
                contentType = imageContentType(bytes) ?: ContentType.Application.OctetStream,
                // A new row is created whenever the bytes change, so an id always maps to the same image
                cacheControl = "private, max-age=$IMAGE_MAX_AGE_SECONDS, immutable",
                etag = id.toString(),
            )
        }
    }
}

private const val IMAGE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60

private fun cover(
    owner: LibraryEntityTable,
    image: Column<EntityID<Uuid>?>,
    imageId: Uuid,
    allowed: Set<Uuid>,
): Query =
    owner
        .join(ImageTable, JoinType.INNER, image, ImageTable.id)
        .select(ImageTable.image)
        .where { (image eq imageId) and (owner.library inList allowed) }
