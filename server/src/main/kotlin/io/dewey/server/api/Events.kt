package io.dewey.server.api

import io.dewey.models.ChangeEvent
import io.dewey.models.ChangeEventBatch
import io.dewey.models.ChangeType
import io.dewey.models.ChangedEntity
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.openapi.ktor.webSocket
import io.dewey.server.events.ChangeEvents
import io.dewey.server.plugins.auth.DeweyPrincipalImpl
import io.dewey.server.plugins.auth.resolveUserPermissions
import io.ktor.server.auth.principal
import io.ktor.server.routing.Routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

fun Routing.eventRouting() {
    val changeEvents by inject<ChangeEvents>()

    webSocket<Api.Events, Unit, ChangeEventBatch> {
        val userId =
            call.principal<DeweyPrincipalImpl>()?.userId
                ?: throw ErrorResponse.internalError("Could not get principal of the event socket")

        val forwarding =
            launch {
                var permissionVersion = changeEvents.permissionsVersion
                var readable = readableLibraries(userId)
                // Tells the client from when on no commit is missed, the handshake finishes earlier
                val listening = ChangeEventBatch(emptyList())
                changeEvents.listen { send(listening) }.collect { batch ->
                    val version = changeEvents.permissionsVersion
                    // An admin reads a library from the moment it exists, without anyone granting it
                    if (version != permissionVersion || batch.events.any { it.isNewLibrary }) {
                        permissionVersion = version
                        readable = readableLibraries(userId)
                    }
                    val events = batch.events.filter { it.libraryId in readable || it.isDeletedLibrary }
                    if (events.isNotEmpty() || batch.missedPreviousEvents) send(batch.copy(events = events))
                }
            }
        incoming.collect {}
        forwarding.cancel()
    }
}

private suspend fun readableLibraries(userId: Uuid): Set<Uuid> =
    withContext(Dispatchers.IO) { resolveUserPermissions(userId).libraries.mapTo(mutableSetOf()) { it.id } }

private val ChangeEvent.isNewLibrary get() = entity == ChangedEntity.LIBRARY && type == ChangeType.CREATED

// Sent to everyone: the permissions that would say who may know are deleted with the library
private val ChangeEvent.isDeletedLibrary get() = entity == ChangedEntity.LIBRARY && type == ChangeType.DELETED
