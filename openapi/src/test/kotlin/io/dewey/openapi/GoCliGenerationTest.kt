package io.dewey.openapi

import io.dewey.openapi.client.go.CommandName
import io.dewey.openapi.client.go.commandName
import io.dewey.openapi.client.go.generateGoCli
import io.dewey.openapi.ktor.Summary
import io.dewey.openapi.ktor.get
import io.ktor.resources.Resource
import io.ktor.server.routing.routing
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant

@Summary("List timed", method = "GET")
@Resource("timed")
class TimedRoute(
    val since: Instant,
)

class GoCliGenerationTest {
    private val commands =
        generatedFiles { fileWriter ->
            testRoutes()
            generateGoCli(
                packageName = "commands",
                clientImportPath = "example.com/generated/client",
                savePath = "",
                loginOnlyGuards = setOf("login"),
                fileWriter = fileWriter,
                cleanDistPackage = false,
            )
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
        assertContains(commands, "listKeysCommand(api)")
        assertFalse("rotateKeysCommand" in commands)
    }

    @Test
    fun `a route left out of the cli has no command`() {
        assertContains(commands, "listCookiesCommand(api)")
        assertFalse("refreshCookieCommand" in commands)
    }

    @Test
    fun `the group of a tag is the help section of its commands`() {
        assertContains(commands, "root.AddGroup(&cobra.Group{ID: \"library\", Title: \"LIBRARY COMMANDS\"})")
        assertContains(
            commands,
            "shelvesGroup := &cobra.Command{Use: \"shelves\", Short: \"Shelves of books\", GroupID: \"library\"",
        )
    }

    @Test
    fun `a list of query values is a flag given once per item, required when the route requires it`() {
        val v5 = commands.substringAfter("func v5Command").substringBefore("\n}\n")

        assertContains(v5, "Use:     \"v5 <path> <name>\"")
        assertContains(v5, "someParamFlag := listFlag(flags, \"some-param\", intKind[int32]()")
        assertContains(v5, "_ = cmd.MarkFlagRequired(\"some-param\")")
        assertContains(v5, "c.V5(cmd.Context(), path, name, *someParamFlag, requestBody)")
    }

    @Test
    fun `a parameter the command line cannot spell is refused`() {
        val error =
            assertFailsWith<IllegalStateException> {
                generatedFiles { fileWriter ->
                    openApi()
                    routing { get<TimedRoute, List<String>> { TODO() } }
                    generateGoCli(
                        packageName = "commands",
                        clientImportPath = "example.com/generated/client",
                        savePath = "",
                        fileWriter = fileWriter,
                        cleanDistPackage = false,
                    )
                }
            }
        assertContains(error.message!!, "List timed takes since as date-time (RFC 3339)")
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
