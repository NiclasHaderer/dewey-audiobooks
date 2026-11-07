package io.dewey.openapi

import io.dewey.openapi.ktor.plugins.OpenAPIWebUI
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocsUiTest {
    @Test
    fun `serves swagger ui assets`() =
        testApplication {
            application {
                openApi()
                install(OpenAPIWebUI)
            }

            val index = client.get("/docs/")
            assertEquals(HttpStatusCode.OK, index.status)
            assertTrue(index.bodyAsText().contains("swagger-ui.css"))

            for (asset in listOf(
                "swagger-ui.css",
                "swagger-ui-bundle.js",
                "swagger-ui-standalone-preset.js",
                "index.css",
            )) {
                assertEquals(HttpStatusCode.OK, client.get("/docs/$asset").status, "asset $asset")
            }
        }
}
