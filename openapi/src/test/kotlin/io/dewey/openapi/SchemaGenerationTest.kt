package io.dewey.openapi

import io.dewey.openapi.common.Patch
import io.dewey.openapi.ktor.Description
import io.dewey.openapi.ktor.Secured
import io.dewey.openapi.ktor.Summary
import io.dewey.openapi.ktor.Tagged
import io.dewey.openapi.ktor.delete
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.patch
import io.dewey.openapi.ktor.plugins.OpenAPIConfigurationKey
import io.dewey.openapi.ktor.plugins.OpenAPIRouting
import io.dewey.openapi.ktor.plugins.generateOpenApiSpec
import io.dewey.openapi.ktor.post
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
import kotlinx.serialization.Serializable
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import io.ktor.client.request.get as httpGet

enum class SortDirection(
    val direction: String,
) {
    ASC("ASC"),
    DESC("DESC"),
}

enum class SpecItemKind {
    Book,
    Series,
}

interface SpecNamed {
    val name: String
}

@Serializable
data class SpecItem(
    val id: Uuid,
    val kind: SpecItemKind,
    val total: Long,
    val ratio: Double,
    override val name: String,
) : SpecNamed

@Serializable
data class SpecPage<T>(
    val items: List<T>,
    val total: Long,
)

@Serializable
data class SpecPatch(
    val note: Patch<String?> = Patch.Absent,
    val kind: Patch<SpecItemKind?> = Patch.Absent,
    val label: Patch<String> = Patch.Absent,
    val comment: String?,
)

@Resource("spec")
@Tagged("Spec")
class SpecApi {
    @Summary("List items", method = "GET")
    @Resource("items")
    class Items(
        @Description("Which page to return") val page: Int = 0,
        val order: SortDirection = SortDirection.ASC,
        private val parent: SpecApi,
    )

    @Secured("bearer")
    @Summary("Delete an item", method = "DELETE")
    @Summary("Patch an item", method = "PATCH")
    @Resource("items/{id}")
    class Item(
        val id: Uuid,
        private val parent: SpecApi,
    )

    @Summary("Ping", method = "POST")
    @Resource("ping.check")
    class Ping(
        private val parent: SpecApi,
    )
}

private fun Application.specRoutes() {
    install(Resources)
    install(DataConversion)
    install(ContentNegotiation)
    install(Authentication) { bearer("bearer") { authenticate { UserIdPrincipal("test") } } }
    install(OpenAPIRouting) {
        components {
            securitySchemes {
                http("bearer") {
                    scheme = "bearer"
                    bearerFormat = "JWT"
                }
            }
        }
    }

    routing {
        post<SpecApi.Ping, Unit, Unit> { _, _ -> TODO() }
        get<SpecApi.Items, SpecPage<SpecItem>> { TODO() }
        delete<SpecApi.Item, Unit, Unit> { _, _ -> TODO() }
        patch<SpecApi.Item, SpecPatch, Unit> { _, _ -> TODO() }
    }
}

private fun generateApi(): OpenAPI {
    lateinit var api: OpenAPI
    testApplication {
        application {
            specRoutes()
            val config = attributes[OpenAPIConfigurationKey]
            config.addRoutesToSpec()
            api = config.schemaHolder.api
        }
        client.httpGet("/")
    }
    return api
}

class SchemaGenerationTest {
    private val api = generateApi()

    private val listItems get() = api.paths["/spec/items"]!!.get
    private val deleteItem get() = api.paths["/spec/items/{id}"]!!.delete
    private val ping get() = api.paths["/spec/ping.check"]!!.post

    @Test
    fun `enums become string schemas with their values`() {
        assertEquals("string", api.components.schemas["SpecItemKind"]!!.type)
        assertContentEquals(listOf("Book", "Series"), api.components.schemas["SpecItemKind"]!!.enum)

        assertEquals("string", api.components.schemas["SortDirection"]!!.type)
        assertContentEquals(listOf("ASC", "DESC"), api.components.schemas["SortDirection"]!!.enum)
    }

    @Test
    fun `enum query parameters reference the enum schema`() {
        val order = listItems.parameters.first { it.name == "order" }
        assertEquals("#/components/schemas/SortDirection", order.schema.`$ref`)
    }

    @Test
    fun `component names stay within the allowed character set`() {
        val allowed = "^[a-zA-Z0-9.\\-_]+$".toRegex()
        for (name in api.components.schemas.keys) {
            assertTrue(allowed.matches(name), "component name '$name' is not a valid key")
        }
        assertTrue("SpecPage_SpecItem" in api.components.schemas)
    }

