package io.dewey.openapi

import io.dewey.openapi.common.Patch
import io.dewey.openapi.ktor.Description
import io.dewey.openapi.ktor.schema.SchemaComponents
import io.dewey.openapi.wire.Body
import io.dewey.openapi.wire.ObjectDeclaration
import io.dewey.openapi.wire.Parameter
import io.dewey.openapi.wire.ParameterLocation
import io.dewey.openapi.wire.ScalarKind
import io.dewey.openapi.wire.TypeParameterDeclaration
import io.dewey.openapi.wire.WireModel
import io.dewey.openapi.wire.WireType
import io.ktor.resources.Resource
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class WirePage<T>(
    val items: List<T>,
)

@Serializable
data class WireBook(
    val id: Uuid,
    val released: LocalDate?,
    val seen: Instant,
)

@Serializable
data class WireAuthor(
    val name: String,
)

@Serializable
data class WireUpdate(
    val title: Patch<String> = Patch.Absent,
    val note: Patch<String?> = Patch.Absent,
    val tags: List<String>,
)

@Serializable
data class WireNode(
    val children: List<WireNode>,
)

@Serializable
sealed interface WireShape

object Other {
    @Serializable
    data class WireAuthor(
        val age: Int,
    )
}

@Serializable
data class WireClash(
    val first: WireAuthor,
    val second: Other.WireAuthor,
)

@Resource("libraries/{libraryId}")
class WireLibrary(
    val libraryId: Uuid,
) {
    @Resource("books")
    class Books(
        val q: String,
        @Description("Which page to return") val page: Int = 0,
        private val parent: WireLibrary,
    )

    @Resource("shelves/{shelf}/{slot}")
    class Slot(
        val slot: Int,
        val shelf: String,
        private val parent: WireLibrary,
    )
}

@Serializable
data class WireTree<T>(
    val value: T,
    val children: List<WireTree<T>>,
)

@Serializable
data class WireFiled<T>(
    val items: List<T>,
    val shelf: WireNode,
)

@Serializable
data class WireMaybe<T>(
    val maybe: T?,
    val sure: T,
)

@Serializable
data class WireSure<T>(
    val sure: T,
    val maybe: T?,
)

@JvmInline
@Serializable
value class WireId(
    val value: Uuid,
)

@Serializable
data class WireIdentified(
    val id: WireId,
    val previous: WireId?,
)

@Serializable
@SerialName("Paged")
data class WirePaged<T>(
    val items: List<T>,
)

@Serializable
data class WireNullablePatch(
    val title: Patch<String>? = null,
)

@Serializable
data class WireBounded<T : Any>(
    val value: T,
)

@Serializable
@SerialName("io.dewey.openapi.WirePage_WireBook")
data class WireComponentClash(
    val count: Int,
)

@Serializable
data class WireComponentClashes(
    val page: WirePage<WireBook>,
    val clash: WireComponentClash,
)

@Serializable
data class WireBox<T>(
    val label: String,
    val value: T,
)

@Serializable
data class WireWrapped<T>(
    val page: WirePage<T>,
)

class WireModelTest {
    private val string = WireType.Scalar(ScalarKind.STRING, false)

    private fun named(
        name: String,
        vararg arguments: WireType,
    ) = WireType.Named(name, arguments.toList(), false)

    private fun objectOf(
        model: WireModel,
        name: String,
    ) = model.declarations.filterIsInstance<ObjectDeclaration>().single { it.name == name }

    @Test
    fun `a generic class is declared once and referenced with its type arguments`() {
        val model = WireModel()
        val books = model.body(typeOf<WirePage<WireBook>>())
        val authors = model.body(typeOf<WirePage<WireAuthor>>())

        assertEquals(Body.Json(named("WirePage", named("WireBook"))), books)
        assertEquals(Body.Json(named("WirePage", named("WireAuthor"))), authors)
        val page = objectOf(model, "WirePage")
        assertEquals(listOf("T"), page.typeParameters.map { it.name })
        assertEquals(WireType.ListOf(WireType.TypeParameter("T", false), false), page.properties.single().type)
    }

