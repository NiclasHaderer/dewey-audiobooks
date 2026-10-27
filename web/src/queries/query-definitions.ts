import { queryOptions } from "@tanstack/react-query"
import webLicensesUrl from "@dewey/assets/third-party-licenses.json?url"
import { Api, isNetworkError, Order, PaginatedResponse, ThirdPartyLicense, UUID } from "@dewey/client"
import { cachedLibrarySearch } from "./query-cache"

export type Resource = "books" | "series" | "authors"
export type NameResource = "narrators" | "genres"
export type LibraryResource = Resource | NameResource

export type Identifiable = { id: UUID }

export type ListFn<T> = (params: {
  libraryId: UUID
  limit?: number
  offset?: number
  order?: Order
  showInvisible?: boolean
}) => Promise<PaginatedResponse<T>>

const PAGE_SIZE = 30
const NAME_PAGE_SIZE = 50

const libraryScope = (libraryId: UUID) => ["library", libraryId] as const

const listAllPages = async <T>(listFn: ListFn<T>, libraryId: UUID, showInvisible?: boolean): Promise<T[]> => {
  const pageSize = 500
  const first = await listFn({ libraryId, limit: pageSize, offset: 0, order: "ASC", showInvisible })
  if (first.items.length === 0 || first.items.length >= first.total) return first.items

  const offsets: number[] = []
  for (let offset = first.items.length; offset < first.total; offset += pageSize) offsets.push(offset)
  const rest = await Promise.all(
    offsets.map(offset => listFn({ libraryId, limit: pageSize, offset, order: "ASC", showInvisible }))
  )
  return [first, ...rest].flatMap(page => page.items)
}

const pagedList = <T>(resource: LibraryResource, listFn: ListFn<T>, pageSize: number) => {
  const scope = (libraryId: UUID) => [...libraryScope(libraryId), resource] as const
  const lists = (libraryId: UUID) => [...scope(libraryId), "list"] as const
  return {
    resource,
    pageSize,
    scope,
    lists,
    page: (libraryId: UUID, order: Order, offset: number) =>
      queryOptions({
        queryKey: [...lists(libraryId), { order }, offset] as const,
        queryFn: () => listFn({ libraryId, limit: pageSize, offset, order }),
        meta: { action: `load ${resource}` },
      }),
    all: (libraryId: UUID) =>
      queryOptions({
        queryKey: [...scope(libraryId), "all"] as const,
        queryFn: () => listAllPages(listFn, libraryId, true),
        meta: { action: `load ${resource}` },
      }),
  }
}

const entity = <T extends Identifiable, D extends T>(
  resource: Resource,
  singular: string,
  listFn: ListFn<T>,
  getFn: (params: { libraryId: UUID; id: UUID }) => Promise<D>
) => {
  const list = pagedList(resource, listFn, PAGE_SIZE)
  const details = (libraryId: UUID) => [...list.scope(libraryId), "detail"] as const
  return {
    ...list,
    singular,
    details,
    detail: (libraryId: UUID, id: UUID) =>
      queryOptions({
        queryKey: [...details(libraryId), id] as const,
        queryFn: () => getFn({ libraryId, id }),
        meta: { action: `load the ${singular}` },
      }),
  }
}

const named = <T, D>(
  resource: NameResource,
  singular: string,
  listFn: ListFn<T>,
  getFn: (params: { libraryId: UUID; name: string }) => Promise<D>
) => ({
  ...pagedList(resource, listFn, NAME_PAGE_SIZE),
  singular,
  detail: (libraryId: UUID, name: string) =>
    queryOptions({
      queryKey: [...libraryScope(libraryId), resource, "detail", name] as const,
      queryFn: () => getFn({ libraryId, name: encodeURIComponent(name) }),
      meta: { action: `load the ${singular}` },
    }),
})

export type PagedQueries<T> = ReturnType<typeof pagedList<T>>
export type EntityQueries<T extends Identifiable, D extends T> = ReturnType<typeof entity<T, D>>

