package io.dewey.openapi.ktor

import io.dewey.openapi.ktor.plugins.OpenAPIConfigurationKey
import io.ktor.http.HttpMethod
import io.ktor.server.application.plugin
import io.ktor.server.auth.authenticate
import io.ktor.server.resources.Resources
import io.ktor.server.resources.resource
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.serializer

inline fun Route.guarded(
    route: OpenApiRoute,
    crossinline build: Route.() -> Unit,
) {
    val secured = route.secured
    if (secured != null) authenticate(secured.name) { build() } else build()
}

class TypedWebSocketSession<IN, OUT>(
    private val session: DefaultWebSocketServerSession,
    private val incomingType: TypeInfo,
    private val outgoingType: TypeInfo,
) : CoroutineScope by session {
    val call get() = session.call

    suspend fun send(message: OUT) = session.sendSerialized(message, outgoingType)

    // Completes once the client has closed the socket
    val incoming: Flow<IN> =
        flow {
            try {
                while (true) emit(session.receiveDeserialized(incomingType))
            } catch (_: ClosedReceiveChannelException) {
            }
        }
}

// OpenAPI cannot describe a websocket, so the route is guarded and typed like the others but left out of the spec.
// The client generators still learn about it. IN is what a client sends and OUT what it receives, Unit for nothing.
inline fun <reified PARAMS : Any, reified IN, reified OUT> Route.webSocket(
    noinline handler: suspend TypedWebSocketSession<IN, OUT>.(params: PARAMS) -> Unit,
) {
    val format = application.plugin(Resources).resourcesFormat
    val serializer = serializer<PARAMS>()
    val route = OpenApiRoute.create<PARAMS, IN, OUT>(HttpMethod.Get, this)
    application.attributes[OpenAPIConfigurationKey].routeCollector.addWebSocket(route)
    guarded(route) {
        resource<PARAMS> {
            webSocket {
                TypedWebSocketSession<IN, OUT>(this, typeInfo<IN>(), typeInfo<OUT>())
                    .handler(format.decodeFromParameters(serializer, call.parameters))
            }
        }
    }
}
