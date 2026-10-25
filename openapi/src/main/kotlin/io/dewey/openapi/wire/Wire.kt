package io.dewey.openapi.wire

import io.dewey.openapi.common.PATCH_SERIAL_NAME
import io.dewey.openapi.ktor.Description
import io.dewey.openapi.ktor.responses.BinaryResponse
import io.dewey.openapi.ktor.responses.FileResponse
import io.dewey.openapi.ktor.responses.RedirectResponse
import io.dewey.openapi.serializion.kotlin.OpenApiJson
import io.ktor.resources.Resource
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.descriptors.getContextualDescriptor
import kotlinx.serialization.descriptors.nonNullOriginal
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf

sealed interface WireType {
    val nullable: Boolean

    data class Scalar(
        val kind: ScalarKind,
        override val nullable: Boolean,
    ) : WireType

    data class ListOf(
        val element: WireType,
        override val nullable: Boolean,
    ) : WireType

    data class MapOf(
        val key: WireType,
        val value: WireType,
        override val nullable: Boolean,
    ) : WireType

    data class Named(
        val name: String,
        val arguments: List<WireType>,
        override val nullable: Boolean,
    ) : WireType

    data class TypeParameter(
        val name: String,
        override val nullable: Boolean,
    ) : WireType
}

enum class ScalarKind {
    STRING,
    CHAR,
    BOOLEAN,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    UUID,
    DATE,
    DATE_TIME,
}

sealed interface Declaration {
    val name: String
}

data class ObjectDeclaration(
    override val name: String,
    val typeParameters: List<TypeParameterDeclaration>,
    val properties: List<Property>,
    val description: String?,
) : Declaration

data class TypeParameterDeclaration(
    val name: String,
    val nonNull: Boolean,
)

data class EnumDeclaration(
    override val name: String,
    val values: List<String>,
) : Declaration

/** [optional] is a key that may be missing, which only a Patch is: every other default is written. */
data class Property(
    val name: String,
    val type: WireType,
    val optional: Boolean,
    val description: String?,
)

/** A route body. Ktor sends a String, a file or a redirect without the JSON converter, everything else as JSON. */
sealed interface Body {
    data object None : Body

    data object Text : Body

    data object Binary : Body

    data object Redirect : Body

    data class Json(
        val type: WireType,
    ) : Body
}

enum class ParameterLocation {
    PATH,
    QUERY,
}

data class Parameter(
    val name: String,
    val type: WireType,
    val location: ParameterLocation,
    val optional: Boolean,
    val description: String?,
)

