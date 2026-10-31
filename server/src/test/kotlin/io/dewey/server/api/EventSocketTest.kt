package io.dewey.server.api

import io.dewey.client.ApiSocket
import io.dewey.client.models.AccessTokenPermissions
import io.dewey.client.models.ChangeEvent
import io.dewey.client.models.ChangeEventBatch
import io.dewey.client.models.ChangeType
import io.dewey.client.models.ChangedEntity
import io.dewey.client.models.CreateAccessToken
import io.dewey.client.models.LibraryPermissionLevel
import io.dewey.models.FileScanner
import io.dewey.openapi.serializion.kotlin.OpenApiJson
import io.dewey.server.DeweyTest
import io.dewey.server.TEST_PASSWORD
import io.dewey.server.TestDeweyClient
import io.dewey.server.api
import io.dewey.server.bearer
import io.dewey.server.common.extensions.canonical
import io.dewey.server.config.DeweyConfig
import io.dewey.server.deweyServer
import io.dewey.server.file.scanner.LibraryImportPipeline
import io.dewey.server.grant
import io.dewey.server.login
import io.dewey.server.newBook
import io.dewey.server.newLibrary
import io.dewey.server.registerWithAccess
import io.dewey.server.repositories.AuthorRepository
import io.dewey.server.repositories.BookRepository
import io.dewey.server.revoke
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.koin.mp.KoinPlatform.getKoin
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.isDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class EventSocketTest : DeweyTest() {
    override fun configure(dataDir: Path) = DeweyConfig(dataDir = dataDir, settleMillis = 50)

    private suspend fun ApplicationTestBuilder.events(
        token: String?,
        block: suspend ApiSocket<Nothing, ChangeEventBatch>.() -> Unit,
    ) {
        val http =
            createClient {
                install(
                    WebSockets,
                ) { contentConverter = KotlinxWebsocketSerializationConverter(OpenApiJson) }
            }
        val socket = TestDeweyClient(http).listenToEvents(headers = token?.let(::bearer) ?: Headers.Empty)
        try {
            socket.block()
        } finally {
            socket.close()
        }
    }

    private suspend fun ApiSocket<Nothing, ChangeEventBatch>.nextBatch(): List<ChangeEvent> =
        withTimeout(5.seconds) { incoming.first() }.events

    private fun changeEvent(
        entity: ChangedEntity,
        type: ChangeType,
        id: Uuid,
        libraryId: Uuid,
    ): ChangeEvent = ChangeEvent(entity = entity, id = id, libraryId = libraryId, type = type)

    @Test
    fun `a commit reaches a connected client after the empty batch that says it is listening`() =
        deweyServer {
            val libId = newLibrary("lib")

            events(login("admin")) {
                assertEquals(emptyList(), nextBatch())

                val bookId = newBook("Dune", libId)

                assertEquals(newBookIn(libId, bookId), nextBatch())
            }
        }

    private fun newBookIn(
        libraryId: Uuid,
        bookId: Uuid,
    ) = listOf(
        changeEvent(ChangedEntity.BOOK, ChangeType.CREATED, bookId, libraryId),
        changeEvent(ChangedEntity.LIBRARY, ChangeType.UPDATED, libraryId, libraryId),
    )

    @Test
    fun `a client stops being told about a library once its access is revoked`() =
        deweyServer {
            val revoked = newLibrary("revoked")
            val kept = newLibrary("kept")
            val token = registerWithAccess("listener", revoked, LibraryPermissionLevel.READONLY)
            grant("listener", kept, LibraryPermissionLevel.READONLY)

            events(token) {
                assertEquals(emptyList(), nextBatch())
                revoke("listener", revoked)

                newBook("Hidden", revoked)
                val bookId = newBook("Dune", kept)

                assertEquals(newBookIn(kept, bookId), nextBatch())
            }
        }

    @Test
    fun `a client is only told about the libraries it can read`() =
        deweyServer {
            val readable = newLibrary("readable")
            val other = newLibrary("other")
            val token = registerWithAccess("listener", readable, LibraryPermissionLevel.READONLY)

            events(token) {
                assertEquals(emptyList(), nextBatch())

                newBook("Hidden", other)
                val bookId = newBook("Dune", readable)

                assertEquals(newBookIn(readable, bookId), nextBatch())
            }
        }

    @Test
    fun `an access token is turned away`() =
        deweyServer {
            val all = AccessTokenPermissions(isAdmin = true, libraries = null)
            val created =
                api.createAccessToken(
                    CreateAccessToken(name = "events", permissions = all, password = TEST_PASSWORD),
                    bearer(login("admin")),
                )

            assertFails { events(created.body().token) { nextBatch() } }
        }

    private suspend fun ApiSocket<Nothing, ChangeEventBatch>.awaitEvents(vararg expected: ChangeEvent) {
        val missing = expected.toMutableSet()
        while (missing.isNotEmpty()) {
            val batch =
                try {
                    nextBatch()
                } catch (e: TimeoutCancellationException) {
                    fail("never received $missing", e)
                }
            missing -= batch.toSet()
        }
    }

    private val sourceMp3: Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("test-resources") }
            .first { it.isDirectory() }
            .resolve("Terry Pratchett/Good Omens/Good Omens.mp3")

    @Test
    fun `a book is announced when its file is scanned, removed and put back`() =
        deweyServer {
            val root = dataDir.resolve("library")
            val file = root.resolve("Terry Pratchett/Good Omens/Good Omens.mp3")
            val libId =
                newLibrary(
                    "lib",
                    folders = listOf(root.createDirectories().absolutePathString()),
                    fileScanners = listOf(FileScanner("AudioFolderScanner")),
                )
            val pipeline = getKoin().get<LibraryImportPipeline>()

            events(login("admin")) {
                assertEquals(emptyList(), nextBatch())

                sourceMp3.copyTo(file.createParentDirectories())
                pipeline.scanLibrary(libId)
                val author = getKoin().get<AuthorRepository>().findByTaggedName("Terry Pratchett", libId)!!.id
                val book = getKoin().get<BookRepository>().findByTaggedName("Good Omens", listOf(author), libId)!!.id
                awaitEvents(
                    changeEvent(ChangedEntity.BOOK, ChangeType.CREATED, book, libId),
                    changeEvent(ChangedEntity.AUTHOR, ChangeType.CREATED, author, libId),
                )

                // The rows are only hidden at this point, which to a client is the same as gone
                val watched = file.canonical()
                file.deleteExisting()
                pipeline.enqueue(watched)
                awaitEvents(
                    changeEvent(ChangedEntity.BOOK, ChangeType.DELETED, book, libId),
                    changeEvent(ChangedEntity.AUTHOR, ChangeType.DELETED, author, libId),
                )
                assertEquals(HttpStatusCode.NotFound, api.getBook(book, libId, bearer(login("admin"))).status)

                sourceMp3.copyTo(file)
                pipeline.enqueue(watched)
                awaitEvents(
                    changeEvent(ChangedEntity.BOOK, ChangeType.CREATED, book, libId),
                    changeEvent(ChangedEntity.AUTHOR, ChangeType.CREATED, author, libId),
                )
            }
        }

    @Test
    fun `a client without a token is turned away`() =
        deweyServer {
            assertFails { events(token = null) {} }
            assertFails { events(token = "not.a.token") {} }
        }
}
