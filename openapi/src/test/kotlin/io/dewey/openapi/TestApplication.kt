package io.dewey.openapi

import io.dewey.openapi.common.Patch
import io.dewey.openapi.ktor.Summary
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.plugins.OpenAPIRouting
import io.dewey.openapi.ktor.post
import io.dewey.openapi.ktor.webSocket
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.dataconversion.DataConversion
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.datetime.LocalDate
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

interface Something {
    val name: String
}

class ListRoute(
    override val name: String,
    val someParam: List<Uuid>,
) : Something

class MapRoute(
    val name: Boolean,
    val someParam: Map<String, Uuid>,
    val note: Patch<String?>,
    val label: Patch<String>,
    val lookup: Patch<HashMap<String, Uuid>?>,
    val hint: String?,
)

class SetRoute(
    val name: Float,
    val someParam: Set<Uuid>,
)

class GenericRoute<T>(
    val name: Pair<Any, Uuid>,
    val someParam: T,
)

class GenericRoute2<T, U>(
    val name: String,
    val someParam: T,
    val someParam2: U,
)

class GenericRoute3<T>(
    val name: String,
    val someParam: Map<String, T>,
    val someParam2: GenericRoute2<T, String>,
)

fun Application.testRoutes() {
    install(Resources)
    install(DataConversion)
    install(ContentNegotiation)
    install(WebSockets)
    install(OpenAPIRouting) {
        info { version = "1.2.3" }
    }

    routing {
        get<Routes.V1, ListRoute> { TODO() }
        get<Routes.V2, MapRoute> { TODO() }
        get<Routes.V3, SetRoute> { TODO() }
        get<Routes.V4, GenericRoute<Uuid>> { TODO() }
        post<Routes.V5, GenericRoute2<LocalDate, Uuid>, GenericRoute3<Instant>> { _, _ -> TODO() }
        webSocket<Routes.Listen, Unit, ListRoute> { TODO() }
    }
}
