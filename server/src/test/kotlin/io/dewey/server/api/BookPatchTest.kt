package io.dewey.server.api

import io.dewey.client.models.BookField
import io.dewey.client.models.BookUpdate
import io.dewey.openapi.common.Patch
import io.dewey.server.DeweyTest
import io.dewey.server.api
import io.dewey.server.bearer
import io.dewey.server.database.tables.BookTable
import io.dewey.server.deweyServer
import io.dewey.server.login
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookPatchTest : DeweyTest() {
    @Test
    fun `an explicit null blanks a field while a missing key leaves it alone`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            transaction {
                BookTable.update({ BookTable.id eq bookId }) {
                    it[description] = "From the tags"
                    it[publisher] = "Tagged"
                }
            }
            val token = bearer(login("admin"))

            val response = api.updateBook(bookId, libId, BookUpdate(description = Patch.Set(null)), token)

            assertEquals(HttpStatusCode.OK, response.status)
            val book = response.body()
            assertNull(book.description, "null must blank the tagged description")
            assertEquals("Tagged", book.publisher, "a key that was not sent must not change anything")
            assertEquals("Dune", book.title, "an unsent title must not change anything")
        }

    @Test
    fun `an edit locks the field until it is unlocked`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            val token = bearer(login("admin"))

            api.updateBook(bookId, libId, BookUpdate(description = Patch.Set("Mine")), token)
            val edited = api.getBook(bookId, libId, token).body()
            assertEquals("Mine", edited.description)
            assertEquals(listOf(BookField.DESCRIPTION), edited.locked)

            val response =
                api.updateBook(bookId, libId, BookUpdate(unlock = Patch.Set(listOf(BookField.DESCRIPTION))), token)

            assertEquals(HttpStatusCode.OK, response.status)
            val unlocked = api.getBook(bookId, libId, token).body()
            assertEquals("Mine", unlocked.description, "unlocking keeps the value until the next scan or match")
            assertEquals(emptyList(), unlocked.locked)
        }

    @Test
    fun `setting and unlocking the same field is rejected`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            val token = bearer(login("admin"))

            val response =
                api.updateBook(
                    bookId,
                    libId,
                    BookUpdate(description = Patch.Set("Mine"), unlock = Patch.Set(listOf(BookField.DESCRIPTION))),
                    token,
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `a blank title is rejected`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            val token = bearer(login("admin"))

            val response = api.updateBook(bookId, libId, BookUpdate(title = Patch.Set("  ")), token)

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `a null title is rejected`() =
        deweyServer {
            val libId = newLibrary("lib", folders = listOf("/media/books"))
            val bookId = newBook("Dune", libId)
            val token = bearer(login("admin"))

            val response =
                client.patch("/api/libraries/$libId/books/$bookId") {
                    headers.appendAll(token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"title": null}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
}
