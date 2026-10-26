package io.dewey.openapi.client.typescript

import io.dewey.openapi.client.common.ClientGenerator
import io.dewey.openapi.client.common.ClientPart
import io.dewey.openapi.common.getResourceContent
import io.dewey.openapi.common.padLinesStart
import io.dewey.openapi.ktor.OpenApiRoute
import io.dewey.openapi.ktor.plugins.OpenAPIConfigurationKey
import io.dewey.openapi.wire.Body
import io.dewey.openapi.wire.EnumDeclaration
import io.dewey.openapi.wire.ObjectDeclaration
import io.dewey.openapi.wire.ParameterLocation
import io.dewey.openapi.wire.ScalarKind
import io.dewey.openapi.wire.TypeParameterDeclaration
import io.dewey.openapi.wire.WireModel
import io.dewey.openapi.wire.WireType
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.server.application.Application
import java.io.File
import java.nio.file.Path

private const val UUID_ALIAS = "type UUID = `\${string}-\${string}-\${string}-\${string}-\${string}`"
private const val EMPTY_ALIAS = "type Empty = \"\""

class TsClientGenerator(
    override val routes: List<OpenApiRoute>,
    private val webSockets: List<OpenApiRoute>,
    val apiFactoryName: String,
    private val apiVersion: String,
    dist: Path,
    fileWriter: ((File, String) -> Unit)?,
    cleanDistPackage: Boolean,
) : ClientGenerator(dist, fileWriter, cleanDistPackage) {
    private val log = logger {}
    private val model = WireModel()
    private val aliases = linkedMapOf<String, String>()
    private val clientFunctions = mutableListOf<Pair<String, String>>()
    private val clientUses = sortedSetOf<String>()
    private val requestRunner: String by lazy { getResourceContent("/typescript/client.ts") }

    init {
        generateApiClient()
    }

    private fun reference(
        type: WireType,
        uses: MutableSet<String>,
    ): String =
        when (type) {
            is WireType.Scalar -> {
                when (type.kind) {
                    ScalarKind.STRING, ScalarKind.CHAR, ScalarKind.DATE, ScalarKind.DATE_TIME -> "string"

                    ScalarKind.UUID -> alias("UUID", UUID_ALIAS, uses)

                    ScalarKind.BOOLEAN -> "boolean"

                    ScalarKind.BYTE, ScalarKind.SHORT, ScalarKind.INT, ScalarKind.LONG, ScalarKind.FLOAT,
                    ScalarKind.DOUBLE,
                    -> "number"
                }
            }

            is WireType.ListOf -> {
                "Array<${nullableReference(type.element, uses)}>"
            }

            is WireType.MapOf -> {
                val key = reference(type.key, uses) + if (type.key.nullable) " | undefined" else ""
                "Record<$key, ${nullableReference(type.value, uses)}>"
            }

            is WireType.Named -> {
                uses += type.name
                type.name + typeArguments(type.arguments.map { nullableReference(it, uses) })
            }

            is WireType.TypeParameter -> {
                type.name
            }
        }

    private fun typeParameter(parameter: TypeParameterDeclaration): String =
        if (parameter.nonNull) "${parameter.name} extends NonNullable<unknown>" else parameter.name

    private fun typeArguments(arguments: List<String>): String =
        if (arguments.isEmpty()) "" else arguments.joinToString(", ", "<", ">")

    private fun nullableReference(
        type: WireType,
        uses: MutableSet<String>,
    ): String = reference(type, uses) + if (type.nullable) " | null" else ""

    private fun reference(body: Body): String =
        when (body) {
            Body.None -> alias("Empty", EMPTY_ALIAS, clientUses)
            Body.Text, Body.Redirect -> "string"
            Body.Binary -> "Blob"
            is Body.Json -> nullableReference(body.type, clientUses)
        }

    private fun parser(body: Body): String =
        when (body) {
            Body.None, Body.Text, Body.Redirect -> "text"
            Body.Binary -> "blob"
            is Body.Json -> "json"
        }

    private fun alias(
        name: String,
        declaration: String,
        uses: MutableSet<String>,
    ): String {
        aliases.getOrPut(name) { declaration }
        uses += name
        return name
    }

    private fun urlParameters(route: OpenApiRoute): String? {
        val urlParams = model.parameters(route.paramsType)
        if (urlParams.isEmpty()) return null
        val types =
            urlParams.joinToString(
                ",",
            ) { "${it.name}${if (it.optional) "?" else ""}: ${nullableReference(it.type, clientUses)}" }
        return "{${urlParams.joinToString(", ") { it.name }}}: {$types}"
    }

    private fun getParameters(route: OpenApiRoute): String =
        buildString {
            urlParameters(route)?.let { append("$it, ") }
            if (route.hasRequestBody) append("body: ${reference(model.body(route.requestType))}, ")
            append("headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []")
        }

    private fun messageType(
        route: OpenApiRoute,
        incoming: Boolean,
    ): String {
        val type = if (incoming) route.responseType else route.requestType
        return if (type.classifier == Unit::class) "never" else reference(model.body(type))
    }

    private fun createURL(route: OpenApiRoute): String {
        val finalPath = route.fullPath.replace("{", "\${")
        val queryParameters = model.parameters(route.paramsType).filter { it.location == ParameterLocation.QUERY }
        return if (queryParameters.isEmpty()) {
            "`$finalPath`"
        } else {
            "_createUrl(`$finalPath`, {${queryParameters.joinToString(", ") { it.name }}})"
        }
    }

    private fun generateApiClient() {
        routes.forEach { route ->
            val routeName = getRouteName(route)
            if (routeName == null) {
                log.warn { "Route ${route.method}:${route.fullPath} has no summary" }
                return@forEach
            }
            val responseBody = model.body(route.responseType)

            val function =
                buildString {
                    append(
                        "$routeName: (${getParameters(
                            route,
                        )}): Promise<ApiResponse<${reference(responseBody)}>> => {\n",
                    )
                    append(
                        "  return _request(${createURL(
                            route,
                        )}, \"${route.method.value}\", \"${parser(responseBody)}\", ",
                    )
                    append("_mergeHeaders(defaultHeadersImpl, headers), ")
                    append(if (route.hasRequestBody) "body" else "undefined")
                    append(", [...defaultInterceptors, ...interceptors], executor, ${route.secured != null});\n")
                    append("}")
                }.padLinesStart(' ', 4)
            clientFunctions.add(routeName to function)
        }
        webSockets.forEach { route ->
            val routeName = getRouteName(route)
            if (routeName == null) {
                log.warn { "Websocket ${route.fullPath} has no summary" }
                return@forEach
            }
            val socket = "ApiSocket<${messageType(route, incoming = false)}, ${messageType(route, incoming = true)}>"
            clientFunctions.add(
                routeName to
                    buildString {
                        append("$routeName: (")
                        urlParameters(route)?.let { append("$it, ") }
                        append("interceptors: ApiSocketInterceptor[] = []): Promise<$socket> => {\n")
                        append("  return _socket(${createURL(route)}, ")
                        append(
                            "[...defaultSocketInterceptors, ...interceptors], socketExecutor, ${route.secured != null});\n",
                        )
                        append("}")
                    }.padLinesStart(' ', 4),
            )
        }
    }

    private class TypeDefinition(
        val code: String,
        val uses: Set<String> = emptySet(),
    )

    // Built after the client functions, which is what fills the model and the aliases
    private val typeDefinitions: Map<String, TypeDefinition> by lazy {
        val declarations =
            model.declarations.associate { declaration ->
                declaration.name to
                    when (declaration) {
                        is EnumDeclaration -> {
                            TypeDefinition(
                                "type ${declaration.name} = ${declaration.values.joinToString(" | ") { "'$it'" }}",
                            )
                        }

                        is ObjectDeclaration -> {
                            val uses = sortedSetOf<String>()
                            val code =
                                buildString {
                                    append(
                                        "interface ${declaration.name}${typeArguments(
                                            declaration.typeParameters.map(::typeParameter),
                                        )} {\n",
                                    )
                                    declaration.properties.sortedBy { it.name }.forEach { property ->
                                        append("  ${property.name}")
                                        if (property.optional) append("?")
                                        append(": ${nullableReference(property.type, uses)};\n")
                                    }
                                    append("}")
                                }
                            TypeDefinition(code, uses - declaration.name)
                        }
                    }
            }
        val clashes = aliases.keys intersect declarations.keys
        check(
            clashes.isEmpty(),
        ) { "${clashes.joinToString()} would replace the alias the TS client defines, rename it" }
        (aliases.mapValues { TypeDefinition(it.value) } + declarations).toSortedMap()
    }

    private fun createTypeImports(): String =
        buildString {
            append(
                "import {ApiCallData, ApiInterceptor, ApiResponse, ApiSocket, ApiSocketData, ApiSocketInterceptor, _request, _createUrl, _mergeHeaders, _socket} from \"./client\";\n",
            )
            append("import type {")
            append(clientUses.joinToString(", "))
            append("} from \"./models\";\n")
        }

    private fun getClientRequests(): String =
        buildString {
            append(createTypeImports())
            append("\n")
            append("export const $apiFactoryName = (\n")
            append("  defaultHeaders: HeadersInit = {},\n")
            append("  defaultInterceptors: ApiInterceptor[] = [],\n")
            append(
                "  executor = (callData: ApiCallData) => fetch(callData.route, {method: callData.method, headers: callData.headers, body: callData.bodySerializer(callData.body)}),\n",
            )
            append("  defaultSocketInterceptors: ApiSocketInterceptor[] = [],\n")
            append("  socketExecutor = (socketData: ApiSocketData) => new WebSocket(socketData.route)\n")
            append(") => {\n")
            append("  const defaultHeadersImpl = new Headers(defaultHeaders)\n")
            append("  return {\n")
            append("    apiVersion: \"$apiVersion\",\n")
            append(clientFunctions.sortedBy { it.first }.joinToString(",\n") { it.second })
            append("\n")
            append("  } as const;\n")
            append("}")
        }

    private fun modelFile(definition: TypeDefinition): String =
        buildString {
            definition.uses.forEach { append("import type {$it} from \"./$it\";\n") }
            if (definition.uses.isNotEmpty()) append("\n")
            append("export ${definition.code}\n")
        }

    private fun modelIndex(): String = typeDefinitions.keys.joinToString("") { "export * from \"./$it\";\n" }

    override fun generateClient(): List<ClientPart> {
        val clientRequests = getClientRequests()
        return listOf(
            ClientPart("client.ts", requestRunner),
            ClientPart("api-client.ts", clientRequests),
            ClientPart("models/index.ts", modelIndex()),
        ) + typeDefinitions.map { (name, definition) -> ClientPart("models/$name.ts", modelFile(definition)) }
    }
}

fun Application.generateTsClient(
    dist: Path,
    apiFactoryName: String = "createApi",
    routes: List<OpenApiRoute>? = null,
    fileWriter: ((File, String) -> Unit)? = null,
    cleanDistPackage: Boolean = true,
) {
    val config = this.attributes[OpenAPIConfigurationKey]
    TsClientGenerator(
        routes = routes ?: config.routeCollector.values(),
        webSockets = config.routeCollector.webSockets,
        apiVersion = config.apiVersion,
        dist = dist,
        fileWriter = fileWriter,
        apiFactoryName = apiFactoryName,
        cleanDistPackage = cleanDistPackage,
    ).safeClient()
}

fun Application.generateTsClient(
    dist: String,
    apiFactoryName: String = "createApi",
    routes: List<OpenApiRoute>? = null,
    fileWriter: ((File, String) -> Unit)? = null,
    cleanDistPackage: Boolean = true,
) = generateTsClient(
    dist = Path.of(dist),
    routes = routes,
    fileWriter = fileWriter,
    apiFactoryName = apiFactoryName,
    cleanDistPackage = cleanDistPackage,
)
