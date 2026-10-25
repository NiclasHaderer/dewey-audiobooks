package io.dewey.openapi.client.kotlin

import io.dewey.openapi.client.common.ClientGenerator
import io.dewey.openapi.client.common.ClientPart
import io.dewey.openapi.common.getResourceContent
import io.dewey.openapi.ktor.OpenApiRoute
import io.dewey.openapi.ktor.plugins.OpenAPIConfigurationKey
import io.dewey.openapi.wire.WireModel
import io.ktor.server.application.Application
import java.io.File
import java.nio.file.Path

enum class KtErrorHandling {
    Result,
    Exception,
    Either,
}

class KotlinClientGenerator(
    // TODO  UserPermissionsModel (to many imports)
    override val routes: List<OpenApiRoute>,
    private val webSockets: List<OpenApiRoute>,
    private val packageName: String,
    private val apiClientName: String,
    private val apiVersion: String,
    private val abstract: Boolean,
    private val errorHandling: KtErrorHandling,
    dist: Path,
    fileWriter: ((File, String) -> Unit)?,
    cleanDistPackage: Boolean,
) : ClientGenerator(dist, fileWriter, cleanDistPackage) {
    private val staticFiles by lazy {
        listOf(
            "RequestRunner" to getResourceContent("/kotlin/RequestRunner.kt"),
            "ApiSocket" to getResourceContent("/kotlin/ApiSocket.kt"),
            "ApiError" to getResourceContent("/kotlin/ApiError.kt"),
            "RequestMetadata" to getResourceContent("/kotlin/RequestMetadata.kt"),
            "OpenApiHttpResponse" to getResourceContent("/kotlin/OpenApiHttpResponse.kt"),
            "SerializationHolder" to getResourceContent("/kotlin/SerializationHolder.kt"),
        )
    }
    private val clientImports = mutableSetOf<String>()
    private val model = WireModel()

    override fun generateClient(): List<ClientPart> =
        buildList {
            val clientFunctions =
                (routes.map { it to false } + webSockets.map { it to true })
                    .sortedBy { (route) -> getRouteName(route).orEmpty() }
                    .map { (route, webSocket) ->
                        KtClientFunction(
                            getRouteName = this@KotlinClientGenerator::getRouteName,
                            route = route,
                            clientImports = clientImports,
                            model = model,
                            errorHandling = errorHandling,
                            webSocket = webSocket,
                        )
                    }

            val base =
                buildString {
                    append("@file:Suppress(\"UnusedImport\")\n\n")
                    // Package
                    append("package $packageName\n\n")

                    // Imports
                    append("import io.ktor.client.*\n")
                    append("import io.ktor.http.*\n")
                    append("import io.ktor.client.call.*\n")
                    append("import io.ktor.client.plugins.*\n")
                    append("import io.ktor.util.reflect.*\n")

                    // Client imports
                    append(clientImports.joinToString("\n"))
                    append("\n")

                    // Optional error handling imports
                    if (errorHandling == KtErrorHandling.Either) {
                        append("import arrow.core.Either\n")
                        append("import java.io.IOException\n")
                    }

                    // Model imports
                    append("import $packageName.models.*\n")
                    append("\n\n")
                }

            add(
                ClientPart(
                    path = "$apiClientName.kt",
                    content =
                        buildString {
                            append(base)
                            // Class
                            append("interface $apiClientName {\n")
                            append("    val apiVersion: String\n")
                            append("        get() = \"$apiVersion\"\n\n")
                            append("${clientFunctions.map { it.content }.joinToString("\n\n")}\n")
                            append("}")
                        },
                ),
            )
            add(
                ClientPart(
                    path = "${apiClientName}Impl.kt",
                    content =
                        buildString {
                            append(base)

                            // Class
                            append("@Suppress(\"unused\")\n")
                            if (abstract) {
                                append("abstract class ${apiClientName}Impl(\n")
                            } else {
                                append("open class ${apiClientName}Impl(\n")
                            }
                            append("    baseUrl: Url\n")
                            append(") : RequestRunner(baseUrl), $apiClientName {\n")
                            append("${clientFunctions.map { it.contentImpl }.joinToString("\n\n")}\n")
                            if (errorHandling == KtErrorHandling.Either) {
                                val eitherFun = getResourceContent("/kotlin/Either.kt")
                                // Replace everything up to // -- Start and after // -- End
                                append(eitherFun.substringAfter("// -- Start").substringBefore("// -- End"))
                            }
                            append("}")
                        },
                ),
            )
            addAll(
                staticFiles.map { (name, content) ->
                    ClientPart(
                        path = "$name.kt",
                        content =
                            buildString {
                                append("package $packageName\n\n")
                                append(content)
                            },
                    )
                },
            )
            addAll(
                model.declarations.map {
                    ClientPart(path = "models/${it.name}.kt", content = it.kotlinFile("$packageName.models"))
                },
            )
        }
}

fun Application.generateKotlinClient(
    apiClientPackageName: String,
    apiClientName: String,
    savePath: Path,
    routes: List<OpenApiRoute>? = null,
    fileWriter: ((File, String) -> Unit)? = null,
    abstract: Boolean = true,
    cleanDistPackage: Boolean = true,
    errorHandling: KtErrorHandling = KtErrorHandling.Either,
) {
    val config = this.attributes[OpenAPIConfigurationKey]
    KotlinClientGenerator(
        routes = routes ?: config.routeCollector.values(),
        webSockets = config.routeCollector.webSockets,
        packageName = apiClientPackageName,
        dist = savePath,
        apiClientName = apiClientName,
        apiVersion = config.apiVersion,
        fileWriter = fileWriter,
        abstract = abstract,
        cleanDistPackage = cleanDistPackage,
        errorHandling = errorHandling,
    ).safeClient()
}

fun Application.generateKotlinClient(
    apiClientPackageName: String,
    apiClientName: String,
    savePath: String,
    routes: List<OpenApiRoute>? = null,
    fileWriter: ((File, String) -> Unit)? = null,
    abstract: Boolean = true,
    cleanDistPackage: Boolean = true,
    errorHandling: KtErrorHandling = KtErrorHandling.Either,
) = generateKotlinClient(
    apiClientPackageName = apiClientPackageName,
    savePath = Path.of(savePath),
    routes = routes,
    apiClientName = apiClientName,
    fileWriter = fileWriter,
    abstract = abstract,
    cleanDistPackage = cleanDistPackage,
    errorHandling = errorHandling,
)
