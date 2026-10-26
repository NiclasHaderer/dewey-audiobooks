package io.dewey.openapi.client.common

import io.dewey.openapi.ktor.OpenApiRoute
import java.io.File
import java.nio.file.Path

abstract class ClientGenerator(
    private val dist: Path,
    fileWriter: ((File, String) -> Unit)?,
    private val cleanDistPackage: Boolean,
) {
    private val fileWriter =
        fileWriter
            ?: { file, content ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }

    protected abstract val routes: List<OpenApiRoute>

    protected abstract fun generateClient(): List<ClientPart>

    fun safeClient() {
        val client = generateClient()
        if (cleanDistPackage) {
            // An empty dist resolves to the current working directory, so cleaning
            // it would wipe whatever the process happens to run in.
            require(dist.toString().isNotBlank()) { "Refusing to clean an empty dist path" }
            dist.toFile().deleteRecursively()
        }
        client.forEach {
            val destination = dist.resolve(it.path).toFile()
            fileWriter(destination, it.content)
        }
    }

    protected fun getRouteName(route: OpenApiRoute): String? {
        val summaryString = route.summary ?: return null
        return summaryString
            .split(" ")
            .mapIndexed { index, word ->
                word.replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase()
                    } else if (index == 0) {
                        it.lowercase()
                    } else {
                        it.toString()
                    }
                }
            }.joinToString("")
            .replace(Regex("[^A-Za-z0-9]"), "")
    }
}

/** A CamelCase name in lower case with [separator] between its words, keeping acronyms such as JWK together. */
internal fun splitWords(
    name: String,
    separator: String,
): String =
    name
        .replace(Regex("([a-z0-9])([A-Z])"), "$1$separator$2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1$separator$2")
        .lowercase()