    @Test
    fun `a field whose type matches the type argument is not mistaken for the parameter`() {
        val model = WireModel()
        assertEquals(Body.Json(named("WireBox", string)), model.body(typeOf<WireBox<String>>()))

        val properties = objectOf(model, "WireBox").properties.associate { it.name to it.type }
        assertEquals(string, properties["label"])
        assertEquals(WireType.TypeParameter("T", false), properties["value"])
    }

    @Test
    fun `a generic inside a generic passes its parameter on`() {
        val model = WireModel()
        model.body(typeOf<WireWrapped<WireBook>>())

        val page = objectOf(model, "WireWrapped").properties.single()
        assertEquals(named("WirePage", WireType.TypeParameter("T", false)), page.type)
    }

    @Test
    fun `two classes that would share a declaration name are refused`() {
        val error = assertFailsWith<IllegalStateException> { WireModel().body(typeOf<WireClash>()) }
        assertContains(error.message!!, "@SerialName")
    }

    @Test
    fun `strings with a known format keep it and nullability comes from the descriptor`() {
        val model = WireModel()
        model.body(typeOf<WireBook>())
        val types = objectOf(model, "WireBook").properties.associate { it.name to it.type }

        assertEquals(WireType.Scalar(ScalarKind.UUID, false), types["id"])
        assertEquals(WireType.Scalar(ScalarKind.DATE, true), types["released"])
        assertEquals(WireType.Scalar(ScalarKind.DATE_TIME, false), types["seen"])
    }

    @Test
    fun `only a patch may be left out and it is nullable only when its value is`() {
        val model = WireModel()
        model.body(typeOf<WireUpdate>())
        val properties = objectOf(model, "WireUpdate").properties.associateBy { it.name }

        assertEquals(true, properties.getValue("title").optional)
        assertEquals(WireType.Scalar(ScalarKind.STRING, false), properties.getValue("title").type)
        assertEquals(WireType.Scalar(ScalarKind.STRING, true), properties.getValue("note").type)
        assertEquals(false, properties.getValue("tags").optional)
    }

    @Test
    fun `a type that contains itself is declared once`() {
        val model = WireModel()
        model.body(typeOf<WireNode>())
        val children = objectOf(model, "WireNode").properties.single()
        assertEquals(WireType.ListOf(named("WireNode"), false), children.type)
    }

    @Test
    fun `polymorphic types are refused`() {
        val error = assertFailsWith<IllegalStateException> { WireModel().body(typeOf<WireShape>()) }
        assertContains(error.message!!, "polymorphic")
    }

    @Test
    fun `bodies that ktor does not send as json are told apart`() {
        val model = WireModel()
        assertEquals(Body.None, model.body(typeOf<Unit>()))
        assertEquals(Body.Text, model.body(typeOf<String>()))
        assertEquals(Body.Binary, model.body(typeOf<ByteArray>()))
    }

    @Test
    fun `route parameters come from the resource and its parents`() {
        val parameters = WireModel().parameters(typeOf<WireLibrary.Books>()).associateBy(Parameter::name)

        assertEquals(listOf("page", "q", "libraryId"), parameters.keys.toList(), "query by name, then the path")
        assertEquals(ParameterLocation.PATH, parameters.getValue("libraryId").location)
        assertEquals(ParameterLocation.QUERY, parameters.getValue("q").location)
        assertEquals(false, parameters.getValue("q").optional)
        assertEquals(true, parameters.getValue("page").optional, "a default makes a query parameter optional")
        assertEquals("Which page to return", parameters.getValue("page").description)
    }

    @Test
    fun `path parameters keep the order of the path, the resource's own before its parents'`() {
        val names = WireModel().parameters(typeOf<WireLibrary.Slot>()).map(Parameter::name)
        assertEquals(listOf("shelf", "slot", "libraryId"), names)
    }

