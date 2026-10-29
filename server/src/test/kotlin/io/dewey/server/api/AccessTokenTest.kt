package io.dewey.server.api

import com.auth0.jwt.JWT
import io.dewey.client.models.AccessTokenLibrary
import io.dewey.client.models.AccessTokenPermissions
import io.dewey.client.models.BookUpdate
import io.dewey.client.models.CreateAccessToken
import io.dewey.client.models.DeweyChangePassword
import io.dewey.client.models.DeweyLoginUser
import io.dewey.client.models.DeweyModifyPermissions
import io.dewey.client.models.DeweyRenameUser
import io.dewey.client.models.LibraryPermissionLevel
import io.dewey.client.models.PartialUpdateLibrary
import io.dewey.client.models.PasswordConfirmation
import io.dewey.client.models.UpdateAccessToken
import io.dewey.client.models.UpdateUserPermissions
import io.dewey.openapi.common.Patch
import io.dewey.server.DeweyTest
import io.dewey.server.TEST_PASSWORD
import io.dewey.server.api
import io.dewey.server.bearer
import io.dewey.server.deweyServer
import io.dewey.server.login
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.register
import io.dewey.server.registerWithAccess
import io.dewey.server.revoke
import io.dewey.server.userId
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

class AccessTokenTest : DeweyTest() {
    private class Token(
        val headers: Headers,
        val id: Uuid,
    )

    private suspend fun ApplicationTestBuilder.token(
        session: Headers,
        isAdmin: Boolean = true,
        libraries: List<AccessTokenLibrary>? = null,
    ): Token {
        val response =
            api.createAccessToken(
                CreateAccessToken(
                    name = "cli",
                    permissions = AccessTokenPermissions(isAdmin, libraries),
                    password = TEST_PASSWORD,
                ),
                session,
            )
        assertEquals(HttpStatusCode.Created, response.status, "could not create the token")
        val created = response.body()
        return Token(bearer(created.token), created.accessToken.id)
    }

    @Test
    fun `a token acts as its user`() =
        deweyServer {
            val libId = newLibrary("lib")
            val token = token(bearer(login("admin")))

            assertEquals(HttpStatusCode.OK, api.getLibrary(libId, token.headers).status)
            assertEquals(HttpStatusCode.OK, api.getCurrentUser(token.headers).status)
        }

    @Test
    fun `an admin token administers libraries and lists accounts but changes none`() =
        deweyServer {
            val libId = newLibrary("lib")
            register("listener")
            val adminId = userId("admin")
            val listenerId = userId("listener")
            val token = token(bearer(login("admin")))
            val permissions = DeweyModifyPermissions(UpdateUserPermissions(isAdmin = true, libraries = emptyList()))

            assertEquals(
                HttpStatusCode.OK,
                api.updateLibrary(libId, PartialUpdateLibrary(name = Patch.Set("Renamed")), token.headers).status,
            )
            assertEquals(HttpStatusCode.OK, api.getUser(adminId, token.headers).status)
            assertEquals(HttpStatusCode.OK, api.getUser(listenerId, token.headers).status)
            assertEquals(HttpStatusCode.OK, api.listUsers(token.headers).status)
            assertEquals(HttpStatusCode.Forbidden, api.updatePermissions(listenerId, permissions, token.headers).status)
            assertEquals(
                HttpStatusCode.Forbidden,
                api.updateUsername(listenerId, DeweyRenameUser("x"), token.headers).status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                api.updateUsername(adminId, DeweyRenameUser("x"), token.headers).status,
            )
            assertEquals(HttpStatusCode.Forbidden, api.deleteUser(listenerId, token.headers).status)
            assertEquals(HttpStatusCode.Forbidden, api.deleteUser(adminId, token.headers).status)
        }

