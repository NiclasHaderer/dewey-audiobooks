package io.dewey.openapi

import io.dewey.openapi.ktor.Summary
import io.dewey.openapi.ktor.plugins.OpenAPIConfigurationKey
import io.dewey.openapi.ktor.plugins.OpenAPIRouting
import io.dewey.openapi.ktor.post
import io.dewey.openapi.serializion.kotlin.OpenApiJson
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.resources.Resource
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.dataconversion.DataConversion
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

@Summary("Ping", method = "POST")
@Resource("no-content")
class NoContentRoute

@Summary("Echo", method = "POST")
@Resource("echo")
class EchoRoute

@Summary("Create", method = "POST", status = 201)
@Resource("create")
class CreateRoute

private fun Application.statusRoutes() {
    install(Resources)
    install(DataConversion)
    install(ContentNegotiation) { json(OpenApiJson) }
    install(OpenAPIRouting)

    routing {
        post<NoContentRoute, Unit, Unit> { _, _ -> }
        post<EchoRoute, String, String> { _, body -> body }
        post<CreateRoute, String, String> { _, body -> body }
    }
}

class ResponseStatusTest {
    @Test
    fun `a unit response is 204 with no body for any accept header`() =
        testApplication {
            application { statusRoutes() }

            for (accept in listOf(ContentType.Text.Plain, ContentType.Application.Json, ContentType.Any)) {
                val response = client.post("/no-content") { accept(accept) }
                assertEquals(HttpStatusCode.NoContent, response.status, "Accept: $accept")
                assertEquals("", response.bodyAsText(), "Accept: $accept")
            }
        }

    @Test
    fun `a post answers 200 unless it declares otherwise`() =
        testApplication {
            application { statusRoutes() }

            val response =
                client.post("/echo") {
                    contentType(ContentType.Application.Json)
                    setBody("\"hello\"")
                }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `a declared status is what the handler actually answers`() =
        testApplication {
            application { statusRoutes() }

            val response =
                client.post("/create") {
                    contentType(ContentType.Application.Json)
                    setBody("\"hello\"")
                }
            assertEquals(HttpStatusCode.Created, response.status)
        }

    @Test
    fun `the schema advertises the same status the handler answers`() =
        testApplication {
            lateinit var codes: Map<String, Set<String>>
            application {
                statusRoutes()
                val config = attributes[OpenAPIConfigurationKey]
                config.addRoutesToSpec()
                codes =
                    config.schemaHolder.api.paths.mapValues { (_, item) ->
                        item.post.responses.keys
                            .filterNot { it == "default" }
                            .toSet()
                    }
            }
            client.post("/no-content")

            assertEquals(setOf("204"), codes["/no-content"])
            assertEquals(setOf("200"), codes["/echo"])
            assertEquals(setOf("201"), codes["/create"])
        }
}
