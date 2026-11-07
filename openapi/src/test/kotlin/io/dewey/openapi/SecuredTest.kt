package io.dewey.openapi

import io.dewey.openapi.ktor.Secured
import io.dewey.openapi.ktor.Summary
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.plugins.OpenAPIConfigurationKey
import io.dewey.openapi.ktor.plugins.OpenAPIRouting
import io.dewey.openapi.ktor.post
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.dataconversion.DataConversion
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.swagger.v3.oas.models.OpenAPI
import kotlin.test.Test
import kotlin.test.assertEquals

@Secured("reader")
@Resource("shelf")
class Shelf {
    @Summary("List books", method = "GET")
    @Summary("Add book", method = "POST")
    @Secured("writer", method = "POST")
    @Resource("books")
    class Books(
        private val parent: Shelf,
    )
}

private fun Application.shelfRoutes() {
    install(Resources)
    install(DataConversion)
    install(ContentNegotiation)
    install(Authentication) {
        bearer("reader") { authenticate { UserIdPrincipal(it.token) } }
        bearer("writer") {
            authenticate { credential ->
                UserIdPrincipal("writer").takeIf { credential.token == "writer" }
            }
        }
    }
    install(OpenAPIRouting) {
        components {
            securitySchemes {
                for (guard in listOf("reader", "writer")) http(guard) { scheme = "bearer" }
            }
        }
    }
    routing {
        get<Shelf.Books, String> { "" }
        post<Shelf.Books, Unit, Unit> { _, _ -> }
    }
}

class SecuredTest {
    @Test
    fun `a method guarded on its own uses its guard, the other methods the one of the resources above`() =
        testApplication {
            application { shelfRoutes() }

            assertEquals(HttpStatusCode.OK, client.get("/shelf/books") { bearer("reader") }.status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/shelf/books") { bearer("reader") }.status)
            assertEquals(HttpStatusCode.NoContent, client.post("/shelf/books") { bearer("writer") }.status)
        }

    @Test
    fun `the spec documents the guard of each method`() {
        lateinit var api: OpenAPI
        testApplication {
            application {
                shelfRoutes()
                val config = attributes[OpenAPIConfigurationKey]
                config.addRoutesToSpec()
                api = config.schemaHolder.api
            }
            startApplication()
        }
        val books = api.paths.getValue("/shelf/books")

        assertEquals(listOf("reader"), books.get.security.flatMap { it.keys })
        assertEquals(listOf("writer"), books.post.security.flatMap { it.keys })
    }

    private fun HttpRequestBuilder.bearer(token: String) = headers.append("Authorization", "Bearer $token")
}
