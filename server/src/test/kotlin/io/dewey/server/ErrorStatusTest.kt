package io.dewey.server

import io.dewey.metadata.MetadataProviderUnavailableException
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorStatusTest : DeweyTest() {
    private fun assertAnswer(
        failure: Throwable,
        expected: HttpStatusCode,
    ) = testApplication {
        application {
            plugins()
            routing { get("/fails") { throw failure } }
        }

        assertEquals(expected, client.get("/fails").status)
    }

    @Test
    fun `an unreachable metadata provider is answered with a bad gateway`() =
        assertAnswer(MetadataProviderUnavailableException("Audible is not answering"), HttpStatusCode.BadGateway)

    @Test
    fun `any other failure is still answered with an internal server error`() =
        assertAnswer(IllegalStateException("something else broke"), HttpStatusCode.InternalServerError)

    @Test
    fun `a resource that refuses its parameters is answered with its own reason`() =
        testApplication {
            application {
                plugins()
                routing {
                    get("/fails") {
                        throw BadRequestException(
                            "Can't transform call to resource",
                            ErrorResponse.userError("Pass a query"),
                        )
                    }
                }
            }

            val response = client.get("/fails")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(
                "Pass a query",
                Json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `an error names no exception class`() =
        testApplication {
            application {
                plugins()
                routing { get("/fails") { throw BadRequestException("Malformed date") } }
            }

            assertEquals(
                "Malformed date",
                Json
                    .parseToJsonElement(client.get("/fails").bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }
}
