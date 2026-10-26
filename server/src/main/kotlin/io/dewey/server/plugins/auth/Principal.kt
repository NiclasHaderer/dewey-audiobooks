package io.dewey.server.plugins.auth

import io.dewey.auth.utils.DeweyPrincipal
import io.dewey.models.LibraryPermissionLevel
import io.dewey.models.LibraryPermissions
import io.dewey.models.UserPermissions
import io.dewey.openapi.ktor.RouteParamsKey
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.database.tables.LibraryTable
import io.dewey.server.database.tables.LibraryUserTable
import io.ktor.http.HttpMethod
import io.ktor.server.auth.principal
import io.ktor.server.request.httpMethod
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class DeweyPrincipalImpl(
    override val userId: Uuid,
) : DeweyPrincipal {
    val permissions: UserPermissions = resolveUserPermissions(userId)
}

fun resolveUserPermissions(userId: Uuid): UserPermissions =
    transaction {
        val user = userRow(userId) ?: throw ErrorResponse.notFound("User", userId)
        val permissions: List<LibraryPermissions> =
            if (user.admin) {
                LibraryTable.selectAll().map {
                    LibraryPermissions(
                        id = it[LibraryTable.id].value,
                        permissions = LibraryPermissionLevel.READ_WRITE,
                        name = it[LibraryTable.name],
                    )
                }
            } else {
                (LibraryUserTable innerJoin LibraryTable)
                    .select(LibraryTable.id, LibraryTable.name, LibraryUserTable.permissions)
                    .where { LibraryUserTable.user eq userId }
                    .map {
                        LibraryPermissions(
                            id = it[LibraryTable.id].value,
                            permissions = it[LibraryUserTable.permissions],
                            name = it[LibraryTable.name],
                        )
                    }
            }
        UserPermissions(isAdmin = user.admin, libraries = permissions)
    }

fun RoutingContext.deweyPrincipal(): DeweyPrincipalImpl =
    deweyPrincipalOrNull()
        ?: throw ErrorResponse.internalError("Could not get principal. Route has to be guarded with one of the Guards")

fun RoutingContext.deweyPrincipalOrNull(): DeweyPrincipalImpl? = call.principal()

/**
 * Marks a route whose writes only ever touch the calling user's own state - listening progress and
 * the like. Such a route needs library membership, but not write permission on the library itself:
 * a READONLY member still gets to track what they listened to.
 */
interface UserScoped

fun RoutingContext.assertLibraryPermissions(vararg libraryIds: Uuid) {
    val principal = deweyPrincipal()

    val readonlyMethods = listOf(HttpMethod.Head, HttpMethod.Get, HttpMethod.Options)
    val isWrite =
        !readonlyMethods.contains(call.request.httpMethod) &&
            call.attributes.getOrNull(RouteParamsKey) !is UserScoped

    libraryIds.forEach { libId ->
        val library =
            principal.permissions.libraries.firstOrNull { allowedLib -> allowedLib.id == libId }
                ?: throw ErrorResponse.forbidden("You do not have access to library $libId")

        if (isWrite && library.permissions != LibraryPermissionLevel.READ_WRITE) {
            throw ErrorResponse.forbidden("You only have read access to library $libId")
        }
    }
}
