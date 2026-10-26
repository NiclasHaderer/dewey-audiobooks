package io.dewey.client

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.util.reflect.TypeInfo
import io.ktor.websocket.close
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ApiSocket<SEND, RECEIVE>(
    val session: DefaultClientWebSocketSession,
    private val sendType: TypeInfo,
    private val receiveType: TypeInfo,
) {
    suspend fun send(message: SEND) = session.sendSerialized(message, sendType)

    // Completes once the server has closed the socket
    val incoming: Flow<RECEIVE> =
        flow {
            try {
                while (true) emit(session.receiveDeserialized(receiveType))
            } catch (_: ClosedReceiveChannelException) {
            }
        }

    suspend fun close() = session.close()
}
