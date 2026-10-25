package io.dewey.server.api

import io.dewey.client.models.LibraryPermissionLevel
import io.dewey.server.DeweyTest
import io.dewey.server.api
import io.dewey.server.bearer
import io.dewey.server.database.access.getOrCreateImage
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.ImageTable
import io.dewey.server.deweyServer
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.pngBytes
import io.dewey.server.registerWithAccess
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ImageCachingTest : DeweyTest() {
    @Test
    fun `a cover is served with an immutable cache entry`() =
        withCover(pngBytes(1, 2, 3)) { token, imageId ->
            val response = api.getImageFile(imageId, token)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("image/png", response.headers[HttpHeaders.ContentType])
            assertEquals("\"$imageId\"", response.headers[HttpHeaders.ETag])
            assertEquals(
                "private, max-age=31536000, immutable",
                response.headers[HttpHeaders.CacheControl],
                "an image id always maps to the same bytes, so a client may keep it offline",
            )
        }

    @Test
    fun `a cached cover is not sent again`() =
        withCover(pngBytes(1, 2, 3)) { token, imageId ->
            val headers =
                Headers.build {
                    appendAll(token)
                    append(HttpHeaders.IfNoneMatch, "\"$imageId\"")
                }
            val response = api.getImageFile(imageId, headers)

            assertEquals(HttpStatusCode.NotModified, response.status)
            assertEquals(0, response.readRawBytes().size)
        }

    @Test
    fun `an image the browser could execute is not served as one`() =
        withCover("<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".toByteArray()) { token, imageId ->
            val response = api.getImageFile(imageId, token)

            assertEquals(
                "application/octet-stream",
                response.headers[HttpHeaders.ContentType],
                "an svg served as an image type would run its scripts on our own origin",
            )
            assertEquals("nosniff", response.headers["X-Content-Type-Options"])
            assertTrue(response.headers["Content-Security-Policy"]!!.contains("sandbox"))
        }

    // The bytes are written straight to the table, because getOrCreateImage refuses anything that is not an image
    private fun withCover(
        bytes: ByteArray,
        block: suspend ApplicationTestBuilder.(Headers, Uuid) -> Unit,
    ) = deweyServer {
        val libId = newLibrary("lib", folders = listOf("/media/books"))
        val bookId = newBook("Dune", libId)
        val imageId =
            transaction {
                val id =
                    getOrCreateImage(bytes, null)
                        ?: ImageTable.insertAndGetId { it[image] = ExposedBlob(bytes) }.value
                BookTable.update({ BookTable.id eq bookId }) { it[coverId] = id }
                id
            }
        block(bearer(registerWithAccess("listener", libId, LibraryPermissionLevel.READONLY)), imageId)
    }
}
