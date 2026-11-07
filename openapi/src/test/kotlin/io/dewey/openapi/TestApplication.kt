package io.dewey.openapi

import io.dewey.openapi.common.Patch
import io.dewey.openapi.ktor.Secured
import io.dewey.openapi.ktor.Summary
import io.dewey.openapi.ktor.Tagged
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.plugins.OpenAPIConfiguration
import io.dewey.openapi.ktor.plugins.OpenAPIConfigurationKey
import io.dewey.openapi.ktor.plugins.OpenAPIRouting
import io.dewey.openapi.ktor.post
import io.dewey.openapi.ktor.webSocket
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.dataconversion.DataConversion
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.swagger.v3.oas.models.OpenAPI
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Resource("{path}")
class Routes(
    val path: Uuid,
    val name: String,
    val someParam: List<Int>,
) {
    @Summary(summary = "V1", method = "GET")
    @Resource("V1")
    class V1(
        val parent: Routes,
    )

    @Summary(summary = "V2", method = "GET")
    @Resource("V2")
    class V2(
        val parent: Routes,
    )

    @Summary(summary = "V3", method = "GET")
    @Resource("V3")
    class V3(
        val parent: Routes,
    )

    @Summary(summary = "V4", method = "GET")
    @Resource("V4")
    class V4(
        val parent: Routes,
    )

    @Summary(summary = "V5", method = "POST")
    @Resource("V5")
    class V5(
        val parent: Routes,
    )

    @Summary(summary = "Listen", method = "GET")
    @Resource("listen")
    class Listen(
        val parent: Routes,
    )
}

@Summary("List shelves", method = "GET", description = "List the shelves, the one used most recently first. In time.")
@Tagged("Shelves", description = "Shelves of books", group = "Library")
@Resource("shelves")
class ShelvesRoute

@Summary("List cookies", method = "GET")
@Summary("Refresh cookie", method = "POST", cli = false)
@Resource("cookies")
class CookiesRoute

@Summary("List keys", method = "GET")
@Summary("Rotate keys", method = "POST")
@Secured("login", method = "POST")
@Resource("keys")
class KeysRoute

interface Something {
    val name: String
}

@Serializable
class ListRoute(
    override val name: String,
    val someParam: List<Uuid>,
) : Something

@Serializable
class MapRoute(
    val name: Boolean,
    val someParam: Map<String, Uuid>,
    val note: Patch<String?>,
    val label: Patch<String>,
    val lookup: Patch<HashMap<String, Uuid>?>,
    val hint: String?,
)

@Serializable
class SetRoute(
    val name: Float,
    val someParam: Set<Uuid>,
)

@Serializable
class GenericRoute<T : Any>(
    val name: Pair<String, Uuid>,
    val someParam: T,
)

@Serializable
class GenericRoute2<T, U>(
    val name: String,
    val someParam: T,
    val someParam2: U,
)

@Serializable
class GenericRoute3<T>(
    val name: String,
    val someParam: Map<String, T>,
    val someParam2: GenericRoute2<T, String>,
)

fun Application.openApi(configure: OpenAPIConfiguration.() -> Unit = {}) {
    install(Resources)
    install(DataConversion)
    install(ContentNegotiation)
    install(OpenAPIRouting) {
        info { version = "1.2.3" }
        configure()
    }
}

fun generatedFiles(generate: Application.(fileWriter: (File, String) -> Unit) -> Unit): Map<String, String> {
    val files = mutableMapOf<String, String>()
    testApplication { application { generate { file, content -> files[file.path] = content } } }
    return files
}

fun openApiSpec(routes: Application.() -> Unit): OpenAPI {
    lateinit var api: OpenAPI
    testApplication {
        application {
            routes()
            val config = attributes[OpenAPIConfigurationKey]
            config.addRoutesToSpec()
            api = config.schemaHolder.api
        }
    }
    return api
}

fun Application.testRoutes() {
    install(WebSockets)
    install(Authentication) { bearer("login") { authenticate { UserIdPrincipal(it.token) } } }
    openApi()

    routing {
        get<Routes.V1, ListRoute> { TODO() }
        get<Routes.V2, MapRoute> { TODO() }
        get<Routes.V3, SetRoute> { TODO() }
        get<Routes.V4, GenericRoute<Uuid>> { TODO() }
        post<Routes.V5, GenericRoute2<LocalDate, Uuid>, GenericRoute3<Instant>> { _, _ -> TODO() }
        webSocket<Routes.Listen, Unit, ListRoute> { TODO() }
        get<ShelvesRoute, List<String>> { TODO() }
        get<CookiesRoute, List<String>> { TODO() }
        post<CookiesRoute, Unit, Unit> { _, _ -> TODO() }
        get<KeysRoute, List<String>> { TODO() }
        post<KeysRoute, Unit, Unit> { _, _ -> TODO() }
    }
}
