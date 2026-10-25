@file:OptIn(ExperimentalSerializationApi::class)

package io.dewey.server.plugins

import io.dewey.openapi.common.Patch
import io.dewey.openapi.serializion.kotlin.OpenApiJson
import io.dewey.server.database.tables.AuthorField
import kotlinx.datetime.LocalDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.time.Instant

@Serializable
data class Wire(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val required: Patch<String> = Patch.Absent,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val nullable: Patch<String?> = Patch.Absent,
    val plain: String?,
)

@Serializable
data class Tagged(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tags: Patch<List<String>> = Patch.Absent,
)

@Serializable
data class Unannotated(
    val tags: Patch<List<String>> = Patch.Absent,
)

@Serializable
data class Scalars(
    val count: Int,
    val flag: Boolean,
    val name: String,
    val tags: List<String>,
)

class SerializationTest {
    private fun read(json: String) = OpenApiJson.decodeFromString<Wire>(json)

    private fun readScalars(json: String) = OpenApiJson.decodeFromString<Scalars>(json)

    @Test
    fun `a missing key reads as absent`() {
        assertEquals(Wire(plain = null), read("""{"plain": null}"""))
    }

    @Test
    fun `a null reads as null where the field may be null`() {
        assertEquals(Wire(nullable = Patch.Set(null), plain = null), read("""{"nullable": null, "plain": null}"""))
    }

    @Test
    fun `a null is rejected where the field may not be null`() {
        val error = assertFailsWith<SerializationException> { read("""{"required": null, "plain": null}""") }
        assertEquals("null is not allowed here, leave the key out instead", error.message)
    }

    @Test
    fun `a value reads as set`() {
        assertEquals(
            Wire(required = Patch.Set("a"), nullable = Patch.Set("b"), plain = "c"),
            read("""{"required": "a", "nullable": "b", "plain": "c"}"""),
        )
    }

    @Test
    fun `only a patch may be left out`() {
        assertFails("a nullable key that is not a Patch must still be sent") { read("{}") }
    }

    @Test
    fun `values are not coerced into another type`() {
        val valid = """{"count": 1, "flag": true, "name": "a", "tags": ["x"]}"""
        assertEquals(Scalars(1, true, "a", listOf("x")), readScalars(valid))

        assertFails("a fraction is not a whole number") { readScalars(valid.replace("1,", "1.5,")) }
        assertFails("a number is not a string") { readScalars(valid.replace("\"a\"", "1")) }
        assertFails("a list of non null strings holds no null") { readScalars(valid.replace("[\"x\"]", "[null]")) }
        assertFails("a single value is not a list") { readScalars(valid.replace("[\"x\"]", "\"x\"")) }
    }

    @Test
    fun `an absent patch is left out and every null is written`() {
        assertEquals("""{"plain":null}""", OpenApiJson.encodeToString(Wire(plain = null)))
        assertEquals(
            """{"nullable":null,"plain":null}""",
            OpenApiJson.encodeToString(Wire(nullable = Patch.Set(null), plain = null)),
        )
        assertEquals(
            """{"required":"a","nullable":"b","plain":"c"}""",
            OpenApiJson.encodeToString(Wire(Patch.Set("a"), Patch.Set("b"), "c")),
        )
    }

    @Test
    fun `an absent patch without EncodeDefault NEVER fails instead of writing a value`() {
        assertFailsWith<SerializationException> { OpenApiJson.encodeToString(Unannotated()) }
    }

    @Test
    fun `every state survives a round trip`() {
        val states =
            listOf(
                Wire(plain = null),
                Wire(nullable = Patch.Set(null), plain = null),
                Wire(Patch.Set("a"), Patch.Set("b"), "c"),
                Wire(required = Patch.Set("a"), nullable = Patch.Set(null), plain = "c"),
            )
        for (state in states) {
            assertEquals(state, read(OpenApiJson.encodeToString(state)))
        }
    }

    @Test
    fun `a patch reads its value with the type argument`() {
        assertEquals(Tagged(Patch.Set(listOf("x"))), OpenApiJson.decodeFromString<Tagged>("""{"tags": ["x"]}"""))
    }

    @Test
    fun `a metadata field goes over the wire under the name the generated clients use`() {
        assertEquals("\"BORN_IN\"", OpenApiJson.encodeToString(AuthorField.BORN_IN))
        assertEquals(AuthorField.BORN_IN, OpenApiJson.decodeFromString<AuthorField>("\"BORN_IN\""))
    }
}
