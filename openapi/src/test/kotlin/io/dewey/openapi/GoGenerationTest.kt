package io.dewey.openapi

import io.dewey.openapi.client.go.generateGoClient
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GoGenerationTest {
    private val generated =
        mutableMapOf<String, String>().also { generated ->
            testApplication {
                application {
                    testRoutes()
                    generateGoClient(
                        packageName = "client",
                        importPath = "example.com/generated/client",
                        savePath = "",
                        fileWriter = { file, content -> generated[file.path] = content },
                        cleanDistPackage = false,
                    )
                }
            }
        }

    private fun words(text: String) = text.trim().replace("\\s+".toRegex(), " ")

    @Test
    fun `every model gets a file of its own in the models package`() {
        assertEquals(
            listOf(
                "client.go",
                "models/generic_route.go",
                "models/generic_route2.go",
                "models/generic_route3.go",
                "models/list_route.go",
                "models/map_route.go",
                "models/pair.go",
                "models/set_route.go",
                "models/wire.go",
                "runtime.go",
            ),
            generated.keys.sorted(),
        )
        assertContains(generated.getValue("models/set_route.go"), "package models\n")
    }

    @Test
    fun `a generic model becomes a generic struct`() {
        assertContains(
            words(generated.getValue("models/generic_route.go")),
            words(
                """
                type GenericRoute[T any] struct {
                    Name Pair[string, UUID] `json:"name"`
                    SomeParam T `json:"someParam"`
                }
                """,
            ),
        )
    }

    @Test
    fun `a patch property is a Patch left out while unset, a nullable one a pointer`() {
        assertContains(
            words(generated.getValue("models/map_route.go")),
            words(
                """
                type MapRoute struct {
                    Hint *string `json:"hint"`
                    Label Patch[string] `json:"label,omitzero"`
                    Lookup Patch[map[string]UUID] `json:"lookup,omitzero"`
                    Name bool `json:"name"`
                    Note Patch[*string] `json:"note,omitzero"`
                    SomeParam map[string]UUID `json:"someParam"`
                }
                """,
            ),
        )
    }

    @Test
    fun `a method takes the path, then the required query, then the body`() {
        assertContains(
            words(generated.getValue("client.go")),
            words(
                """
                func (c *Client) V5(ctx context.Context, path models.UUID, name string, someParam []int32, body models.GenericRoute2[models.Date, models.UUID]) (models.GenericRoute3[time.Time], error) {
                    query := url.Values{}
                    query.Set("name", queryValue(name))
                    for _, item := range someParam {
                        query.Add("someParam", queryValue(item))
                    }
                    r := request{
                        method: "POST",
                        path: "/" + pathValue(path) + "/V5",
                        query: query,
                        body: body,
                        hasBody: true,
                        secured: false,
                    }
                """,
            ),
        )
    }

    @Test
    fun `the event socket is left out`() {
        assertFalse("Listen" in generated.getValue("client.go"))
    }

    @Test
    fun `the generated client compiles`() {
        val path = System.getenv("PATH").orEmpty().split(':')
        val go = path.map { File(it, "go") }.firstOrNull { it.canExecute() }
        // CI builds the server without a Go toolchain, the cli workflow compiles the real client instead
        assumeTrue(go != null, "no Go toolchain on the PATH")
        val module = Files.createTempDirectory("go-client")
        try {
            module.resolve("go.mod").writeText("module example.com/generated\n\ngo 1.24\n")
            val packageDir = module.resolve("client").createDirectories()
            generated.forEach { (name, content) ->
                val file = packageDir.resolve(name)
                file.parent.createDirectories()
                file.writeText(content)
            }
            val process =
                ProcessBuilder(go!!.path, "vet", "./...")
                    .directory(module.toFile())
                    .redirectErrorStream(true)
                    .apply { environment()["GOFLAGS"] = "-mod=mod" }
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(2, TimeUnit.MINUTES)
            assertEquals(0, process.exitValue(), output)
        } finally {
            module.toFile().deleteRecursively()
        }
    }
}