    @Test
    fun `a generic that contains itself is matched without recursing forever`() {
        val model = WireModel()
        assertEquals(Body.Json(named("WireTree", string)), model.body(typeOf<WireTree<String>>()))
        val children = objectOf(model, "WireTree").properties.single { it.name == "children" }
        assertEquals(WireType.ListOf(named("WireTree", WireType.TypeParameter("T", false)), false), children.type)

        assertEquals(Body.Json(named("WireFiled", named("WireBook"))), model.body(typeOf<WireFiled<WireBook>>()))
    }

    @Test
    fun `a property typed T decides whether the argument is nullable, whatever order the properties have`() {
        val nullableString = WireType.Scalar(ScalarKind.STRING, true)
        val model = WireModel()
        assertEquals(Body.Json(named("WireMaybe", nullableString)), model.body(typeOf<WireMaybe<String?>>()))
        assertEquals(Body.Json(named("WireSure", nullableString)), model.body(typeOf<WireSure<String?>>()))
        assertEquals(Body.Json(named("WireMaybe", string)), model.body(typeOf<WireMaybe<String>>()))
    }

    @Test
    fun `a value class is described as the value it wraps`() {
        val model = WireModel()
        model.body(typeOf<WireIdentified>())
        val types = objectOf(model, "WireIdentified").properties.associate { it.name to it.type }

        assertEquals(WireType.Scalar(ScalarKind.UUID, false), types["id"])
        assertEquals(WireType.Scalar(ScalarKind.UUID, true), types["previous"])
        assertEquals(null, model.declarations.find { it.name == "WireId" })
    }

    @Test
    fun `a generic class with a custom serial name is refused with the cause once it is used twice`() {
        val model = WireModel()
        model.body(typeOf<WirePaged<WireBook>>())
        val error = assertFailsWith<IllegalStateException> { model.body(typeOf<WirePaged<WireAuthor>>()) }
        assertContains(error.message!!, "Remove its custom @SerialName")
    }

    @Test
    fun `a nullable patch is refused with what to write instead`() {
        val error = assertFailsWith<IllegalStateException> { WireModel().body(typeOf<WireNullablePatch>()) }
        assertContains(error.message!!, "Patch<T?>")
    }

    @Test
    fun `a type parameter bounded by Any is declared non null`() {
        val model = WireModel()
        model.body(typeOf<WireBounded<String>>())
        model.body(typeOf<WirePage<WireBook>>())

        assertEquals(listOf(TypeParameterDeclaration("T", true)), objectOf(model, "WireBounded").typeParameters)
        assertEquals(listOf(TypeParameterDeclaration("T", false)), objectOf(model, "WirePage").typeParameters)
    }

    @Test
    fun `a pair is the object kotlinx writes, not a tuple`() {
        val model = WireModel()
        assertEquals(Body.Json(named("Pair", string, named("WireBook"))), model.body(typeOf<Pair<String, WireBook>>()))
        val pair = objectOf(model, "Pair")
        assertEquals(listOf("A", "B"), pair.typeParameters.map { it.name })
        assertEquals(
            listOf("first" to WireType.TypeParameter("A", false), "second" to WireType.TypeParameter("B", false)),
            pair.properties.map { it.name to it.type },
        )
    }

    @Test
    fun `the spec names each use of a generic after its arguments, nullable ones included`() {
        val model = WireModel()
        val components = SchemaComponents(model)
        components.schema(model.body(typeOf<WirePage<WireBook?>>()))
        components.schema(model.body(typeOf<WirePage<WireBook>>()))

        assertContains(components.components.keys, "WirePage_WireBookOrNull")
        assertContains(components.components.keys, "WirePage_WireBook")
        assertEquals(
            true,
            components.components
                .getValue("WirePage_WireBookOrNull")
                .properties["items"]!!
                .items.nullable,
        )
    }

    @Test
    fun `a class whose name a generic use would take in the spec is refused`() {
        val model = WireModel()
        val error =
            assertFailsWith<IllegalStateException> {
                SchemaComponents(model).schema(model.body(typeOf<WireComponentClashes>()))
            }
        assertContains(error.message!!, "WirePage_WireBook")
    }
}