    @Test
    fun `the current user of a token has the permissions of the token`() =
        deweyServer {
            val libId = newLibrary("lib")
            newLibrary("other", folders = listOf("/media/other"))
            val token =
                token(
                    bearer(login("admin")),
                    isAdmin = false,
                    libraries = listOf(AccessTokenLibrary(libId, LibraryPermissionLevel.READONLY)),
                )

            val permissions = api.getCurrentUser(token.headers).body().permissions

            assertEquals(false, permissions.isAdmin)
            assertEquals(
                listOf(libId to LibraryPermissionLevel.READONLY),
                permissions.libraries.map {
                    it.id to
                        it.permissions
                },
            )
        }

    @Test
    fun `a token without admin rights is no admin, even for an admin`() =
        deweyServer {
            register("listener")
            val listenerId = userId("listener")
            val token = token(bearer(login("admin")), isAdmin = false)

            assertEquals(HttpStatusCode.Unauthorized, api.listUsers(token.headers).status)
            assertEquals(HttpStatusCode.Forbidden, api.getUser(listenerId, token.headers).status)
        }

    @Test
    fun `a read only token cannot write a library its user can write`() =
        deweyServer {
            val libId = newLibrary("lib")
            val bookId = newBook("Dune", libId)
            val token =
                token(
                    bearer(login("admin")),
                    libraries = listOf(AccessTokenLibrary(libId, LibraryPermissionLevel.READONLY)),
                )

            assertEquals(HttpStatusCode.OK, api.getBook(bookId, libId, token.headers).status)
            assertEquals(
                HttpStatusCode.Forbidden,
                api.updateBook(bookId, libId, BookUpdate(title = Patch.Set("Renamed")), token.headers).status,
            )
        }

    @Test
    fun `editing a token changes what a copy already in use may do`() =
        deweyServer {
            val libId = newLibrary("lib")
            val bookId = newBook("Dune", libId)
            val session = bearer(login("admin"))
            val token = token(session, isAdmin = false)
            val readOnly =
                AccessTokenPermissions(false, listOf(AccessTokenLibrary(libId, LibraryPermissionLevel.READONLY)))

            val updated =
                api.updateAccessToken(
                    token.id,
                    UpdateAccessToken(name = "renamed", permissions = readOnly, password = TEST_PASSWORD),
                    session,
                )

            assertEquals(HttpStatusCode.OK, updated.status)
            assertEquals("renamed", updated.body().name)
            assertEquals(
                HttpStatusCode.Forbidden,
                api.updateBook(bookId, libId, BookUpdate(title = Patch.Set("Renamed")), token.headers).status,
            )
        }

    @Test
    fun `editing a token takes the password of a login of its user`() =
        deweyServer {
            val libId = newLibrary("lib")
            val session = bearer(login("admin"))
            val token = token(session, isAdmin = false)
            val other = bearer(registerWithAccess("listener", libId, LibraryPermissionLevel.READ_WRITE))
            val wider = AccessTokenPermissions(true, null)

            val wrong = api.updateAccessToken(
                token.id,
                UpdateAccessToken(name = "cli", permissions = wider, password = "wrong password"),
                session,
            )
            val byToken = api.updateAccessToken(
                token.id,
                UpdateAccessToken(name = "cli", permissions = wider, password = TEST_PASSWORD),
                token.headers,
            )
            val byOther = api.updateAccessToken(
                token.id,
                UpdateAccessToken(name = "cli", permissions = wider, password = TEST_PASSWORD),
                other,
            )

            assertEquals(HttpStatusCode.BadRequest, wrong.status)
            assertEquals(HttpStatusCode.Forbidden, byToken.status)
            assertEquals(HttpStatusCode.NotFound, byOther.status)
            assertEquals(
                false,
                api
                    .listAccessTokens(session)
                    .body()
                    .single()
                    .permissions.isAdmin,
            )
        }

    @Test
    fun `a wrong password revokes nothing`() =
        deweyServer {
            val session = bearer(login("admin"))
            val token = token(session)

            val revoked = api.revokeAccessToken(token.id, PasswordConfirmation("wrong password"), session)

            assertEquals(HttpStatusCode.BadRequest, revoked.status)
            assertEquals(HttpStatusCode.OK, api.listLibraries(token.headers).status)
        }

