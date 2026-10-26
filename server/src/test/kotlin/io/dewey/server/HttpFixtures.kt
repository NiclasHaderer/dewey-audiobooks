package io.dewey.server

import io.dewey.client.models.DeweyLoginUser
import io.dewey.client.models.DeweyModifyPermissions
import io.dewey.client.models.DeweyRegisterUser
import io.dewey.client.models.LibraryPermissionLevel
import io.dewey.client.models.UpdateLibraryPermissions
import io.dewey.client.models.UpdateUserPermissions
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

const val TEST_PASSWORD = "hunter22"

fun deweyServer(block: suspend ApplicationTestBuilder.() -> Unit) =
    testApplication {
        application {
            plugins()
            routing()
        }
        register("admin")
        block()
    }

suspend fun ApplicationTestBuilder.register(username: String) {
    val response = api.registerUser(DeweyRegisterUser(password = TEST_PASSWORD, username = username))
    assertEquals(HttpStatusCode.Created, response.status, "could not register $username")
}

suspend fun ApplicationTestBuilder.login(username: String): String {
    val response = api.loginUser(DeweyLoginUser(password = TEST_PASSWORD, username = username))
    assertEquals(HttpStatusCode.OK, response.status, "could not log in $username")
    return response.body().accessToken
}

suspend fun ApplicationTestBuilder.registerWithAccess(
    username: String,
    libraryId: Uuid,
    level: LibraryPermissionLevel,
): String {
    register(username)
    grant(username, libraryId, level)
    return login(username)
}

// The endpoint replaces the whole permission set, so grant and revoke read the current one first
suspend fun ApplicationTestBuilder.setLibraries(
    username: String,
    libraries: List<UpdateLibraryPermissions>,
) {
    val admin = bearer(login("admin"))
    val response =
        api.updatePermissions(
            userId(username),
            DeweyModifyPermissions(UpdateUserPermissions(isAdmin = false, libraries = libraries)),
            admin,
        )
    assertEquals(HttpStatusCode.OK, response.status, "could not set permissions for $username")
}

suspend fun ApplicationTestBuilder.grant(
    username: String,
    libraryId: Uuid,
    level: LibraryPermissionLevel,
) = setLibraries(username, currentLibraries(username) + UpdateLibraryPermissions(libraryId, level))

suspend fun ApplicationTestBuilder.revoke(
    username: String,
    libraryId: Uuid,
) = setLibraries(username, currentLibraries(username).filterNot { it.id == libraryId })

private suspend fun ApplicationTestBuilder.currentLibraries(username: String) =
    user(username).permissions.libraries.map { UpdateLibraryPermissions(it.id, it.permissions) }

suspend fun ApplicationTestBuilder.userId(username: String): Uuid = user(username).id

private suspend fun ApplicationTestBuilder.user(username: String) =
    api.listUsers(bearer(login("admin"))).body().single { it.username == username }
