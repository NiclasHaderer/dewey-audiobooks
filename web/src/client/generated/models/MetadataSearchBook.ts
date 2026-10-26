import type { MetadataAgentID } from "./MetadataAgentID"
import type { MetadataBookSeries } from "./MetadataBookSeries"
import type { MetadataLanguage } from "./MetadataLanguage"
import type { MetadataSearchAuthor } from "./MetadataSearchAuthor"

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