    @Test
    fun `a token limited to one library cannot reach another`() =
        deweyServer {
            val allowed = newLibrary("allowed", folders = listOf("/media/allowed"))
            val other = newLibrary("other", folders = listOf("/media/other"))
            val token =
                token(
                    bearer(login("admin")),
                    libraries = listOf(AccessTokenLibrary(allowed, LibraryPermissionLevel.READ_WRITE)),
                )

            assertEquals(HttpStatusCode.OK, api.getLibrary(allowed, token.headers).status)
            assertEquals(HttpStatusCode.NotFound, api.getLibrary(other, token.headers).status)
        }

    @Test
    fun `a library the user loses is lost to the token`() =
        deweyServer {
            val libId = newLibrary("lib")
            val session = bearer(registerWithAccess("listener", libId, LibraryPermissionLevel.READ_WRITE))
            val token = token(session, isAdmin = false)
            assertEquals(HttpStatusCode.OK, api.getLibrary(libId, token.headers).status)

            revoke("listener", libId)

            assertEquals(HttpStatusCode.NotFound, api.getLibrary(libId, token.headers).status)
        }

    @Test
    fun `a token asking for more than its user has gets no more`() =
        deweyServer {
            val libId = newLibrary("lib")
            val bookId = newBook("Dune", libId)
            val session = bearer(registerWithAccess("listener", libId, LibraryPermissionLevel.READONLY))
            val token =
                token(
                    session,
                    isAdmin = true,
                    libraries = listOf(AccessTokenLibrary(libId, LibraryPermissionLevel.READ_WRITE)),
                )

            assertEquals(HttpStatusCode.Unauthorized, api.listUsers(token.headers).status)
            assertEquals(
                HttpStatusCode.Forbidden,
                api.updateBook(bookId, libId, BookUpdate(title = Patch.Set("Renamed")), token.headers).status,
            )
        }

    @Test
    fun `a forged and a revoked token are refused`() =
        deweyServer {
            val session = bearer(login("admin"))
            val revoked = token(session)
            assertEquals(
                HttpStatusCode.NoContent,
                api.revokeAccessToken(revoked.id, PasswordConfirmation(TEST_PASSWORD), session).status,
            )

            assertEquals(HttpStatusCode.Unauthorized, api.listLibraries(bearer("not-a-token")).status)
            assertEquals(HttpStatusCode.Unauthorized, api.listLibraries(revoked.headers).status)
        }

    @Test
    fun `a token is a JWT of its own type that names its row and carries its expiry`() =
        deweyServer {
            val expiresAt = Clock.System.now() + 30.days
            val response =
                api.createAccessToken(
                    CreateAccessToken(
                        name = "cli",
                        expiresAt = expiresAt,
                        permissions = AccessTokenPermissions(isAdmin = false, libraries = null),
                        password = TEST_PASSWORD,
                    ),
                    bearer(login("admin")),
                )
            val created = response.body()

            val jwt = JWT.decode(created.token)

            assertEquals("access-token", jwt.getClaim("type").asString())
            assertEquals(created.accessToken.id.toString(), jwt.getClaim("tid").asString())
            assertEquals(expiresAt.epochSeconds, jwt.expiresAtAsInstant.epochSecond)
        }

    @Test
    fun `changing the password revokes every token`() =
        deweyServer {
            val session = bearer(login("admin"))
            val token = token(session)

            val changed =
                api.updatePassword(userId("admin"), DeweyChangePassword(TEST_PASSWORD, "another password"), session)

            assertEquals(HttpStatusCode.NoContent, changed.status)
            assertEquals(HttpStatusCode.Unauthorized, api.listLibraries(token.headers).status)
            val newSession = api.loginUser(DeweyLoginUser(password = "another password", username = "admin")).body()
            assertEquals(emptyList(), api.listAccessTokens(bearer(newSession.accessToken)).body())
        }

