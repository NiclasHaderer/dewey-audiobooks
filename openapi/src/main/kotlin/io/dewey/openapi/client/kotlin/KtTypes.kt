package io.dewey.openapi.client.kotlin

import io.dewey.openapi.common.Patch
import io.dewey.openapi.wire.Body
import io.dewey.openapi.wire.Declaration
import io.dewey.openapi.wire.EnumDeclaration
import io.dewey.openapi.wire.ObjectDeclaration
import io.dewey.openapi.wire.ScalarKind
import io.dewey.openapi.wire.WireType

internal fun WireType.kotlin(): String {
    val base =
        when (this) {
            is WireType.Scalar -> {
                when (kind) {
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
            }

            is WireType.ListOf -> {
                "List<${element.kotlin()}>"
            }

            is WireType.MapOf -> {
                "Map<${key.kotlin()}, ${value.kotlin()}>"
            }

            is WireType.Named -> {
                name + typeArguments(arguments.map { it.kotlin() })
            }

            is WireType.TypeParameter -> {
                name
            }
        }
    return if (nullable) "$base?" else base
}

internal fun WireType.kotlinImports(): Set<String> =
    when (this) {
        is WireType.Scalar -> {
            when (kind) {
                ScalarKind.UUID -> setOf("import kotlin.uuid.Uuid")
                ScalarKind.DATE -> setOf("import kotlinx.datetime.LocalDate")
                ScalarKind.DATE_TIME -> setOf("import kotlin.time.Instant")
                else -> emptySet()
            }
        }

        is WireType.ListOf -> {
            element.kotlinImports()
        }

        is WireType.MapOf -> {
            key.kotlinImports() + value.kotlinImports()
        }

        is WireType.Named -> {
            arguments.flatMap { it.kotlinImports() }.toSet()
        }

        is WireType.TypeParameter -> {
            emptySet()
        }
    }

private fun typeArguments(arguments: List<String>): String =
    if (arguments.isEmpty()) "" else arguments.joinToString(", ", "<", ">")

internal fun Body.kotlin(): String =
    when (this) {
        Body.None -> "Unit"
        Body.Text, Body.Redirect -> "String"
        Body.Binary -> "ByteArrayInputStream"
        is Body.Json -> type.kotlin()
    }

internal fun Body.kotlinImports(): Set<String> =
    when (this) {
        Body.Binary -> setOf("import java.io.ByteArrayInputStream")
        is Body.Json -> type.kotlinImports()
        else -> emptySet()
    }

internal fun Declaration.kotlinFile(packageName: String): String =
    buildString {
        append("package $packageName\n\n")
        when (this@kotlinFile) {
            is EnumDeclaration -> append("enum class $name {\n${values.joinToString(",\n") { "  $it" }}\n}")
            is ObjectDeclaration -> append(dataClass())
        }
    }

// A missing key reads as Patch.Absent through the default, and EncodeDefault keeps Absent from being written
private fun ObjectDeclaration.dataClass(): String {
    val hasPatch = properties.any { it.optional }
    val imports =
        buildSet {
            add("import kotlinx.serialization.Serializable")
            if (hasPatch) {
                add("import ${Patch::class.qualifiedName}")
                add("import kotlinx.serialization.EncodeDefault")
                add("import kotlinx.serialization.ExperimentalSerializationApi")
            }
            properties.forEach { addAll(it.type.kotlinImports()) }
        }
    return buildString {
        append(imports.sorted().joinToString("\n"))
        append("\n\n")
        if (hasPatch) append("@OptIn(ExperimentalSerializationApi::class)\n")
        append("@Serializable\n")
        if (properties.isEmpty()) {
            append("class $name")
            return@buildString
        }
        append(
            "data class $name${typeArguments(
                typeParameters.map { if (it.nonNull) "${it.name} : Any" else it.name },
            )}(\n",
        )
        append(
            properties.sortedBy { it.name }.joinToString(",\n") { property ->
                when {
                    property.optional -> {
                        "    @EncodeDefault(EncodeDefault.Mode.NEVER) val ${property.name}: " +
                            "Patch<${property.type.kotlin()}> = Patch.Absent"
                    }

                    property.type.nullable -> {
                        "    val ${property.name}: ${property.type.kotlin()} = null"
                    }

                    else -> {
                        "    val ${property.name}: ${property.type.kotlin()}"
                    }
                }
            },
        )
        append("\n)")
    }
}
