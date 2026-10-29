import {
  ApiCallData,
  ApiInterceptor,
  ApiResponse,
  ApiSocket,
  ApiSocketData,
  ApiSocketInterceptor,
  _request,
  _createUrl,
  _mergeHeaders,
  _socket,
} from "./client"
import type {
  ApiVersion,
  Author,
  AuthorCreate,
  AuthorDetailed,
  AuthorUpdate,
  Book,
  BookDetailed,
  BookUpdate,
  ChangeEventBatch,
  DeweyAccessToken,
  DeweyChangePassword,
  DeweyJWKs,
  DeweyLoginUser,
  DeweyModifyPermissions,
  DeweyRegisterUser,
  DeweyRenameUser,
  DeweyUser,
  DeweyUserWithPermissions,
  Empty,
  FileScanner,
  FileSystemItem,
  Genre,
  GenreDetailed,
  Library,
  LibrarySearchResult,
  ListeningHistoryEntry,
  MetadataAgentApiModel,
  MetadataAuthor,
  MetadataBook,
  MetadataChapters,
  MetadataLanguage,
  MetadataSearchCount,
  MetadataSeries,
  NamedId,
  Narrator,
  NarratorDetailed,
  Order,
  PaginatedResponse,
  PartialUpdateLibrary,
  ProgressUpdate,
  Series,
  SeriesCreate,
  SeriesDetailed,
  SeriesUpdate,
  SetDismissed,
  SetFinished,
  ThirdPartyLicense,
  TitledId,
  UUID,
  UpdateLibrary,
  UpdateUserPermissions,
  UserPermissions,
} from "./models"

