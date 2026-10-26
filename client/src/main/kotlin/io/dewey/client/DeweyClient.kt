@file:Suppress("UnusedImport")

package io.dewey.client

import io.dewey.client.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import java.io.ByteArrayInputStream
import kotlin.uuid.Uuid

interface DeweyClient {
    val apiVersion: String
        get() = "0.0.2"

    suspend fun autoMatchAuthor(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, Author> = { _, _ -> },
    ): OpenApiHttpResponse<Author>

    suspend fun autoMatchBook(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, Book> = { _, _ -> },
    ): OpenApiHttpResponse<Book>

    suspend fun autoMatchSeries(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, Series> = { _, _ -> },
    ): OpenApiHttpResponse<Series>

    suspend fun createAccessToken(
        body: CreateAccessToken,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<CreateAccessToken> = { _, _ -> },
        onAfterRequest: OnAfterRequest<CreateAccessToken, CreatedAccessToken> = { _, _ -> },
    ): OpenApiHttpResponse<CreatedAccessToken>

    suspend fun createAuthor(
        libraryId: Uuid,
        body: AuthorCreate,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<AuthorCreate> = { _, _ -> },
        onAfterRequest: OnAfterRequest<AuthorCreate, AuthorDetailed> = { _, _ -> },
    ): OpenApiHttpResponse<AuthorDetailed>

    suspend fun createLibrary(
        body: UpdateLibrary,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<UpdateLibrary> = { _, _ -> },
        onAfterRequest: OnAfterRequest<UpdateLibrary, Library> = { _, _ -> },
    ): OpenApiHttpResponse<Library>

    suspend fun createSeries(
        libraryId: Uuid,
        body: SeriesCreate,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<SeriesCreate> = { _, _ -> },
        onAfterRequest: OnAfterRequest<SeriesCreate, SeriesDetailed> = { _, _ -> },
    ): OpenApiHttpResponse<SeriesDetailed>

    suspend fun deleteLibrary(
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, Unit> = { _, _ -> },
    ): OpenApiHttpResponse<Unit>

    suspend fun deleteUser(
        id: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, Unit> = { _, _ -> },
    ): OpenApiHttpResponse<Unit>

    suspend fun getApiVersion(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, ApiVersion> = { _, _ -> },
    ): OpenApiHttpResponse<ApiVersion>

    suspend fun getAudioFile(
        id: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, ByteArrayInputStream> = { _, _ -> },
    ): OpenApiHttpResponse<ByteArrayInputStream>

    suspend fun getAuthor(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, AuthorDetailed> = { _, _ -> },
    ): OpenApiHttpResponse<AuthorDetailed>

    suspend fun getAuthorAutocomplete(
        q: String,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<NamedId>> = { _, _ -> },
    ): OpenApiHttpResponse<List<NamedId>>

    suspend fun getAuthorMetadata(
        provider: String,
        id: String,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, MetadataAuthor> = { _, _ -> },
    ): OpenApiHttpResponse<MetadataAuthor>

    suspend fun getBook(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, BookDetailed> = { _, _ -> },
    ): OpenApiHttpResponse<BookDetailed>

    suspend fun getBookAutocomplete(
        q: String,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<TitledId>> = { _, _ -> },
    ): OpenApiHttpResponse<List<TitledId>>

    suspend fun getBookChapterMetadata(
        provider: String,
        id: String,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, MetadataChapters> = { _, _ -> },
    ): OpenApiHttpResponse<MetadataChapters>

    suspend fun getBookMetadata(
        provider: String,
        id: String,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, MetadataBook> = { _, _ -> },
    ): OpenApiHttpResponse<MetadataBook>

    suspend fun getContinueListening(
        limit: Int? = null,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<Book>> = { _, _ -> },
    ): OpenApiHttpResponse<List<Book>>

    suspend fun getCurrentUser(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, DeweyUserWithPermissions<UserPermissions>> = { _, _ -> },
    ): OpenApiHttpResponse<DeweyUserWithPermissions<UserPermissions>>

    suspend fun getGenre(
        name: String,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, GenreDetailed> = { _, _ -> },
    ): OpenApiHttpResponse<GenreDetailed>

    suspend fun getImageFile(
        id: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, ByteArrayInputStream> = { _, _ -> },
    ): OpenApiHttpResponse<ByteArrayInputStream>

    suspend fun getLibrary(
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, Library> = { _, _ -> },
    ): OpenApiHttpResponse<Library>

    suspend fun getListeningHistory(
        limit: Int? = null,
        offset: Long? = null,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<ListeningHistoryEntry>> = { _, _ -> },
    ): OpenApiHttpResponse<PaginatedResponse<ListeningHistoryEntry>>

