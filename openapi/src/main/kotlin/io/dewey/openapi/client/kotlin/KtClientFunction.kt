package io.dewey.openapi.client.kotlin

import io.dewey.openapi.ktor.OpenApiRoute
import io.dewey.openapi.wire.Parameter
import io.dewey.openapi.wire.ParameterLocation
import io.dewey.openapi.wire.WireModel
import io.github.oshai.kotlinlogging.KotlinLogging.logger

class KtClientFunction(
    private val getRouteName: (OpenApiRoute) -> String?,
    private val route: OpenApiRoute,
    private val clientImports: MutableSet<String>,
    private val model: WireModel,
    private val errorHandling: KtErrorHandling,
    private val webSocket: Boolean = false,
) {
    companion object {
        private val log = logger {}
    }

    private val requestBody = model.body(route.requestType)
    private val responseBody = model.body(route.responseType)
    private val parameters =
        model.parameters(route.paramsType)

    val contentImpl = run { generateContent(true) }
    val content = run { generateContent(false) }

    private fun getParameters(impl: Boolean) =
        buildList {
            fun withIfNotImpl(str: String) = if (impl) "" else str

            fun addParameter(param: Parameter) {
                clientImports.addAll(param.type.kotlinImports())
                val type = if (param.optional &&
                    !param.type.nullable
                ) {
                    "${param.type.kotlin()}?"
                } else {
                    param.type.kotlin()
                }
                val default = if (param.optional) withIfNotImpl(" = null") else ""
                add("${param.name}: $type$default, ")
            }

            val (optionalParams, requiredParams) = parameters.partition { it.optional }

            // Everything carrying a default has to come last, so the body sits between required and optional
            requiredParams.forEach(::addParameter)

            if (!webSocket && route.hasRequestBody) add("body: ${requestBody.kotlin()}, ")

            optionalParams.forEach(::addParameter)

            add("headers: Headers${withIfNotImpl("= Headers.Empty")},")

            if (webSocket) {
                add("onBeforeRequest: OnBeforeRequest<Unit>${withIfNotImpl(" = { _, _ -> }")}")
                return@buildList
            }

            add("onBeforeRequest: OnBeforeRequest<${requestBody.kotlin()}>${withIfNotImpl(" = { _, _ -> }")}, ")
            add(
                "onAfterRequest: OnAfterRequest<${requestBody.kotlin()}, ${responseBody.kotlin()}>" +
                    withIfNotImpl(" = { _, _ -> }"),
            )
        }

    private fun generateContent(impl: Boolean): String? {
        val routeName = getRouteName(route)
        if (routeName == null) {
            log.warn { "Route ${route.method}:${route.fullPath} has no summary" }
            return null
        }

        clientImports.addAll(responseBody.kotlinImports())
        clientImports.addAll(requestBody.kotlinImports())
        val request = requestBody.kotlin()
        val response = responseBody.kotlin()
        return buildString {
            append("    ${if (impl) "override" else ""} suspend fun $routeName(\n")
            getParameters(impl).forEach { append("        $it\n") }

            // A direction nothing travels in is Nothing, so that a client cannot send what the server does not read
            fun message(type: String) = if (type == "Unit") "Nothing" else type
            val functionRunner =
                when {
                    webSocket -> {
                        "= run" to "ApiSocket<${message(request)}, ${message(response)}>"
                    }

                    errorHandling == KtErrorHandling.Result -> {
                        "= runCatching" to
                            "Result<OpenApiHttpResponse<$response>>"
                    }

                    errorHandling == KtErrorHandling.Exception -> {
                        "= run" to "OpenApiHttpResponse<$response>"
                    }

                    else -> {
                        "= wrapInEither" to "Either<OpenApiHttpResponse<$response>, ApiError>"
                    }
                }
            append("    ): ${functionRunner.second}")
            if (!impl) {
                append("\n")
                return@buildString
            }
            append(" ${functionRunner.first} {\n")
            append("        ${if (webSocket) "openSocket" else "makeRequest"}(\n")
            append("            RequestMetadata(\n")
            // Path parameters are spelled {name} in the route, which is also their name in the generated
            // signature, so turning the braces into Kotlin interpolation fills them in.
            append("                path = \"${route.fullPath.replace("{", "\${")}\",\n")
            append("                method = HttpMethod(\"${route.method.value}\"),\n")
            append("                headers = headers,\n")
            val body = if (webSocket || !route.hasRequestBody) "Unit" else "body"
            append("                body = $body,\n")
            append("                shouldLogin = ${route.secured != null},\n")
            append("                securitySchema = \"${route.secured?.name}\",\n")
            val queryParameters = parameters.filter { it.location == ParameterLocation.QUERY }
            if (queryParameters.isNotEmpty()) {
                val entries = queryParameters.joinToString(", ") { "\"${it.name}\" to ${it.name}" }
                append("                queryParameters = mapOf($entries),\n")
            }
            append("            ),\n")
            append("            typeInfo<$request>(),\n")
            append("            typeInfo<$response>(),\n")
            if (webSocket) {
                append("            onBeforeRequest=onBeforeRequest\n")
            } else {
                append("            onBeforeRequest=onBeforeRequest,\n")
                append("            onAfterRequest=onAfterRequest\n")
            }
            append("        )")
            append("\n    }")
        }
    }
}