    @Test
    fun `a token cannot change the password or create tokens`() =
        deweyServer {
            val token = token(bearer(login("admin")))
            val adminId = userId("admin")

            val password =
                api.updatePassword(adminId, DeweyChangePassword(TEST_PASSWORD, "another password"), token.headers)
            val created =
                api.createAccessToken(
                    CreateAccessToken(
                        name = "child",
                        permissions = AccessTokenPermissions(true, null),
                        password = TEST_PASSWORD,
                    ),
                    token.headers,
                )

            assertEquals(HttpStatusCode.Forbidden, password.status)
            assertEquals(HttpStatusCode.Forbidden, created.status)
        }

    @Test
    fun `creating a token takes the password and a wrong one creates nothing`() =
        deweyServer {
            val session = bearer(login("admin"))

            val created =
                api.createAccessToken(
                    CreateAccessToken(
                        name = "cli",
                        permissions = AccessTokenPermissions(true, null),
                        password = "wrong password",
                    ),
                    session,
                )

            assertEquals(HttpStatusCode.BadRequest, created.status)
            assertEquals(emptyList(), api.listAccessTokens(session).body())
        }

    @Test
    fun `a token is shown again for the password`() =
        deweyServer {
            val session = bearer(login("admin"))
            val token = token(session)

            val wrong = api.revealAccessToken(token.id, PasswordConfirmation("wrong password"), session)
            val revealed = api.revealAccessToken(token.id, PasswordConfirmation(TEST_PASSWORD), session)

            assertEquals(HttpStatusCode.BadRequest, wrong.status)
            assertEquals(HttpStatusCode.OK, revealed.status)
            val shown = JWT.decode(revealed.body().token)
            assertEquals(token.id.toString(), shown.getClaim("tid").asString())
            assertEquals(HttpStatusCode.OK, api.listLibraries(bearer(revealed.body().token)).status)
        }

    @Test
    fun `only a login of its user shows a token`() =
        deweyServer {
            val libId = newLibrary("lib")
            val token = token(bearer(login("admin")))
            val other = bearer(registerWithAccess("listener", libId, LibraryPermissionLevel.READ_WRITE))

            val byToken = api.revealAccessToken(token.id, PasswordConfirmation(TEST_PASSWORD), token.headers)
            val byOther = api.revealAccessToken(token.id, PasswordConfirmation(TEST_PASSWORD), other)

            assertEquals(HttpStatusCode.Forbidden, byToken.status)
            assertEquals(HttpStatusCode.NotFound, byOther.status)
        }

    @Test
    fun `a token lists tokens but revokes none`() =
        deweyServer {
            val session = bearer(login("admin"))
            val first = token(session)
            val second = token(session)

            val listed = api.listAccessTokens(first.headers).body()
            assertEquals(listOf(first.id, second.id), listed.map { it.id })
            assertEquals(
                HttpStatusCode.Forbidden,
                api.revokeAccessToken(second.id, PasswordConfirmation(TEST_PASSWORD), first.headers).status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                api.revokeAccessToken(first.id, PasswordConfirmation(TEST_PASSWORD), first.headers).status,
            )
            assertEquals(HttpStatusCode.OK, api.listLibraries(first.headers).status)
        }

    @Test
    fun `nobody sees or revokes the tokens of another user`() =
        deweyServer {
            val libId = newLibrary("lib")
            val adminToken = token(bearer(login("admin")))
            val other = bearer(registerWithAccess("listener", libId, LibraryPermissionLevel.READ_WRITE))

            assertEquals(emptyList(), api.listAccessTokens(other).body())
            assertEquals(
                HttpStatusCode.NotFound,
                api.revokeAccessToken(adminToken.id, PasswordConfirmation(TEST_PASSWORD), other).status,
            )
            assertEquals(HttpStatusCode.OK, api.listLibraries(adminToken.headers).status)
        }
}