    suspend fun getNarrator(
        name: String,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, NarratorDetailed> = { _, _ -> },
    ): OpenApiHttpResponse<NarratorDetailed>

    suspend fun getSeries(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, SeriesDetailed> = { _, _ -> },
    ): OpenApiHttpResponse<SeriesDetailed>

    suspend fun getSeriesAutocomplete(
        q: String,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<TitledId>> = { _, _ -> },
    ): OpenApiHttpResponse<List<TitledId>>

    suspend fun getSeriesMetadata(
        provider: String,
        id: String,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, MetadataSeries> = { _, _ -> },
    ): OpenApiHttpResponse<MetadataSeries>

    suspend fun getUser(
        id: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, DeweyUser> = { _, _ -> },
    ): OpenApiHttpResponse<DeweyUser>

    suspend fun listAccessTokens(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<AccessToken>> = { _, _ -> },
    ): OpenApiHttpResponse<List<AccessToken>>

    suspend fun listAuthors(
        libraryId: Uuid,
        limit: Int? = null,
        offset: Long? = null,
        order: Order? = null,
        showInvisible: Boolean? = null,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<Author>> = { _, _ -> },
    ): OpenApiHttpResponse<PaginatedResponse<Author>>

    suspend fun listBooks(
        libraryId: Uuid,
        limit: Int? = null,
        offset: Long? = null,
        order: Order? = null,
        showInvisible: Boolean? = null,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<Book>> = { _, _ -> },
    ): OpenApiHttpResponse<PaginatedResponse<Book>>

    suspend fun listFileScanners(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<FileScanner>> = { _, _ -> },
    ): OpenApiHttpResponse<List<FileScanner>>

    suspend fun listFoldersAtACertainPath(
        path: String,
        showHidden: Boolean? = null,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<FileSystemItem>> = { _, _ -> },
    ): OpenApiHttpResponse<List<FileSystemItem>>

    suspend fun listGenres(
        libraryId: Uuid,
        limit: Int? = null,
        offset: Long? = null,
        order: Order? = null,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<Genre>> = { _, _ -> },
    ): OpenApiHttpResponse<PaginatedResponse<Genre>>

    suspend fun listLibraries(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<Library>> = { _, _ -> },
    ): OpenApiHttpResponse<List<Library>>

    suspend fun listMetadataAgents(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<MetadataAgentApiModel>> = { _, _ -> },
    ): OpenApiHttpResponse<List<MetadataAgentApiModel>>

    suspend fun listNarrators(
        libraryId: Uuid,
        limit: Int? = null,
        offset: Long? = null,
        order: Order? = null,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<Narrator>> = { _, _ -> },
    ): OpenApiHttpResponse<PaginatedResponse<Narrator>>

    suspend fun listSeries(
        libraryId: Uuid,
        limit: Int? = null,
        offset: Long? = null,
        order: Order? = null,
        showInvisible: Boolean? = null,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<Series>> = { _, _ -> },
    ): OpenApiHttpResponse<PaginatedResponse<Series>>

    suspend fun listThirdPartyLicenses(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<ThirdPartyLicense>> = { _, _ -> },
    ): OpenApiHttpResponse<List<ThirdPartyLicense>>

    suspend fun listUsers(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<DeweyUserWithPermissions<UserPermissions>>> = { _, _ -> },
    ): OpenApiHttpResponse<List<DeweyUserWithPermissions<UserPermissions>>>

    suspend fun listenToEvents(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
    ): ApiSocket<Nothing, ChangeEventBatch>

    suspend fun loginUser(
        body: DeweyLoginUser,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<DeweyLoginUser> = { _, _ -> },
        onAfterRequest: OnAfterRequest<DeweyLoginUser, DeweyAccessToken> = { _, _ -> },
    ): OpenApiHttpResponse<DeweyAccessToken>

    suspend fun logoutUser(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, Unit> = { _, _ -> },
    ): OpenApiHttpResponse<Unit>

    suspend fun pingServer(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, Unit> = { _, _ -> },
    ): OpenApiHttpResponse<Unit>

    suspend fun refreshAccessToken(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, DeweyAccessToken> = { _, _ -> },
    ): OpenApiHttpResponse<DeweyAccessToken>

    suspend fun registerUser(
        body: DeweyRegisterUser,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<DeweyRegisterUser> = { _, _ -> },
        onAfterRequest: OnAfterRequest<DeweyRegisterUser, DeweyUser> = { _, _ -> },
    ): OpenApiHttpResponse<DeweyUser>

    suspend fun rescanLibrary(
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, Unit> = { _, _ -> },
    ): OpenApiHttpResponse<Unit>

    suspend fun retrieveJwks(
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, DeweyJWKs> = { _, _ -> },
    ): OpenApiHttpResponse<DeweyJWKs>

