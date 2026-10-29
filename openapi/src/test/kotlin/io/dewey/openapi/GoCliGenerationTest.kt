package io.dewey.openapi

import io.dewey.openapi.client.go.CommandName
import io.dewey.openapi.client.go.commandName
import io.dewey.openapi.client.go.generateGoCli
import io.dewey.openapi.ktor.Secured
import io.dewey.openapi.ktor.Summary
import io.dewey.openapi.ktor.Tagged
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.plugins.OpenAPIRouting
import io.dewey.openapi.ktor.post
import io.ktor.resources.Resource
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.dataconversion.DataConversion
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@Summary("List shelves", method = "GET")
@Tagged("Shelves", description = "Shelves of books", group = "Library")
@Resource("shelves")
class ShelvesRoute

@Summary("List keys", method = "GET")
@Summary("Rotate keys", method = "POST")
@Secured("login", method = "POST")
@Resource("keys")
class KeysRoute

class GoCliGenerationTest {
    private val commands =
        mutableMapOf<String, String>()
            .also { generated ->
                testApplication {
                    application {
                        testRoutes()
                        generateGoCli(
                            packageName = "commands",
                            clientImportPath = "example.com/generated/client",
                            savePath = "",
                            fileWriter = { file, content -> generated[file.path] = content },
                            cleanDistPackage = false,
                        )
                    }
                }
            }.getValue("commands.go")

    @Test
    fun `the help spells out the body and the output with their generic arguments filled in`() {
        val v5 = commands.substringAfter("func v5Command").substringBefore("cmd.RunE")

        assertContains(v5, "\"BODY\": \"name        string\\nsomeParam   date (YYYY-MM-DD)\\nsomeParam2  uuid\"")
        assertContains(v5, "\"JSON FIELDS\": \"name        string\\nsomeParam   map of string to date-time")
        assertContains(v5, "someParam2  GenericRoute2 of date-time (RFC 3339), string")
        assertContains(v5, "\"TYPES\": \"GenericRoute2 of date-time (RFC 3339), string\\n")
    }

    @Test
    fun `a route behind a guard that refuses access tokens has no command`() {
        val generated = mutableMapOf<String, String>()
        testApplication {
            application {
                install(Resources)
                install(DataConversion)
                install(ContentNegotiation)
                install(Authentication) { bearer("login") { authenticate { UserIdPrincipal(it.token) } } }
                install(OpenAPIRouting)
                routing {
                    get<KeysRoute, List<String>> { emptyList() }
                    post<KeysRoute, Unit, Unit> { _, _ -> }
                }
                generateGoCli(
                    packageName = "commands",
                    clientImportPath = "example.com/generated/client",
                    savePath = "",
                    loginOnlyGuards = setOf("login"),
                    fileWriter = { file, content -> generated[file.path] = content },
                    cleanDistPackage = false,
                )
            }
        }
        val commands = generated.getValue("commands.go")

        assertContains(commands, "listKeysCommand(api)")
        assertFalse("rotateKeysCommand" in commands)
    }

    @Test
    fun `the group of a tag is the help section of its commands`() {
        val generated = mutableMapOf<String, String>()
        testApplication {
            application {
                install(Resources)
                install(DataConversion)
                install(ContentNegotiation)
                install(OpenAPIRouting)
                routing { get<ShelvesRoute, List<String>> { emptyList() } }
                generateGoCli(
                    packageName = "commands",
                    clientImportPath = "example.com/generated/client",
                    savePath = "",
                    fileWriter = { file, content -> generated[file.path] = content },
                    cleanDistPackage = false,
                )
            }
        }
        val commands = generated.getValue("commands.go")

        assertContains(commands, "root.AddGroup(&cobra.Group{ID: \"library\", Title: \"LIBRARY COMMANDS\"})")
        assertContains(commands, "Use: \"shelves\", Short: \"Shelves of books\", GroupID: \"library\"")
    }

    @Test
    fun `the tag is the group and the summary without the tag's noun the command`() {
        assertEquals(CommandName("books", "get"), commandName("Get book", "Books"))
        assertEquals(CommandName("books", "set-progress"), commandName("Set book progress", "Books"))
        assertEquals(CommandName("libraries", "list"), commandName("List libraries", "Libraries"))
        assertEquals(CommandName("access-tokens", "reveal"), commandName("Reveal access token", "Access tokens"))
    }

    @Test
    fun `a summary that does not name its tag keeps all of its words`() {
        assertEquals(CommandName("me", "get-listening-history"), commandName("Get listening history", "Me"))
    }

    @Test
    fun `a route without a tag is a command of its own`() {
        assertEquals(CommandName(null, "ping-server"), commandName("Ping server", null))
    }

    @Test
    fun `a summary that is only the tag's noun has no command to become`() {
        assertFailsWith<IllegalStateException> { commandName("Books", "Books") }
    }
}