// One instance per generated artifact, so its declarations are exactly what that artifact references
@OptIn(ExperimentalSerializationApi::class)
class WireModel(
    private val module: SerializersModule = OpenApiJson.serializersModule,
) {
    private val declared = linkedMapOf<String, Pair<SerialDescriptor, Declaration>>()

    val declarations: List<Declaration> get() = declared.values.map { it.second }

    fun declaration(name: String): Declaration = declared.getValue(name).second

    fun body(type: KType): Body {
        val clazz = type.classifier as? KClass<*>
        return when {
            clazz == Unit::class -> Body.None
            clazz == String::class -> Body.Text
            clazz == ByteArray::class -> Body.Binary
            clazz != null && clazz.isSubclassOf(BinaryResponse::class) -> Body.Binary
            clazz != null && clazz.isSubclassOf(FileResponse::class) -> Body.Binary
            clazz != null && clazz.isSubclassOf(RedirectResponse::class) -> Body.Redirect
            else -> Body.Json(type(module.serializer(type).descriptor))
        }
    }

    fun description(type: KType): String? {
        val named = (body(type) as? Body.Json)?.type as? WireType.Named ?: return null
        return (declaration(named.name) as? ObjectDeclaration)?.description
    }

    /**
     * The parameters of a Ktor resource in the one order the spec and both clients use: the query parameters by name,
     * then the path parameters as the path spells them, each time the resource's own before those of its parents.
     */
    fun parameters(resource: KType): List<Parameter> {
        val parameters = mutableListOf<Parameter>()
        collectParameters(module.serializer(resource).descriptor, parameters)
        return parameters.filter { it.location == ParameterLocation.QUERY } +
            parameters.filter { it.location == ParameterLocation.PATH }
    }

    private fun collectParameters(
        descriptor: SerialDescriptor,
        into: MutableList<Parameter>,
    ) {
        val own = mutableListOf<Parameter>()
        collectOwnParameters(descriptor, own, into)
        into += own.filter { it.location == ParameterLocation.QUERY }.sortedBy { it.name }
        into += own.filter { it.location == ParameterLocation.PATH }
        descriptor.parentResources().forEach { collectParameters(it, into) }
    }

    private fun SerialDescriptor.parentResources(): List<SerialDescriptor> =
        (0 until elementsCount).map(::getElementDescriptor).filter { element ->
            element.annotations.any { it is Resource }
        }

    private fun collectOwnParameters(
        descriptor: SerialDescriptor,
        own: MutableList<Parameter>,
        collected: List<Parameter>,
    ) {
        val resource =
            descriptor.annotations.filterIsInstance<Resource>().singleOrNull()
                ?: error("${descriptor.serialName} is not a @Resource, so it cannot carry route parameters")
        val pathNames = PATH_VARIABLE.findAll(resource.path).map { it.groupValues[1] }.toList()
        for (index in 0 until descriptor.elementsCount) {
            val element = descriptor.getElementDescriptor(index)
            if (element.annotations.any { it is Resource }) continue
            val name = descriptor.getElementName(index)
            check((collected + own).none { it.name == name }) {
                "${descriptor.serialName} declares the route parameter $name a second time, rename one of them"
            }
            val inPath = name in pathNames
            own +=
                Parameter(
                    name = name,
                    type = type(element),
                    location = if (inPath) ParameterLocation.PATH else ParameterLocation.QUERY,
                    optional = !inPath && descriptor.isElementOptional(index),
                    description = descriptor.getElementAnnotations(index).description(),
                )
        }
        val missing = pathNames - own.map { it.name }.toSet()
        check(missing.isEmpty()) {
            "${descriptor.serialName} uses ${missing.joinToString {
                "{$it}"
            }} in its path without a property of that name"
        }
        own.sortBy { if (it.location == ParameterLocation.PATH) pathNames.indexOf(it.name) else -1 }
    }

    fun type(descriptor: SerialDescriptor): WireType {
        val nullable = descriptor.isNullable
        val original = descriptor.nonNullOriginal
        if (original.serialName.startsWith(TYPE_PARAMETER_PREFIX)) {
            return WireType.TypeParameter(original.serialName.removePrefix(TYPE_PARAMETER_PREFIX), nullable)
        }
        // A value class is written as the value it wraps, even though its descriptor is a class with one element
        if (original.isInline) return type(original.getElementDescriptor(0)).withNullable(nullable)
        return when (val kind = original.kind) {
            is PrimitiveKind -> {
                WireType.Scalar(scalarKind(original, kind), nullable)
            }

            StructureKind.LIST -> {
                WireType.ListOf(type(original.getElementDescriptor(0)), nullable)
            }

            StructureKind.MAP -> {
                WireType.MapOf(type(original.getElementDescriptor(0)), type(original.getElementDescriptor(1)), nullable)
            }

            SerialKind.ENUM -> {
                val name = declare(original) { EnumDeclaration(it, original.elementNames.toList()) }
                WireType.Named(name, emptyList(), nullable)
            }

            StructureKind.CLASS, StructureKind.OBJECT -> {
                classType(original, nullable)
            }

            SerialKind.CONTEXTUAL -> {
                check(original.serialName != PATCH_SERIAL_NAME) { "A Patch can only be the type of a property" }
                val contextual =
                    module.getContextualDescriptor(original)
                        ?: error("No contextual serializer is registered for ${original.serialName}")
                type(contextual).withNullable(nullable)
            }

            is PolymorphicKind -> {
                error(
                    "${original.serialName} is polymorphic, which neither the schema nor the clients can describe. " +
                        "Use a concrete @Serializable class instead.",
                )
            }
        }
    }

    // A descriptor only exists for an instance like PaginatedResponse<Book>. Asking kotlinx for the same class with
    // placeholder serializers as type arguments gives the descriptor of PaginatedResponse<T>, whose placeholders mark
    // exactly where T goes. Matching the instance against it then reads off Book.
    private fun classType(
        descriptor: SerialDescriptor,
        nullable: Boolean,
    ): WireType.Named {
        val clazz = classOf(descriptor.serialName)?.kotlin
        val parameters =
            clazz?.typeParameters.orEmpty().map { parameter ->
                TypeParameterDeclaration(parameter.name, nonNull = parameter.upperBounds.none { it.isMarkedNullable })
            }
        if (clazz == null || parameters.isEmpty()) {
            val name = declare(descriptor) { objectDeclaration(it, emptyList(), descriptor) }
            return WireType.Named(name, emptyList(), nullable)
        }
        val generic =
            module
                .serializer(clazz, parameters.map { TypeParameterSerializer(it.name) }, isNullable = false)
                .descriptor
        val name = declare(generic) { objectDeclaration(it, parameters, generic) }
        val arguments = mutableMapOf<String, SerialDescriptor>()
        matchArguments(generic, descriptor, arguments, mutableSetOf())
        return WireType.Named(
            name,
            parameters.map { parameter ->
                val argument =
                    arguments[parameter.name]
                        ?: error(
                            "${descriptor.serialName} does not use its type parameter ${parameter.name} in any property",
                        )
                type(argument)
            },
            nullable,
        )
    }

    // Walks every property of the template, so a type that contains itself would recurse forever without [visited]
    private fun matchArguments(
        generic: SerialDescriptor,
        instance: SerialDescriptor,
        into: MutableMap<String, SerialDescriptor>,
        visited: MutableSet<Pair<SerialDescriptor, SerialDescriptor>>,
    ) {
        if (!visited.add(generic to instance)) return
        val original = generic.nonNullOriginal
        if (original.serialName.startsWith(TYPE_PARAMETER_PREFIX)) {
            val parameter = original.serialName.removePrefix(TYPE_PARAMETER_PREFIX)
            if (generic.isNullable) {
                // A property typed T? holds a nullable value whichever argument T has, so its nullability is not T's.
                // A property typed T says whether the argument itself is nullable, and wins over it.
                into.putIfAbsent(parameter, instance.nonNullOriginal)
            } else {
                into[parameter] = instance
            }
            return
        }
        val instanceOriginal = instance.nonNullOriginal
        for (index in 0 until original.elementsCount) {
            matchArguments(
                original.getElementDescriptor(index),
                instanceOriginal.getElementDescriptor(index),
                into,
                visited,
            )
        }
    }

    private fun objectDeclaration(
        name: String,
        typeParameters: List<TypeParameterDeclaration>,
        descriptor: SerialDescriptor,
    ): ObjectDeclaration {
        val properties =
            (0 until descriptor.elementsCount).map { index ->
                val element = descriptor.getElementDescriptor(index)
                val patch = element.nonNullOriginal.serialName == PATCH_SERIAL_NAME
                check(!patch || !element.isNullable) {
                    "${descriptor.serialName}.${descriptor.getElementName(index)} is a nullable Patch, " +
                        "make its value nullable instead: Patch<T?>"
                }
                Property(
                    name = descriptor.getElementName(index),
                    type = type(if (patch) element.getElementDescriptor(0) else element),
                    optional = patch,
                    description = descriptor.getElementAnnotations(index).description(),
                )
            }
        return ObjectDeclaration(name, typeParameters, properties, descriptor.annotations.description())
    }

    // Registers the name before the declaration is built, so a type that refers to itself ends in a reference
    private fun declare(
        descriptor: SerialDescriptor,
        build: (name: String) -> Declaration,
    ): String {
        val name = descriptor.serialName.substringAfterLast('.')
        val existing = declared[name]
        if (existing != null) {
            check(existing.first == descriptor) {
                if (existing.first.serialName == descriptor.serialName) {
                    "${descriptor.serialName} is used with different type arguments, but its class cannot be found " +
                        "under that name to be declared as generic. Remove its custom @SerialName."
                } else {
                    "${existing.first.serialName} and ${descriptor.serialName} would both be declared as $name, " +
                        "give one of them a different @SerialName"
                }
            }
            return name
        }
        declared[name] = descriptor to PLACEHOLDER
        declared[name] = descriptor to build(name)
        return name
    }

    private fun scalarKind(
        descriptor: SerialDescriptor,
        kind: PrimitiveKind,
    ): ScalarKind =
        when (kind) {
            PrimitiveKind.STRING -> FORMATTED_STRINGS[descriptor.serialName] ?: ScalarKind.STRING
            PrimitiveKind.CHAR -> ScalarKind.CHAR
            PrimitiveKind.BOOLEAN -> ScalarKind.BOOLEAN
            PrimitiveKind.BYTE -> ScalarKind.BYTE
            PrimitiveKind.SHORT -> ScalarKind.SHORT
            PrimitiveKind.INT -> ScalarKind.INT
            PrimitiveKind.LONG -> ScalarKind.LONG
            PrimitiveKind.FLOAT -> ScalarKind.FLOAT
            PrimitiveKind.DOUBLE -> ScalarKind.DOUBLE
        }

    private class TypeParameterSerializer(
        parameter: String,
    ) : KSerializer<Any?> {
        override val descriptor = PrimitiveSerialDescriptor("$TYPE_PARAMETER_PREFIX$parameter", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder,
            value: Any?,
        ) = error("A type parameter placeholder is never serialized")

        override fun deserialize(decoder: Decoder): Any? = error("A type parameter placeholder is never deserialized")
    }

    private companion object {
        const val TYPE_PARAMETER_PREFIX = "io.dewey.openapi.wire.TypeParameter."

        val PATH_VARIABLE = "\\{(\\w+)}".toRegex()

        val PLACEHOLDER = EnumDeclaration("", emptyList())

        val FORMATTED_STRINGS =
            mapOf(
                "kotlin.uuid.Uuid" to ScalarKind.UUID,
                "kotlinx.datetime.LocalDate" to ScalarKind.DATE,
                "kotlin.time.Instant" to ScalarKind.DATE_TIME,
            )

        // The default serial name is the class name with nested classes joined by dots, which the class loader
        // spells with $. A class with a custom @SerialName is not found, so a generic one is declared from its first
        // use alone, and declare() refuses a second use with other type arguments.
        fun classOf(serialName: String): Class<*>? {
            var candidate = serialName
            while (true) {
                runCatching { return Class.forName(candidate, false, WireModel::class.java.classLoader) }
                val dot = candidate.lastIndexOf('.')
                if (dot < 0) return null
                candidate = candidate.substring(0, dot) + '$' + candidate.substring(dot + 1)
            }
        }

        fun List<Annotation>.description(): String? = filterIsInstance<Description>().firstOrNull()?.description
    }
}

internal fun WireType.withNullable(nullable: Boolean = true): WireType =
    if (!nullable) {
        this
    } else {
        when (this) {
            is WireType.Scalar -> copy(nullable = true)
            is WireType.ListOf -> copy(nullable = true)
            is WireType.MapOf -> copy(nullable = true)
            is WireType.Named -> copy(nullable = true)
            is WireType.TypeParameter -> copy(nullable = true)
        }
    }