    suspend fun revokeAccessToken(
        tokenId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, Unit> = { _, _ -> },
    ): OpenApiHttpResponse<Unit>

    suspend fun searchAuthorMetadata(
        q: String,
        libraryId: Uuid,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<MetadataAuthor>> = { _, _ -> },
    ): OpenApiHttpResponse<List<MetadataAuthor>>

    suspend fun searchBookMetadata(
        q: String,
        libraryId: Uuid,
        authorName: String? = null,
        keywords: String? = null,
        language: MetadataLanguage? = null,
        narrator: String? = null,
        pageSize: MetadataSearchCount? = null,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<MetadataBook>> = { _, _ -> },
    ): OpenApiHttpResponse<List<MetadataBook>>

    suspend fun searchInAllLibraries(
        author: String? = null,
        book: String? = null,
        q: String? = null,
        series: String? = null,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, LibrarySearchResult> = { _, _ -> },
    ): OpenApiHttpResponse<LibrarySearchResult>

    suspend fun searchSeriesMetadata(
        q: String,
        libraryId: Uuid,
        authorName: String? = null,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<Unit> = { _, _ -> },
        onAfterRequest: OnAfterRequest<Unit, List<MetadataSeries>> = { _, _ -> },
    ): OpenApiHttpResponse<List<MetadataSeries>>

    suspend fun setBookDismissed(
        id: Uuid,
        libraryId: Uuid,
        body: SetDismissed,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<SetDismissed> = { _, _ -> },
        onAfterRequest: OnAfterRequest<SetDismissed, Unit> = { _, _ -> },
    ): OpenApiHttpResponse<Unit>

    suspend fun setBookFinished(
        id: Uuid,
        libraryId: Uuid,
        body: SetFinished,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<SetFinished> = { _, _ -> },
        onAfterRequest: OnAfterRequest<SetFinished, Unit> = { _, _ -> },
    ): OpenApiHttpResponse<Unit>

    suspend fun setBookProgress(
        id: Uuid,
        libraryId: Uuid,
        body: ProgressUpdate,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<ProgressUpdate> = { _, _ -> },
        onAfterRequest: OnAfterRequest<ProgressUpdate, Unit> = { _, _ -> },
    ): OpenApiHttpResponse<Unit>

    suspend fun updateAuthor(
        id: Uuid,
        libraryId: Uuid,
        body: AuthorUpdate,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<AuthorUpdate> = { _, _ -> },
        onAfterRequest: OnAfterRequest<AuthorUpdate, Author> = { _, _ -> },
    ): OpenApiHttpResponse<Author>

    suspend fun updateBook(
        id: Uuid,
        libraryId: Uuid,
        body: BookUpdate,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<BookUpdate> = { _, _ -> },
        onAfterRequest: OnAfterRequest<BookUpdate, Book> = { _, _ -> },
    ): OpenApiHttpResponse<Book>

    suspend fun updateLibrary(
        libraryId: Uuid,
        body: PartialUpdateLibrary,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<PartialUpdateLibrary> = { _, _ -> },
        onAfterRequest: OnAfterRequest<PartialUpdateLibrary, Library> = { _, _ -> },
    ): OpenApiHttpResponse<Library>

    suspend fun updatePassword(
        id: Uuid,
        body: DeweyChangePassword,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<DeweyChangePassword> = { _, _ -> },
        onAfterRequest: OnAfterRequest<DeweyChangePassword, Unit> = { _, _ -> },
    ): OpenApiHttpResponse<Unit>

    suspend fun updatePermissions(
        id: Uuid,
        body: DeweyModifyPermissions<UpdateUserPermissions>,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<DeweyModifyPermissions<UpdateUserPermissions>> = { _, _ -> },
        onAfterRequest: OnAfterRequest<DeweyModifyPermissions<UpdateUserPermissions>, DeweyUser> = { _, _ -> },
    ): OpenApiHttpResponse<DeweyUser>

    suspend fun updateSeries(
        id: Uuid,
        libraryId: Uuid,
        body: SeriesUpdate,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<SeriesUpdate> = { _, _ -> },
        onAfterRequest: OnAfterRequest<SeriesUpdate, Series> = { _, _ -> },
    ): OpenApiHttpResponse<Series>

    suspend fun updateUsername(
        id: Uuid,
        body: DeweyRenameUser,
        headers: Headers = Headers.Empty,
        onBeforeRequest: OnBeforeRequest<DeweyRenameUser> = { _, _ -> },
        onAfterRequest: OnAfterRequest<DeweyRenameUser, DeweyUser> = { _, _ -> },
    ): OpenApiHttpResponse<DeweyUser>
}
