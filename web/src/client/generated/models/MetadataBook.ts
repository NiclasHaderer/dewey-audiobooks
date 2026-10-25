import type { MetadataAgentID } from "./MetadataAgentID"
import type { MetadataBookSeries } from "./MetadataBookSeries"
import type { MetadataLanguage } from "./MetadataLanguage"
import type { MetadataSearchAuthor } from "./MetadataSearchAuthor"

export interface MetadataBook {
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
