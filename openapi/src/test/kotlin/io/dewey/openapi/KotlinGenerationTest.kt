package io.dewey.openapi

import io.dewey.openapi.client.kotlin.KtErrorHandling
import io.dewey.openapi.client.kotlin.generateKotlinClient
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class KotlinGenerationTest {
    private val generated =
        mutableMapOf<String, String>().also { generated ->
            testApplication {
                application {
                    testRoutes()
                    generateKotlinClient(
                        apiClientPackageName = "io.dewey.test",
                        apiClientName = "TestClient",
                        savePath = "",
                        fileWriter = { file, content -> generated[file.path] = content },
                        cleanDistPackage = false,
                        errorHandling = KtErrorHandling.Exception,
                    )
                }
            }
        }

    @Test
    fun `a generic model keeps its type parameters and their bounds`() {
        assertEquals(
            """
            package io.dewey.test.models

            import kotlin.uuid.Uuid
            import kotlinx.serialization.Serializable

            @Serializable
            data class GenericRoute<T : Any>(
                val name: Pair<String, Uuid>,
                val someParam: T
            )
            """.trimIndent(),
            generated["models/GenericRoute.kt"],
        )
    }

    @Test
    fun `a patch property defaults to absent and is never written as absent`() {
        assertEquals(
            """
            package io.dewey.test.models

            import io.dewey.openapi.common.Patch
            import kotlin.uuid.Uuid
            import kotlinx.serialization.EncodeDefault
            import kotlinx.serialization.ExperimentalSerializationApi
            import kotlinx.serialization.Serializable

            @OptIn(ExperimentalSerializationApi::class)
            @Serializable
            data class MapRoute(
                val hint: String? = null,
                @EncodeDefault(EncodeDefault.Mode.NEVER) val label: Patch<String> = Patch.Absent,
                @EncodeDefault(EncodeDefault.Mode.NEVER) val lookup: Patch<Map<String, Uuid>?> = Patch.Absent,
                val name: Boolean,
                @EncodeDefault(EncodeDefault.Mode.NEVER) val note: Patch<String?> = Patch.Absent,
                val someParam: Map<String, Uuid>
            )
            """.trimIndent(),
            generated["models/MapRoute.kt"],
        )
    }

    @Test
    fun `client functions take the query parameters, then the path, then the body, with generic types`() {
        val client = generated.getValue("TestClient.kt")

        fun words(text: String) = text.trim().replace("\\s+".toRegex(), " ")
        assertContains(
            words(client),
            words(
                """
                suspend fun v5(
                    name: String,
                    someParam: List<Int>,
                    path: Uuid,
                    body: GenericRoute2<LocalDate, Uuid>,
                    headers: Headers= Headers.Empty,
                    onBeforeRequest: OnBeforeRequest<GenericRoute2<LocalDate, Uuid>> = { _, _ -> },
                    onAfterRequest: OnAfterRequest<GenericRoute2<LocalDate, Uuid>, GenericRoute3<Instant>> = { _, _ -> }
                ): OpenApiHttpResponse<GenericRoute3<Instant>>
                """,
            ),
        )
        val functions = "suspend fun (\\w+)".toRegex().findAll(client).map { it.groupValues[1] }.toList()
        assertEquals(functions.sorted(), functions, "client functions are ordered by name")
    }
}
