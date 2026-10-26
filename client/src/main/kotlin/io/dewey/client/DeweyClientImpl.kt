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

@Suppress("unused")
abstract class DeweyClientImpl(
    baseUrl: Url,
) : RequestRunner(baseUrl),
    DeweyClient {
    override suspend fun autoMatchAuthor(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, Author>,
    ): OpenApiHttpResponse<Author> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/authors/$id/automatch",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<Author>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun autoMatchBook(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, Book>,
    ): OpenApiHttpResponse<Book> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/books/$id/automatch",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<Book>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun autoMatchSeries(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, Series>,
    ): OpenApiHttpResponse<Series> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/series/$id/automatch",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<Series>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun createAccessToken(
        body: CreateAccessToken,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<CreateAccessToken>,
        onAfterRequest: OnAfterRequest<CreateAccessToken, CreatedAccessToken>,
    ): OpenApiHttpResponse<CreatedAccessToken> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user/tokens",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<CreateAccessToken>(),
                typeInfo<CreatedAccessToken>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun createAuthor(
        libraryId: Uuid,
        body: AuthorCreate,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<AuthorCreate>,
        onAfterRequest: OnAfterRequest<AuthorCreate, AuthorDetailed>,
    ): OpenApiHttpResponse<AuthorDetailed> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/authors",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<AuthorCreate>(),
                typeInfo<AuthorDetailed>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun createLibrary(
        body: UpdateLibrary,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<UpdateLibrary>,
        onAfterRequest: OnAfterRequest<UpdateLibrary, Library>,
    ): OpenApiHttpResponse<Library> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<UpdateLibrary>(),
                typeInfo<Library>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun createSeries(
        libraryId: Uuid,
        body: SeriesCreate,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<SeriesCreate>,
        onAfterRequest: OnAfterRequest<SeriesCreate, SeriesDetailed>,
    ): OpenApiHttpResponse<SeriesDetailed> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/series",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<SeriesCreate>(),
                typeInfo<SeriesDetailed>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun deleteLibrary(
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, Unit>,
    ): OpenApiHttpResponse<Unit> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId",
                    method = HttpMethod("DELETE"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<Unit>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun deleteUser(
        id: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, Unit>,
    ): OpenApiHttpResponse<Unit> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user/$id",
                    method = HttpMethod("DELETE"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<Unit>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getApiVersion(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, ApiVersion>,
    ): OpenApiHttpResponse<ApiVersion> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/version",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = false,
                    securitySchema = "null",
                ),
                typeInfo<Unit>(),
                typeInfo<ApiVersion>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getAudioFile(
        id: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, ByteArrayInputStream>,
    ): OpenApiHttpResponse<ByteArrayInputStream> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/stream/audio/$id",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "media",
                ),
                typeInfo<Unit>(),
                typeInfo<ByteArrayInputStream>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getAuthor(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, AuthorDetailed>,
    ): OpenApiHttpResponse<AuthorDetailed> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/authors/$id",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<AuthorDetailed>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getAuthorAutocomplete(
        q: String,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<NamedId>>,
    ): OpenApiHttpResponse<List<NamedId>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/authors/autocomplete",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("q" to q),
                ),
                typeInfo<Unit>(),
                typeInfo<List<NamedId>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getAuthorMetadata(
        provider: String,
        id: String,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, MetadataAuthor>,
    ): OpenApiHttpResponse<MetadataAuthor> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/metadata/author/$id",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("provider" to provider),
                ),
                typeInfo<Unit>(),
                typeInfo<MetadataAuthor>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getBook(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, BookDetailed>,
    ): OpenApiHttpResponse<BookDetailed> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/books/$id",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<BookDetailed>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getBookAutocomplete(
        q: String,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<TitledId>>,
    ): OpenApiHttpResponse<List<TitledId>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/books/autocomplete",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("q" to q),
                ),
                typeInfo<Unit>(),
                typeInfo<List<TitledId>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getBookChapterMetadata(
        provider: String,
        id: String,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, MetadataChapters>,
    ): OpenApiHttpResponse<MetadataChapters> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/metadata/book/$id/chapters",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("provider" to provider),
                ),
                typeInfo<Unit>(),
                typeInfo<MetadataChapters>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getBookMetadata(
        provider: String,
        id: String,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, MetadataBook>,
    ): OpenApiHttpResponse<MetadataBook> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/metadata/book/$id",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("provider" to provider),
                ),
                typeInfo<Unit>(),
                typeInfo<MetadataBook>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getContinueListening(
        limit: Int?,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<Book>>,
    ): OpenApiHttpResponse<List<Book>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/me/continue-listening",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("limit" to limit),
                ),
                typeInfo<Unit>(),
                typeInfo<List<Book>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getCurrentUser(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, DeweyUserWithPermissions<UserPermissions>>,
    ): OpenApiHttpResponse<DeweyUserWithPermissions<UserPermissions>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user/current",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<DeweyUserWithPermissions<UserPermissions>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getGenre(
        name: String,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, GenreDetailed>,
    ): OpenApiHttpResponse<GenreDetailed> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/genres/$name",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<GenreDetailed>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getImageFile(
        id: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, ByteArrayInputStream>,
    ): OpenApiHttpResponse<ByteArrayInputStream> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/stream/images/$id",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "media",
                ),
                typeInfo<Unit>(),
                typeInfo<ByteArrayInputStream>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getLibrary(
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, Library>,
    ): OpenApiHttpResponse<Library> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<Library>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getListeningHistory(
        limit: Int?,
        offset: Long?,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<ListeningHistoryEntry>>,
    ): OpenApiHttpResponse<PaginatedResponse<ListeningHistoryEntry>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/me/history",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("limit" to limit, "offset" to offset),
                ),
                typeInfo<Unit>(),
                typeInfo<PaginatedResponse<ListeningHistoryEntry>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getNarrator(
        name: String,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, NarratorDetailed>,
    ): OpenApiHttpResponse<NarratorDetailed> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/narrators/$name",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<NarratorDetailed>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getSeries(
        id: Uuid,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, SeriesDetailed>,
    ): OpenApiHttpResponse<SeriesDetailed> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/series/$id",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<SeriesDetailed>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getSeriesAutocomplete(
        q: String,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<TitledId>>,
    ): OpenApiHttpResponse<List<TitledId>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/series/autocomplete",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("q" to q),
                ),
                typeInfo<Unit>(),
                typeInfo<List<TitledId>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getSeriesMetadata(
        provider: String,
        id: String,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, MetadataSeries>,
    ): OpenApiHttpResponse<MetadataSeries> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/metadata/series/$id",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("provider" to provider),
                ),
                typeInfo<Unit>(),
                typeInfo<MetadataSeries>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun getUser(
        id: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, DeweyUser>,
    ): OpenApiHttpResponse<DeweyUser> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user/$id",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<DeweyUser>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listAccessTokens(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<AccessToken>>,
    ): OpenApiHttpResponse<List<AccessToken>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user/tokens",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<List<AccessToken>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listAuthors(
        libraryId: Uuid,
        limit: Int?,
        offset: Long?,
        order: Order?,
        showInvisible: Boolean?,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<Author>>,
    ): OpenApiHttpResponse<PaginatedResponse<Author>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/authors",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf(
                        "limit" to limit,
                        "offset" to offset,
                        "order" to order,
                        "showInvisible" to showInvisible,
                    ),
                ),
                typeInfo<Unit>(),
                typeInfo<PaginatedResponse<Author>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listBooks(
        libraryId: Uuid,
        limit: Int?,
        offset: Long?,
        order: Order?,
        showInvisible: Boolean?,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<Book>>,
    ): OpenApiHttpResponse<PaginatedResponse<Book>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/books",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf(
                        "limit" to limit,
                        "offset" to offset,
                        "order" to order,
                        "showInvisible" to showInvisible,
                    ),
                ),
                typeInfo<Unit>(),
                typeInfo<PaginatedResponse<Book>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listFileScanners(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<FileScanner>>,
    ): OpenApiHttpResponse<List<FileScanner>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/scanners",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "admin",
                ),
                typeInfo<Unit>(),
                typeInfo<List<FileScanner>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listFoldersAtACertainPath(
        path: String,
        showHidden: Boolean?,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<FileSystemItem>>,
    ): OpenApiHttpResponse<List<FileSystemItem>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/fs",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "admin",
                    queryParameters = mapOf("path" to path, "showHidden" to showHidden),
                ),
                typeInfo<Unit>(),
                typeInfo<List<FileSystemItem>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listGenres(
        libraryId: Uuid,
        limit: Int?,
        offset: Long?,
        order: Order?,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<Genre>>,
    ): OpenApiHttpResponse<PaginatedResponse<Genre>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/genres",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("limit" to limit, "offset" to offset, "order" to order),
                ),
                typeInfo<Unit>(),
                typeInfo<PaginatedResponse<Genre>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listLibraries(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<Library>>,
    ): OpenApiHttpResponse<List<Library>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<List<Library>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listMetadataAgents(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<MetadataAgentApiModel>>,
    ): OpenApiHttpResponse<List<MetadataAgentApiModel>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/metadata-agents",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "admin",
                ),
                typeInfo<Unit>(),
                typeInfo<List<MetadataAgentApiModel>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listNarrators(
        libraryId: Uuid,
        limit: Int?,
        offset: Long?,
        order: Order?,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<Narrator>>,
    ): OpenApiHttpResponse<PaginatedResponse<Narrator>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/narrators",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("limit" to limit, "offset" to offset, "order" to order),
                ),
                typeInfo<Unit>(),
                typeInfo<PaginatedResponse<Narrator>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listSeries(
        libraryId: Uuid,
        limit: Int?,
        offset: Long?,
        order: Order?,
        showInvisible: Boolean?,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, PaginatedResponse<Series>>,
    ): OpenApiHttpResponse<PaginatedResponse<Series>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/series",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf(
                        "limit" to limit,
                        "offset" to offset,
                        "order" to order,
                        "showInvisible" to showInvisible,
                    ),
                ),
                typeInfo<Unit>(),
                typeInfo<PaginatedResponse<Series>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listThirdPartyLicenses(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<ThirdPartyLicense>>,
    ): OpenApiHttpResponse<List<ThirdPartyLicense>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/licenses",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<List<ThirdPartyLicense>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listUsers(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<DeweyUserWithPermissions<UserPermissions>>>,
    ): OpenApiHttpResponse<List<DeweyUserWithPermissions<UserPermissions>>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "admin",
                ),
                typeInfo<Unit>(),
                typeInfo<List<DeweyUserWithPermissions<UserPermissions>>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun listenToEvents(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
    ): ApiSocket<Nothing, ChangeEventBatch> =
        run {
            openSocket(
                RequestMetadata(
                    path = "/api/events",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "media",
                ),
                typeInfo<Unit>(),
                typeInfo<ChangeEventBatch>(),
                onBeforeRequest = onBeforeRequest,
            )
        }

    override suspend fun loginUser(
        body: DeweyLoginUser,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<DeweyLoginUser>,
        onAfterRequest: OnAfterRequest<DeweyLoginUser, DeweyAccessToken>,
    ): OpenApiHttpResponse<DeweyAccessToken> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/login",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = body,
                    shouldLogin = false,
                    securitySchema = "null",
                ),
                typeInfo<DeweyLoginUser>(),
                typeInfo<DeweyAccessToken>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun logoutUser(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, Unit>,
    ): OpenApiHttpResponse<Unit> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/logout",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = false,
                    securitySchema = "null",
                ),
                typeInfo<Unit>(),
                typeInfo<Unit>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun pingServer(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, Unit>,
    ): OpenApiHttpResponse<Unit> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/ping",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = false,
                    securitySchema = "null",
                ),
                typeInfo<Unit>(),
                typeInfo<Unit>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun refreshAccessToken(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, DeweyAccessToken>,
    ): OpenApiHttpResponse<DeweyAccessToken> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user/refresh",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = false,
                    securitySchema = "null",
                ),
                typeInfo<Unit>(),
                typeInfo<DeweyAccessToken>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun registerUser(
        body: DeweyRegisterUser,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<DeweyRegisterUser>,
        onAfterRequest: OnAfterRequest<DeweyRegisterUser, DeweyUser>,
    ): OpenApiHttpResponse<DeweyUser> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/register",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = body,
                    shouldLogin = false,
                    securitySchema = "null",
                ),
                typeInfo<DeweyRegisterUser>(),
                typeInfo<DeweyUser>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun rescanLibrary(
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, Unit>,
    ): OpenApiHttpResponse<Unit> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/rescan",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<Unit>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun retrieveJwks(
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, DeweyJWKs>,
    ): OpenApiHttpResponse<DeweyJWKs> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/jwks.json",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = false,
                    securitySchema = "null",
                ),
                typeInfo<Unit>(),
                typeInfo<DeweyJWKs>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun revealAccessToken(
        tokenId: Uuid,
        body: RevealAccessToken,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<RevealAccessToken>,
        onAfterRequest: OnAfterRequest<RevealAccessToken, RevealedAccessToken>,
    ): OpenApiHttpResponse<RevealedAccessToken> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user/tokens/$tokenId/reveal",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<RevealAccessToken>(),
                typeInfo<RevealedAccessToken>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun revokeAccessToken(
        tokenId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, Unit>,
    ): OpenApiHttpResponse<Unit> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user/tokens/$tokenId",
                    method = HttpMethod("DELETE"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<Unit>(),
                typeInfo<Unit>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun searchAuthorMetadata(
        q: String,
        libraryId: Uuid,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<MetadataAuthor>>,
    ): OpenApiHttpResponse<List<MetadataAuthor>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/metadata/author/search",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("q" to q),
                ),
                typeInfo<Unit>(),
                typeInfo<List<MetadataAuthor>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun searchBookMetadata(
        q: String,
        libraryId: Uuid,
        authorName: String?,
        keywords: String?,
        language: MetadataLanguage?,
        narrator: String?,
        pageSize: MetadataSearchCount?,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<MetadataBook>>,
    ): OpenApiHttpResponse<List<MetadataBook>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/metadata/book/search",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf(
                        "authorName" to authorName,
                        "keywords" to keywords,
                        "language" to language,
                        "narrator" to narrator,
                        "pageSize" to pageSize,
                        "q" to q,
                    ),
                ),
                typeInfo<Unit>(),
                typeInfo<List<MetadataBook>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun searchInAllLibraries(
        author: String?,
        book: String?,
        q: String?,
        series: String?,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, LibrarySearchResult>,
    ): OpenApiHttpResponse<LibrarySearchResult> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/search",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("author" to author, "book" to book, "q" to q, "series" to series),
                ),
                typeInfo<Unit>(),
                typeInfo<LibrarySearchResult>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun searchSeriesMetadata(
        q: String,
        libraryId: Uuid,
        authorName: String?,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<Unit>,
        onAfterRequest: OnAfterRequest<Unit, List<MetadataSeries>>,
    ): OpenApiHttpResponse<List<MetadataSeries>> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/metadata/series/search",
                    method = HttpMethod("GET"),
                    headers = headers,
                    body = Unit,
                    shouldLogin = true,
                    securitySchema = "normal",
                    queryParameters = mapOf("authorName" to authorName, "q" to q),
                ),
                typeInfo<Unit>(),
                typeInfo<List<MetadataSeries>>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun setBookDismissed(
        id: Uuid,
        libraryId: Uuid,
        body: SetDismissed,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<SetDismissed>,
        onAfterRequest: OnAfterRequest<SetDismissed, Unit>,
    ): OpenApiHttpResponse<Unit> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/books/$id/progress/dismissed",
                    method = HttpMethod("PUT"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<SetDismissed>(),
                typeInfo<Unit>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun setBookFinished(
        id: Uuid,
        libraryId: Uuid,
        body: SetFinished,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<SetFinished>,
        onAfterRequest: OnAfterRequest<SetFinished, Unit>,
    ): OpenApiHttpResponse<Unit> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/books/$id/progress/finished",
                    method = HttpMethod("PUT"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<SetFinished>(),
                typeInfo<Unit>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun setBookProgress(
        id: Uuid,
        libraryId: Uuid,
        body: ProgressUpdate,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<ProgressUpdate>,
        onAfterRequest: OnAfterRequest<ProgressUpdate, Unit>,
    ): OpenApiHttpResponse<Unit> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/books/$id/progress",
                    method = HttpMethod("PUT"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<ProgressUpdate>(),
                typeInfo<Unit>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun updateAuthor(
        id: Uuid,
        libraryId: Uuid,
        body: AuthorUpdate,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<AuthorUpdate>,
        onAfterRequest: OnAfterRequest<AuthorUpdate, Author>,
    ): OpenApiHttpResponse<Author> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/authors/$id",
                    method = HttpMethod("PATCH"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<AuthorUpdate>(),
                typeInfo<Author>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun updateBook(
        id: Uuid,
        libraryId: Uuid,
        body: BookUpdate,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<BookUpdate>,
        onAfterRequest: OnAfterRequest<BookUpdate, Book>,
    ): OpenApiHttpResponse<Book> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/books/$id",
                    method = HttpMethod("PATCH"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<BookUpdate>(),
                typeInfo<Book>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun updateLibrary(
        libraryId: Uuid,
        body: PartialUpdateLibrary,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<PartialUpdateLibrary>,
        onAfterRequest: OnAfterRequest<PartialUpdateLibrary, Library>,
    ): OpenApiHttpResponse<Library> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId",
                    method = HttpMethod("PATCH"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<PartialUpdateLibrary>(),
                typeInfo<Library>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun updatePassword(
        id: Uuid,
        body: DeweyChangePassword,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<DeweyChangePassword>,
        onAfterRequest: OnAfterRequest<DeweyChangePassword, Unit>,
    ): OpenApiHttpResponse<Unit> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user/$id/password",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<DeweyChangePassword>(),
                typeInfo<Unit>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun updatePermissions(
        id: Uuid,
        body: DeweyModifyPermissions<UpdateUserPermissions>,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<DeweyModifyPermissions<UpdateUserPermissions>>,
        onAfterRequest: OnAfterRequest<DeweyModifyPermissions<UpdateUserPermissions>, DeweyUser>,
    ): OpenApiHttpResponse<DeweyUser> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user/$id/permissions",
                    method = HttpMethod("PUT"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "admin",
                ),
                typeInfo<DeweyModifyPermissions<UpdateUserPermissions>>(),
                typeInfo<DeweyUser>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun updateSeries(
        id: Uuid,
        libraryId: Uuid,
        body: SeriesUpdate,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<SeriesUpdate>,
        onAfterRequest: OnAfterRequest<SeriesUpdate, Series>,
    ): OpenApiHttpResponse<Series> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/libraries/$libraryId/series/$id",
                    method = HttpMethod("PATCH"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<SeriesUpdate>(),
                typeInfo<Series>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }

    override suspend fun updateUsername(
        id: Uuid,
        body: DeweyRenameUser,
        headers: Headers,
        onBeforeRequest: OnBeforeRequest<DeweyRenameUser>,
        onAfterRequest: OnAfterRequest<DeweyRenameUser, DeweyUser>,
    ): OpenApiHttpResponse<DeweyUser> =
        run {
            makeRequest(
                RequestMetadata(
                    path = "/api/auth/user/$id/username",
                    method = HttpMethod("POST"),
                    headers = headers,
                    body = body,
                    shouldLogin = true,
                    securitySchema = "normal",
                ),
                typeInfo<DeweyRenameUser>(),
                typeInfo<DeweyUser>(),
                onBeforeRequest = onBeforeRequest,
                onAfterRequest = onAfterRequest,
            )
        }
}