export const createApi = (
  defaultHeaders: HeadersInit = {},
  defaultInterceptors: ApiInterceptor[] = [],
  executor = (callData: ApiCallData) =>
    fetch(callData.route, {
      method: callData.method,
      headers: callData.headers,
      body: callData.bodySerializer(callData.body),
    }),
  defaultSocketInterceptors: ApiSocketInterceptor[] = [],
  socketExecutor = (socketData: ApiSocketData) => new WebSocket(socketData.route)
) => {
  const defaultHeadersImpl = new Headers(defaultHeaders)
  return {
    apiVersion: "0.0.2",
    autoMatchAuthor: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Author>> => {
      return _request(
        `/api/libraries/${libraryId}/authors/${id}/automatch`,
        "POST",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    autoMatchBook: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Book>> => {
      return _request(
        `/api/libraries/${libraryId}/books/${id}/automatch`,
        "POST",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    autoMatchSeries: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Series>> => {
      return _request(
        `/api/libraries/${libraryId}/series/${id}/automatch`,
        "POST",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    createAuthor: (
      { libraryId }: { libraryId: UUID },
      body: AuthorCreate,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<AuthorDetailed>> => {
      return _request(
        `/api/libraries/${libraryId}/authors`,
        "POST",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    createLibrary: (
      body: UpdateLibrary,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Library>> => {
      return _request(
        `/api/libraries`,
        "POST",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    createSeries: (
      { libraryId }: { libraryId: UUID },
      body: SeriesCreate,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<SeriesDetailed>> => {
      return _request(
        `/api/libraries/${libraryId}/series`,
        "POST",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    deleteLibrary: (
      { libraryId }: { libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Empty>> => {
      return _request(
        `/api/libraries/${libraryId}`,
        "DELETE",
        "text",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    deleteUser: (
      { id }: { id: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Empty>> => {
      return _request(
        `/api/auth/user/${id}`,
        "DELETE",
        "text",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getApiVersion: (
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<ApiVersion>> => {
      return _request(
        `/api/version`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        false
      )
    },
    getAudioFile: (
      { id }: { id: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Blob>> => {
      return _request(
        `/api/stream/audio/${id}`,
        "GET",
        "blob",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getAuthor: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<AuthorDetailed>> => {
      return _request(
        `/api/libraries/${libraryId}/authors/${id}`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getAuthorAutocomplete: (
      { query, libraryId }: { query: string; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<NamedId>>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/authors/autocomplete`, { query }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getAuthorMetadata: (
      { provider, id, libraryId }: { provider: string; id: string; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<MetadataAuthor>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/metadata/author/${id}`, { provider }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getBook: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<BookDetailed>> => {
      return _request(
        `/api/libraries/${libraryId}/books/${id}`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getBookAutocomplete: (
      { query, libraryId }: { query: string; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<TitledId>>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/books/autocomplete`, { query }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getBookChapterMetadata: (
      { provider, id, libraryId }: { provider: string; id: string; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<MetadataChapters>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/metadata/book/${id}/chapters`, { provider }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getBookMetadata: (
      { provider, id, libraryId }: { provider: string; id: string; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<MetadataBook>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/metadata/book/${id}`, { provider }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getContinueListening: (
      { limit }: { limit?: number },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<Book>>> => {
      return _request(
        _createUrl(`/api/me/continue-listening`, { limit }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getCurrentUser: (
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<DeweyUserWithPermissions<UserPermissions>>> => {
      return _request(
        `/api/auth/user/current`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getGenre: (
      { name, libraryId }: { name: string; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<GenreDetailed>> => {
      return _request(
        `/api/libraries/${libraryId}/genres/${name}`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getImageFile: (
      { id }: { id: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Blob>> => {
      return _request(
        `/api/stream/images/${id}`,
        "GET",
        "blob",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getLibrary: (
      { libraryId }: { libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Library>> => {
      return _request(
        `/api/libraries/${libraryId}`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getListeningHistory: (
      { limit, offset }: { limit?: number; offset?: number },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<PaginatedResponse<ListeningHistoryEntry>>> => {
      return _request(
        _createUrl(`/api/me/history`, { limit, offset }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getNarrator: (
      { name, libraryId }: { name: string; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<NarratorDetailed>> => {
      return _request(
        `/api/libraries/${libraryId}/narrators/${name}`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getSeries: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<SeriesDetailed>> => {
      return _request(
        `/api/libraries/${libraryId}/series/${id}`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getSeriesAutocomplete: (
      { query, libraryId }: { query: string; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<TitledId>>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/series/autocomplete`, { query }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getSeriesMetadata: (
      { provider, id, libraryId }: { provider: string; id: string; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<MetadataSeries>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/metadata/series/${id}`, { provider }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    getUser: (
      { id }: { id: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<DeweyUser>> => {
      return _request(
        `/api/auth/user/${id}`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listAuthors: (
      {
        limit,
        offset,
        order,
        showInvisible,
        libraryId,
      }: { limit?: number; offset?: number; order?: Order; showInvisible?: boolean; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<PaginatedResponse<Author>>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/authors`, { limit, offset, order, showInvisible }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listBooks: (
      {
        limit,
        offset,
        order,
        showInvisible,
        libraryId,
      }: { limit?: number; offset?: number; order?: Order; showInvisible?: boolean; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<PaginatedResponse<Book>>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/books`, { limit, offset, order, showInvisible }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listFileScanners: (
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<FileScanner>>> => {
      return _request(
        `/api/scanners`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listFoldersAtACertainPath: (
      { path, showHidden }: { path: string; showHidden?: boolean },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<FileSystemItem>>> => {
      return _request(
        _createUrl(`/api/fs`, { path, showHidden }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listGenres: (
      { limit, offset, order, libraryId }: { limit?: number; offset?: number; order?: Order; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<PaginatedResponse<Genre>>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/genres`, { limit, offset, order }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listLibraries: (
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<Library>>> => {
      return _request(
        `/api/libraries`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listMetadataAgents: (
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<MetadataAgentApiModel>>> => {
      return _request(
        `/api/metadata-agents`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listNarrators: (
      { limit, offset, order, libraryId }: { limit?: number; offset?: number; order?: Order; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<PaginatedResponse<Narrator>>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/narrators`, { limit, offset, order }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listSeries: (
      {
        limit,
        offset,
        order,
        showInvisible,
        libraryId,
      }: { limit?: number; offset?: number; order?: Order; showInvisible?: boolean; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<PaginatedResponse<Series>>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/series`, { limit, offset, order, showInvisible }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listThirdPartyLicenses: (
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<ThirdPartyLicense>>> => {
      return _request(
        `/api/licenses`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listUsers: (
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<DeweyUserWithPermissions<UserPermissions>>>> => {
      return _request(
        `/api/auth/user`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    listenToEvents: (interceptors: ApiSocketInterceptor[] = []): Promise<ApiSocket<never, ChangeEventBatch>> => {
      return _socket(`/api/events`, [...defaultSocketInterceptors, ...interceptors], socketExecutor, true)
    },
    loginUser: (
      body: DeweyLoginUser,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<DeweyAccessToken>> => {
      return _request(
        `/api/auth/login`,
        "POST",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        false
      )
    },
    logoutUser: (headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<Empty>> => {
      return _request(
        `/api/auth/logout`,
        "POST",
        "text",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        false
      )
    },
    pingServer: (headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<Empty>> => {
      return _request(
        `/api/ping`,
        "POST",
        "text",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        false
      )
    },
    refreshAccessToken: (
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<DeweyAccessToken>> => {
      return _request(
        `/api/auth/user/refresh`,
        "POST",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        false
      )
    },
    registerUser: (
      body: DeweyRegisterUser,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<DeweyUser>> => {
      return _request(
        `/api/auth/register`,
        "POST",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        false
      )
    },
    rescanLibrary: (
      { libraryId }: { libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Empty>> => {
      return _request(
        `/api/libraries/${libraryId}/rescan`,
        "POST",
        "text",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    retrieveJwks: (headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<DeweyJWKs>> => {
      return _request(
        `/api/auth/jwks.json`,
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        false
      )
    },
    searchAuthorMetadata: (
      { query, libraryId }: { query: string; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<MetadataAuthor>>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/metadata/author/search`, { query }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    searchBookMetadata: (
      {
        authorName,
        keywords,
        language,
        narrator,
        pageSize,
        query,
        libraryId,
      }: {
        authorName?: string | null
        keywords?: string | null
        language?: MetadataLanguage | null
        narrator?: string | null
        pageSize?: MetadataSearchCount | null
        query: string
        libraryId: UUID
      },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<MetadataBook>>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/metadata/book/search`, {
          authorName,
          keywords,
          language,
          narrator,
          pageSize,
          query,
        }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    searchInAllLibraries: (
      {
        author,
        book,
        query,
        series,
      }: { author?: string | null; book?: string | null; query?: string | null; series?: string | null },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<LibrarySearchResult>> => {
      return _request(
        _createUrl(`/api/libraries/search`, { author, book, query, series }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    searchSeriesMetadata: (
      { authorName, query, libraryId }: { authorName?: string | null; query: string; libraryId: UUID },
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Array<MetadataSeries>>> => {
      return _request(
        _createUrl(`/api/libraries/${libraryId}/metadata/series/search`, { authorName, query }),
        "GET",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        undefined,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    setBookDismissed: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      body: SetDismissed,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Empty>> => {
      return _request(
        `/api/libraries/${libraryId}/books/${id}/progress/dismissed`,
        "PUT",
        "text",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    setBookFinished: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      body: SetFinished,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Empty>> => {
      return _request(
        `/api/libraries/${libraryId}/books/${id}/progress/finished`,
        "PUT",
        "text",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    setBookProgress: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      body: ProgressUpdate,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Empty>> => {
      return _request(
        `/api/libraries/${libraryId}/books/${id}/progress`,
        "PUT",
        "text",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    updateAuthor: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      body: AuthorUpdate,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Author>> => {
      return _request(
        `/api/libraries/${libraryId}/authors/${id}`,
        "PATCH",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    updateBook: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      body: BookUpdate,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Book>> => {
      return _request(
        `/api/libraries/${libraryId}/books/${id}`,
        "PATCH",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    updateLibrary: (
      { libraryId }: { libraryId: UUID },
      body: PartialUpdateLibrary,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Library>> => {
      return _request(
        `/api/libraries/${libraryId}`,
        "PATCH",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    updatePassword: (
      { id }: { id: UUID },
      body: DeweyChangePassword,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Empty>> => {
      return _request(
        `/api/auth/user/${id}/password`,
        "POST",
        "text",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    updatePermissions: (
      { id }: { id: UUID },
      body: DeweyModifyPermissions<UpdateUserPermissions>,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<DeweyUser>> => {
      return _request(
        `/api/auth/user/${id}/permissions`,
        "PUT",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    updateSeries: (
      { id, libraryId }: { id: UUID; libraryId: UUID },
      body: SeriesUpdate,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<Series>> => {
      return _request(
        `/api/libraries/${libraryId}/series/${id}`,
        "PATCH",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
    updateUsername: (
      { id }: { id: UUID },
      body: DeweyRenameUser,
      headers: HeadersInit = {},
      interceptors: ApiInterceptor[] = []
    ): Promise<ApiResponse<DeweyUser>> => {
      return _request(
        `/api/auth/user/${id}/username`,
        "POST",
        "json",
        _mergeHeaders(defaultHeadersImpl, headers),
        body,
        [...defaultInterceptors, ...interceptors],
        executor,
        true
      )
    },
  } as const
}
