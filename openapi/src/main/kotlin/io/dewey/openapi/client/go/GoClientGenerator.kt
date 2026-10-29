package io.dewey.openapi.client.go

import io.dewey.openapi.client.common.ClientGenerator
import io.dewey.openapi.client.common.ClientPart
import io.dewey.openapi.common.getResourceContent
import io.dewey.openapi.ktor.OpenApiRoute
import io.dewey.openapi.ktor.plugins.OpenAPIConfigurationKey
import io.dewey.openapi.wire.Body
import io.dewey.openapi.wire.Parameter
import io.dewey.openapi.wire.ParameterLocation
import io.dewey.openapi.wire.WireModel
import io.dewey.openapi.wire.WireType
import io.dewey.openapi.wire.withNullable
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.server.application.Application
import java.io.File
import java.nio.file.Path

/** The route as one generated client method sees it, which the CLI commands are generated from as well. */
class GoRoute(
    val route: OpenApiRoute,
    val functionName: String,
    model: WireModel,
) {
    val requestBody: Body = if (route.hasRequestBody) model.body(route.requestType) else Body.None
    val responseBody: Body = model.body(route.responseType)
    private val parameters = model.parameters(route.paramsType)

    // Outermost first, as the path spells them, so the library comes before the book in it
    val pathParameters: List<Parameter> =
        parameters
            .filter { it.location == ParameterLocation.PATH }
            .sortedBy { route.fullPath.indexOf("{${it.name}}") }
    val requiredQuery: List<Parameter> = parameters.filter { it.location == ParameterLocation.QUERY && !it.optional }
    val optionalQuery: List<Parameter> = parameters.filter { it.location == ParameterLocation.QUERY && it.optional }

    val optionsType: String? = if (optionalQuery.isEmpty()) null else "${functionName}Options"

    val returnType: String? =
        when (responseBody) {
            Body.None -> null
            Body.Text, Body.Redirect -> "string"
            Body.Binary -> "io.ReadCloser"
            is Body.Json -> responseBody.type.go(MODELS)
        }

    val bodyType: String? =
        when (requestBody) {
            Body.None -> null
            Body.Text, Body.Redirect -> "string"
            Body.Binary -> "io.Reader"
            is Body.Json -> requestBody.type.go(MODELS)
        }
}

// An optional parameter that is not nullable yet becomes a pointer, nil being "not given"
internal fun optionalWire(type: WireType): WireType =
    if (type is WireType.ListOf || type is WireType.MapOf || type.nullable) type else type.withNullable()

// How the client package refers to the models package below it
internal const val MODELS = "models."
private const val MODELS_DIRECTORY = "models"
private const val TYPES_FILE = "types.go"

// The Patch semantics the server's SerializationTest pins down, checked against the Go side of the wire
private const val TYPES_TEST_FILE = "types_test.go"

