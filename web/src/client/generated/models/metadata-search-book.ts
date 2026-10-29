import type { MetadataAgentID } from "./metadata-agent-id"
import type { MetadataBookSeries } from "./metadata-book-series"
import type { MetadataLanguage } from "./metadata-language"
import type { MetadataSearchAuthor } from "./metadata-search-author"

export interface MetadataSearchBook {
  authors: Array<MetadataSearchAuthor> | null
  coverURL: string | null
  id: MetadataAgentID
  language: MetadataLanguage | null
  link: string | null
  narrators: Array<string>
  releaseDate: string | null
  series: Array<MetadataBookSeries>
  title: string | null
}
