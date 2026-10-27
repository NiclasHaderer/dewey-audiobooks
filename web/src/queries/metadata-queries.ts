import { useQuery } from "@tanstack/react-query"
import { UUID } from "@dewey/client"
import { queries } from "./query-definitions"

type Search = { query: string; authorName?: string }

export const useAuthorMetadataSearch = (libraryId: UUID, params: { query: string } | null) =>
  useQuery({ ...queries.metadataSearch.authors(libraryId, params ?? { query: "" }), enabled: params !== null })

export const useBookMetadataSearch = (libraryId: UUID, params: Search | null) =>
  useQuery({ ...queries.metadataSearch.books(libraryId, params ?? { query: "" }), enabled: params !== null })

export const useSeriesMetadataSearch = (libraryId: UUID, params: Search | null) =>
  useQuery({ ...queries.metadataSearch.series(libraryId, params ?? { query: "" }), enabled: params !== null })