const metadataSearchKey = (resource: Resource, libraryId: UUID, params: object) =>
  ["metadata-search", resource, libraryId, params] as const

export const queries = {
  currentUser: queryOptions({
    queryKey: ["current-user"] as const,
    queryFn: () => Api.getCurrentUser(),
    meta: { action: "load your profile" },
    staleTime: Infinity,
  }),
  users: queryOptions({
    queryKey: ["users"] as const,
    queryFn: () => Api.listUsers(),
    meta: { action: "load users" },
  }),
  accessTokens: queryOptions({
    queryKey: ["access-tokens"] as const,
    queryFn: () => Api.listAccessTokens(),
    meta: { action: "load your access tokens", persist: false },
  }),
  libraries: queryOptions({
    queryKey: ["libraries"] as const,
    queryFn: () => Api.listLibraries(),
    meta: { action: "load your libraries" },
    staleTime: 5 * 60_000,
  }),
  continueListening: queryOptions({
    queryKey: ["continue-listening"] as const,
    queryFn: () => Api.getContinueListening({}),
    meta: { action: "load continue listening" },
  }),

  metadataAgents: queryOptions({
    queryKey: ["metadata-agents"] as const,
    queryFn: () => Api.listMetadataAgents(),
    meta: { action: "load metadata agents" },
  }),
  fileScanners: queryOptions({
    queryKey: ["file-scanners"] as const,
    queryFn: () => Api.listFileScanners(),
    meta: { action: "load file scanners" },
  }),
  folders: (path: string) =>
    queryOptions({
      queryKey: ["folders", path] as const,
      queryFn: () => Api.listFoldersAtACertainPath({ path }),
      meta: { action: "load folders", persist: false },
    }),

  serverLicenses: queryOptions({
    queryKey: ["licenses", "server"] as const,
    queryFn: () => Api.listThirdPartyLicenses(),
    meta: { action: "load server licenses" },
  }),
  webLicenses: queryOptions({
    queryKey: ["licenses", "web"] as const,
    queryFn: (): Promise<ThirdPartyLicense[]> => fetch(webLicensesUrl).then(response => response.json()),
    meta: { action: "load web licenses" },
    staleTime: Infinity,
  }),

  librarySearches: ["library-search"] as const,
  librarySearch: (q: string) =>
    queryOptions({
      queryKey: ["library-search", q] as const,
      queryFn: async ({ client }) => {
        try {
          return await Api.searchInAllLibraries({ query: q })
        } catch (error) {
          if (isNetworkError(error)) return cachedLibrarySearch(client, q)
          throw error
        }
      },
      meta: { action: "run the search", persist: false },
    }),

  metadataSearch: {
    authors: (libraryId: UUID, params: { query: string }) =>
      queryOptions({
        queryKey: metadataSearchKey("authors", libraryId, params),
        queryFn: () => Api.searchAuthorMetadata({ ...params, libraryId }),
        meta: { action: "search for author metadata", persist: false },
      }),
    books: (libraryId: UUID, params: { query: string; authorName?: string }) =>
      queryOptions({
        queryKey: metadataSearchKey("books", libraryId, params),
        queryFn: () => Api.searchBookMetadata({ ...params, libraryId }),
        meta: { action: "search for book metadata", persist: false },
      }),
    series: (libraryId: UUID, params: { query: string; authorName?: string }) =>
      queryOptions({
        queryKey: metadataSearchKey("series", libraryId, params),
        queryFn: () => Api.searchSeriesMetadata({ ...params, libraryId }),
        meta: { action: "search for series metadata", persist: false },
      }),
  },

  library: libraryScope,
  books: entity("books", "book", Api.listBooks, Api.getBook),
  series: entity("series", "series", Api.listSeries, Api.getSeries),
  authors: entity("authors", "author", Api.listAuthors, Api.getAuthor),
  narrators: named("narrators", "narrator", Api.listNarrators, Api.getNarrator),
  genres: named("genres", "genre", Api.listGenres, Api.getGenre),
}
