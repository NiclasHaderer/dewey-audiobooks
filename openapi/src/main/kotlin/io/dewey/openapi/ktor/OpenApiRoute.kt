package io.dewey.openapi.ktor

import io.dewey.openapi.common.InternalAPI
import io.dewey.openapi.common.findAnnotationsFirstUp
import io.dewey.openapi.common.fullPath
import io.dewey.openapi.common.parent
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.routing.Route
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.typeOf

class OpenApiRoute(
    val method: HttpMethod,
    val basePath: String,
    val paramsType: KType,
    val requestType: KType,
    val responseType: KType,
) {
    constructor(
        method: HttpMethod,
        route: Route,
        paramsType: KType,
        requestType: KType,
        responseType: KType,
    ) : this(method, route.fullPath(), paramsType, requestType, responseType)

    companion object {
        inline fun <reified PARAMS, reified BODY, reified RESPONSE> create(
            method: HttpMethod,
            route: Route,
        ): OpenApiRoute = OpenApiRoute(method, route, typeOf<PARAMS>(), typeOf<BODY>(), typeOf<RESPONSE>())
    }

    val paramsClass: KClass<*> get() = paramsType.classifier as KClass<*>

    val hasRequestBody: Boolean get() = requestType.classifier != Unit::class

    val hasResponseBody: Boolean get() = responseType.classifier != Unit::class

    @OptIn(InternalAPI::class)
    val resourcePath by lazy {
        var resourcePath = ""
        var resourceClass: KClass<*>? = paramsClass
        while (resourceClass != null) {
            // Init has already checked that every class is decorated as a resource
            val resource = resourceClass.findAnnotation<Resource>()!!
            if (resource.path.isNotBlank()) {
                resourcePath =
                    if (resourcePath.isNotBlank()) {
                        "${resource.path}/$resourcePath"
                    } else {
                        resource.path
                    }
            }
            resourceClass = resourceClass.parent
        }
        resourcePath.ifBlank { null }
    }

    private val operation by lazy {
        paramsClass.findAnnotations<Summary>().firstOrNull { it.method == this.method.value }
    }

    val description by lazy {
        operation?.description?.ifBlank { null } ?: paramsClass.findAnnotation<Description>()?.description
    }

    val command by lazy { operation?.command?.ifBlank { null } }

    val inCli by lazy { operation?.cli ?: true }

    val summary by lazy { operation?.summary }

    // The guard the resource declares for this method, else the nearest one declared for every method
    @OptIn(InternalAPI::class)
    val secured by lazy {
        if (paramsClass.findAnnotation<NotSecured>() != null) return@lazy null
        paramsClass.findAnnotations<Secured>().firstOrNull { it.method.equals(method.value, ignoreCase = true) }
            ?: generateSequence(paramsClass) { it.parent }.firstNotNullOfOrNull { resource ->
                resource.findAnnotations<Secured>().firstOrNull { it.method.isEmpty() }
            }
    }

    val responseStatusCode by lazy {
        val declared = operation?.status?.takeIf { it != Summary.INFER_STATUS }
        when {
            declared != null -> HttpStatusCode.fromValue(declared)
            !hasResponseBody -> HttpStatusCode.NoContent
            else -> HttpStatusCode.OK
        }
    }

    val fullPath by lazy {
        var fullPath = basePath
        if (resourcePath != null) {
            fullPath += "/$resourcePath"
        }
        fullPath.replace("/+".toRegex(), "/")
    }

    val tags by lazy { paramsClass.findAnnotationsFirstUp<Tagged>().map { it.name } }

    val tagDescription by lazy {
        paramsClass.findAnnotationsFirstUp<Tagged>().firstNotNullOfOrNull { it.description.ifBlank { null } }
    }

    val tagGroup by lazy {
        paramsClass.findAnnotationsFirstUp<Tagged>().firstNotNullOfOrNull { it.group.ifBlank { null } }
    }

    val operationId by lazy {
        val segments =
            fullPath.split("/").filter { it.isNotBlank() }.joinToString("") { segment ->
                val isPathParam = segment.startsWith("{") && segment.endsWith("}")
                val words =
                    segment
                        .split("[^A-Za-z0-9]+".toRegex())
                        .filter { it.isNotBlank() }
                        .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
                if (isPathParam) "By$words" else words
            }
        method.value.lowercase() + segments
    }

    init {
        assertParamsHierarchy(paramsClass)
    }

    @OptIn(InternalAPI::class)
    private fun assertParamsHierarchy(paramsClazz: KClass<*>) {
        // There are three conditions under which the hierarchy is valid:
        // 1. Every class over params is decorated as @Resource
        paramsClazz.findAnnotation<Resource>()
            ?: throw IllegalStateException("Class ${paramsClazz.qualifiedName} is not decorated as a resource")

        val parent = paramsClazz.parent ?: return

        // 2. Every class has a field which references the parent class name, because otherwise the
        // ktor routing will not work as one would think
        val hasDeclaredParent = paramsClazz.declaredMemberProperties.any { it.returnType.classifier == parent }
        if (!hasDeclaredParent) {
            throw IllegalStateException(
                "Class ${paramsClazz.qualifiedName} has no property of type ${parent.qualifiedName}." +
                    "You have to create an additional property with the parent class as type",
            )
        }
        // 3. Every parent fulfills requirements 1 and 2
        assertParamsHierarchy(parent)
    }
}
