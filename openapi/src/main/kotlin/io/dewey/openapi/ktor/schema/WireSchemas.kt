package io.dewey.openapi.ktor.schema

import io.dewey.openapi.wire.Body
import io.dewey.openapi.wire.EnumDeclaration
import io.dewey.openapi.wire.ObjectDeclaration
import io.dewey.openapi.wire.ScalarKind
import io.dewey.openapi.wire.WireModel
import io.dewey.openapi.wire.WireType
import io.dewey.openapi.wire.withNullable
import io.ktor.http.ContentType
import io.swagger.v3.core.util.RefUtils
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.BooleanSchema
import io.swagger.v3.oas.models.media.DateSchema
import io.swagger.v3.oas.models.media.DateTimeSchema
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.NumberSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema

/**
 * OpenAPI 3.0 has no generics, so every use of a generic declaration becomes a component of its own, named after its
 * type arguments the way PaginatedResponse<Book> becomes PaginatedResponse_Book.
 */
internal class SchemaComponents(
    private val model: WireModel,
) {
    val components = linkedMapOf<String, Schema<*>>()
    private val componentTypes = mutableMapOf<String, WireType.Named>()

    fun schema(body: Body): Schema<*>? =
        when (body) {
            Body.None -> null
            Body.Text, Body.Redirect -> StringSchema()
            Body.Binary -> StringSchema().format("binary")
            is Body.Json -> schema(body.type)
        }

    fun schema(type: WireType): Schema<*> {
        val schema =
            when (type) {
                is WireType.Scalar -> type.kind.schema()
                is WireType.ListOf -> ArraySchema().items(schema(type.element))
                is WireType.MapOf -> ObjectSchema().additionalProperties(schema(type.value))
                is WireType.Named -> Schema<Any>().`$ref`(RefUtils.constructRef(component(type)))
                is WireType.TypeParameter -> error("Type parameter ${type.name} was not substituted")
            }
        if (!type.nullable) return schema
        // OpenAPI 3.0 ignores every sibling of a $ref, so a named schema has to be wrapped to carry the nullable
        val wrapped = if (schema.`$ref` != null) Schema<Any>().allOf(listOf(schema)) else schema
        return wrapped.nullable(true)
    }

    private fun component(type: WireType.Named): String {
        val name = componentName(type)
        val nonNull = type.copy(nullable = false)
        val existing = componentTypes.putIfAbsent(name, nonNull)
        if (existing != null) {
            check(existing == nonNull) {
                "${existing.name} and ${type.name} would both become the component $name, rename one of them"
            }
            return name
        }
        components[name] = ObjectSchema()
        components[name] =
            when (val declaration = model.declaration(type.name)) {
                is EnumDeclaration -> {
                    StringSchema().also { schema ->
                        declaration.values.forEach(schema::addEnumItemObject)
                    }
                }

                is ObjectDeclaration -> {
                    val arguments = declaration.typeParameters
                        .map { it.name }
                        .zip(type.arguments)
                        .toMap()
                    val sorted = declaration.properties.sortedBy { it.name }
                    ObjectSchema().also { schema ->
                        schema.required = sorted.filterNot { it.optional }.map { it.name }
                        schema.properties =
                            sorted.associate { property ->
                                val propertySchema = schema(property.type.substitute(arguments))
                                property.name to propertySchema.also { it.description = property.description }
                            }
                        schema.description = declaration.description
                    }
                }
            }
        return name
    }
}

internal val Body.contentType: ContentType
    get() =
        when (this) {
            Body.None, Body.Text, Body.Redirect -> ContentType.Text.Plain
            Body.Binary -> ContentType.Application.OctetStream
            is Body.Json -> ContentType.Application.Json
        }

private fun WireType.substitute(arguments: Map<String, WireType>): WireType =
    when (this) {
        is WireType.TypeParameter -> {
            val argument = arguments.getValue(name)
            if (nullable) argument.withNullable() else argument
        }

        is WireType.ListOf -> {
            copy(element = element.substitute(arguments))
        }

        is WireType.MapOf -> {
            copy(key = key.substitute(arguments), value = value.substitute(arguments))
        }

        is WireType.Named -> {
            copy(arguments = this.arguments.map { it.substitute(arguments) })
        }

        is WireType.Scalar -> {
            this
        }
    }

private fun componentName(type: WireType.Named): String =
    (listOf(type.name) + type.arguments.map(::argumentName)).joinToString("_")

private fun argumentName(type: WireType): String {
    val name =
        when (type) {
            is WireType.Scalar -> type.kind.argumentName
            is WireType.ListOf -> "List_${argumentName(type.element)}"
            is WireType.MapOf -> "Map_${argumentName(type.key)}_${argumentName(type.value)}"
            is WireType.Named -> componentName(type)
            is WireType.TypeParameter -> type.name
        }
    return if (type.nullable) "${name}OrNull" else name
}

private val ScalarKind.argumentName: String
    get() =
        when (this) {
            ScalarKind.STRING -> "String"
            ScalarKind.CHAR -> "Char"
            ScalarKind.BOOLEAN -> "Boolean"
            ScalarKind.BYTE -> "Byte"
            ScalarKind.SHORT -> "Short"
            ScalarKind.INT -> "Int"
            ScalarKind.LONG -> "Long"
            ScalarKind.FLOAT -> "Float"
            ScalarKind.DOUBLE -> "Double"
            ScalarKind.UUID -> "Uuid"
            ScalarKind.DATE -> "LocalDate"
            ScalarKind.DATE_TIME -> "Instant"
        }

private fun ScalarKind.schema(): Schema<*> =
    when (this) {
        ScalarKind.STRING, ScalarKind.CHAR -> StringSchema()
        ScalarKind.BOOLEAN -> BooleanSchema()
        ScalarKind.BYTE -> IntegerSchema().format("int8")
        ScalarKind.SHORT -> IntegerSchema().format("int16")
        ScalarKind.INT -> IntegerSchema().format("int32")
        ScalarKind.LONG -> IntegerSchema().format("int64")
        ScalarKind.FLOAT -> NumberSchema().format("float")
        ScalarKind.DOUBLE -> NumberSchema().format("double")
        ScalarKind.UUID -> StringSchema().format("uuid")
        ScalarKind.DATE -> DateSchema()
        ScalarKind.DATE_TIME -> DateTimeSchema()
    }