    @Test
    fun `a property from a super type sits on the class, the way it is serialized`() {
        val item = api.components.schemas["SpecItem"]!!
        assertNull(item.allOf)
        assertContains(item.properties.keys, "name")
        assertNull(api.components.schemas["SpecNamed"])
    }

    @Test
    fun `whole numbers are integers and floating point numbers are not`() {
        val item = api.components.schemas["SpecItem"]!!
        assertEquals("integer", item.properties["total"]!!.type)
        assertEquals("int64", item.properties["total"]!!.format)
        assertEquals("number", item.properties["ratio"]!!.type)
        assertEquals(
            "integer",
            listItems.parameters
                .first { it.name == "page" }
                .schema.type,
        )
    }

    @Test
    fun `uuids use the uuid format`() {
        val id = api.components.schemas["SpecItem"]!!.properties["id"]!!
        assertEquals("string", id.type)
        assertEquals("uuid", id.format)
        assertNull(id.pattern)
    }

    @Test
    fun `unit responses carry no body`() {
        val response = ping.responses["204"]!!
        assertNull(response.content)
        assertEquals("No Content", response.description)
    }

    @Test
    fun `unit request bodies are omitted`() {
        assertNull(ping.requestBody)
    }

    @Test
    fun `every operation has an id`() {
        assertEquals("getSpecItems", listItems.operationId)
        assertEquals("deleteSpecItemsById", deleteItem.operationId)
        assertEquals("postSpecPingCheck", ping.operationId)

        val ids = api.paths.values
            .flatMap { it.readOperations() }
            .map { it.operationId }
        assertFalse(ids.any { it.isNullOrBlank() })
        assertEquals(ids.size, ids.toSet().size, "operation ids are not unique")
    }

    @Test
    fun `no response description is left empty`() {
        val descriptions =
            api.paths.values
                .flatMap { it.readOperations() }
                .flatMap { it.responses.values }
                .map { it.description }
        assertFalse(descriptions.any { it.isNullOrBlank() })
        assertEquals("OK", listItems.responses["200"]!!.description)
    }

    @Test
    fun `errors are documented`() {
        val error = api.components.schemas["ErrorResponse"]!!
        assertContentEquals(listOf("error", "status"), error.required)

        for (operation in api.paths.values.flatMap { it.readOperations() }) {
            val default = assertNotNull(operation.responses.default, "${operation.operationId} has no default response")
            assertEquals(
                "#/components/schemas/ErrorResponse",
                default.content["application/json"]!!.schema.`$ref`,
            )
        }

        assertNotNull(deleteItem.responses["401"])
        assertNotNull(deleteItem.responses["403"])
        assertNull(listItems.responses["401"])
    }

    @Test
    fun `patch properties may be left out and are only nullable when their type argument is`() {
        val patch = api.components.schemas["SpecPatch"]!!
        assertContentEquals(listOf("comment"), patch.required, "only a Patch may be left out")
        assertEquals(true, patch.properties["comment"]!!.nullable)

        val note = patch.properties["note"]!!
        assertEquals("string", note.type)
        assertEquals(true, note.nullable)

        val kind = patch.properties["kind"]!!
        assertNull(kind.`$ref`, "nullable next to a \$ref is ignored in OpenAPI 3.0")
        assertEquals("#/components/schemas/SpecItemKind", kind.allOf.single().`$ref`)
        assertEquals(true, kind.nullable)

        val label = patch.properties["label"]!!
        assertEquals("string", label.type)
        assertNull(label.nullable)
    }

    @Test
    fun `the written spec lists paths and components by name`() {
        val file = createTempDirectory().resolve("openapi.yaml")
        testApplication {
            application {
                specRoutes()
                generateOpenApiSpec(file)
            }
            client.httpGet("/")
        }
        val spec = file.readText()

        fun keysUnder(section: String) =
            spec
                .substringAfter("\n$section:\n")
                .lines()
                .takeWhile { it.isEmpty() || it.startsWith(" ") }
                .filter { "^  [^ ].*:$".toRegex().matches(it) }
                .map { it.trim().removeSuffix(":") }

        val paths = keysUnder("paths")
        assertEquals(paths.sorted(), paths)
        assertEquals(3, paths.size)
        val schemas =
            spec
                .substringAfter("\n  schemas:\n")
                .lines()
                .filter { "^    [^ ].*:$".toRegex().matches(it) }
                .map { it.trim().removeSuffix(":") }
        assertEquals(schemas.sorted(), schemas)
        assertContains(schemas, "SpecPage_SpecItem")
    }

    @Test
    fun `parameters can carry a description`() {
        assertEquals("Which page to return", listItems.parameters.first { it.name == "page" }.description)
        assertTrue(deleteItem.parameters.first { it.name == "id" }.required)
    }
}
