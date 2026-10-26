import type { MetadataAgentID } from "./metadata-agent-id"
import type { MetadataBookSeries } from "./metadata-book-series"
import type { MetadataLanguage } from "./metadata-language"
import type { MetadataSearchAuthor } from "./metadata-search-author"

export interface MetadataBook {
  asin: string | null
  authors: Array<MetadataSearchAuthor> | null
  coverURL: string | null
  description: string | null
  id: MetadataAgentID
  isbn: string | null
  language: MetadataLanguage | null
  link: string | null
  narrators: Array<string>
  providerRating: number | null
  publisher: string | null
  releaseDate: string | null
  series: Array<MetadataBookSeries>
  title: string | null
}