class GoClientGenerator(
    override val routes: List<OpenApiRoute>,
    private val webSockets: List<OpenApiRoute>,
    private val packageName: String,
    // The import path of the client package, the models package is imported below it
    private val importPath: String,
    dist: Path,
    fileWriter: ((File, String) -> Unit)?,
    cleanDistPackage: Boolean,
) : ClientGenerator(dist, fileWriter, cleanDistPackage) {
    private val log = logger {}
    val model = WireModel()

    val goRoutes: List<GoRoute> by lazy {
        webSockets.forEach { log.info { "Websocket ${it.fullPath} is not part of the Go client" } }
        routes
            .mapNotNull { route ->
                val name = getRouteName(route)
                if (name == null) {
                    log.warn { "Route ${route.method}:${route.fullPath} has no summary" }
                    null
                } else {
                    GoRoute(route, exported(name), model)
                }
            }.sortedBy { it.functionName }
            .also { all ->
                val duplicates = all.groupBy { it.functionName }.filterValues { it.size > 1 }.keys
                check(duplicates.isEmpty()) {
                    "Routes share the Go function name ${duplicates.joinToString()}, give them different summaries"
                }
            }
    }

    override fun generateClient(): List<ClientPart> {
        val client = clientFile()
        val modelFiles = model.declarations.associateBy { fileName(it.name) }
        val clashes =
            model.declarations.groupBy { fileName(it.name) }.filter { (file, models) ->
                models.size > 1 || file == TYPES_FILE
            }
        check(clashes.isEmpty()) {
            "${clashes.values.flatten().joinToString { it.name }} would be written to the same Go file, rename one"
        }
        val runtime = GENERATED_HEADER + "package $packageName\n\n" + getResourceContent("/go/runtime.go")
        val types = GENERATED_HEADER + "package $MODELS_DIRECTORY\n\n" + getResourceContent("/go/$TYPES_FILE")
        val typesTest = GENERATED_HEADER + "package $MODELS_DIRECTORY\n\n" + getResourceContent("/go/$TYPES_TEST_FILE")
        return listOf(
            ClientPart("runtime.go", runtime),
            ClientPart("client.go", client),
            ClientPart("$MODELS_DIRECTORY/$TYPES_FILE", types),
            ClientPart("$MODELS_DIRECTORY/$TYPES_TEST_FILE", typesTest),
        ) +
            modelFiles.map { (file, declaration) ->
                ClientPart("$MODELS_DIRECTORY/$file", declaration.goFile(MODELS_DIRECTORY))
            }
    }

    private fun clientFile(): String {
        val functions = goRoutes.map(::function)
        val imports =
            buildList {
                add("context")
                if (goRoutes.any { it.responseBody == Body.Binary || it.requestBody == Body.Binary }) add("io")
                if (goRoutes.any { it.requiredQuery.isNotEmpty() || it.optionsType != null }) add("net/url")
                val usesTime =
                    goRoutes.any { route ->
                        route.requestBody.usesTime() || route.responseBody.usesTime() ||
                            (route.pathParameters + route.requiredQuery + route.optionalQuery).any {
                                it.type.usesTime()
                            }
                    }
                if (usesTime) add("time")
                if (functions.any { MODELS in it }) add("$importPath/$MODELS_DIRECTORY")
            }
        return buildString {
            append(GENERATED_HEADER)
            append("package $packageName\n\n")
            append("import (\n")
            imports.forEach { append("\t\"$it\"\n") }
            append(")\n\n")
            append(functions.joinToString("\n"))
        }
    }

    private fun function(goRoute: GoRoute): String =
        buildString {
            val route = goRoute.route
            goRoute.optionsType?.let { options ->
                append("type $options struct {\n")
                goRoute.optionalQuery.forEach { parameter ->
                    append("\t${exported(parameter.name)} ${optionalType(parameter.type)}\n")
                }
                append("}\n\n")
            }

            val arguments =
                buildList {
                    add("ctx context.Context")
                    (goRoute.pathParameters + goRoute.requiredQuery).forEach {
                        add(
                            "${parameterName(it.name)} ${it.type.go(MODELS)}",
                        )
                    }
                    goRoute.bodyType?.let { add("body $it") }
                    goRoute.optionsType?.let { add("opts *$it") }
                }
            val result = goRoute.returnType?.let { "($it, error)" } ?: "error"
            append("func (c *Client) ${goRoute.functionName}(${arguments.joinToString(", ")}) $result {\n")

            val hasQuery = goRoute.requiredQuery.isNotEmpty() || goRoute.optionsType != null
            if (hasQuery) {
                append("\tquery := url.Values{}\n")
                goRoute.requiredQuery.forEach { append(addQuery(it.name, parameterName(it.name), it.type, "\t")) }
                if (goRoute.optionsType != null) {
                    append("\tif opts != nil {\n")
                    goRoute.optionalQuery.forEach { parameter ->
                        append(
                            addQuery(
                                parameter.name,
                                "opts.${exported(parameter.name)}",
                                optionalWire(parameter.type),
                                "\t\t",
                            ),
                        )
                    }
                    append("\t}\n")
                }
            }

            append("\tr := request{\n")
            append("\t\tmethod: \"${route.method.value}\",\n")
            append("\t\tpath: ${pathExpression(goRoute)},\n")
            if (hasQuery) append("\t\tquery: query,\n")
            if (goRoute.bodyType != null) append("\t\tbody: body,\n\t\thasBody: true,\n")
            append("\t\tsecured: ${route.secured != null},\n")
            append("\t}\n")

            when (val response = goRoute.responseBody) {
                Body.None -> {
                    append("\treturn c.json(ctx, r, nil)\n")
                }

                Body.Text, Body.Redirect -> {
                    append("\treturn c.text(ctx, r)\n")
                }

                Body.Binary -> {
                    append("\treturn c.stream(ctx, r)\n")
                }

                is Body.Json -> {
                    append("\tvar out ${response.type.go(MODELS)}\n")
                    append("\terr := c.json(ctx, r, &out)\n")
                    append("\treturn out, err\n")
                }
            }
            append("}\n")
        }

    private fun pathExpression(goRoute: GoRoute): String {
        val parts = mutableListOf<String>()
        var rest = goRoute.route.fullPath
        while (true) {
            val match = PATH_VARIABLE.find(rest) ?: break
            if (match.range.first > 0) parts += "\"${rest.substring(0, match.range.first)}\""
            parts += "pathValue(${parameterName(match.groupValues[1])})"
            rest = rest.substring(match.range.last + 1)
        }
        if (rest.isNotEmpty() || parts.isEmpty()) parts += "\"$rest\""
        return parts.joinToString(" + ")
    }

    private fun optionalType(type: WireType): String = optionalWire(type).go(MODELS)

    private fun addQuery(
        name: String,
        value: String,
        type: WireType,
        indent: String,
    ): String =
        when {
            type is WireType.ListOf -> {
                "${indent}for _, item := range $value {\n$indent\tquery.Add(\"$name\", queryValue(item))\n$indent}\n"
            }

            type.nullable -> {
                "${indent}if $value != nil {\n$indent\tquery.Set(\"$name\", queryValue(*$value))\n$indent}\n"
            }

            else -> {
                "${indent}query.Set(\"$name\", queryValue($value))\n"
            }
        }

    private companion object {
        val PATH_VARIABLE = "\\{(\\w+)}".toRegex()
    }
}

fun Application.goClientGenerator(
    packageName: String,
    importPath: String,
    savePath: Path,
    routes: List<OpenApiRoute>? = null,
    fileWriter: ((File, String) -> Unit)? = null,
    cleanDistPackage: Boolean = true,
): GoClientGenerator {
    val config = this.attributes[OpenAPIConfigurationKey]
    return GoClientGenerator(
        routes = routes ?: config.routeCollector.values(),
        webSockets = config.routeCollector.webSockets,
        packageName = packageName,
        importPath = importPath,
        dist = savePath,
        fileWriter = fileWriter,
        cleanDistPackage = cleanDistPackage,
    )
}

fun Application.generateGoClient(
    packageName: String,
    importPath: String,
    savePath: String,
    routes: List<OpenApiRoute>? = null,
    fileWriter: ((File, String) -> Unit)? = null,
    cleanDistPackage: Boolean = true,
) = goClientGenerator(packageName, importPath, Path.of(savePath), routes, fileWriter, cleanDistPackage).safeClient()
