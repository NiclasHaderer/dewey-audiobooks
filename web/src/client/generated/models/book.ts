import type { MetadataLanguage } from "./metadata-language"
import type { NamedId } from "./named-id"
import type { PlayStatus } from "./play-status"
import type { TitledId } from "./titled-id"
import type { UUID } from "./uuid"

export interface Book {
  asin: string | null
  authors: Array<NamedId>
  coverID: UUID | null
  description: string | null
  durationMs: number
  genres: Array<string>
  id: UUID
  isbn: string | null
  language: MetadataLanguage | null
  libraryId: UUID
  narrators: Array<string>
  positionMs: number
  provider: string | null
  providerID: string | null
  providerRating: number | null
  publisher: string | null
  releaseDate: string | null
  series: Array<TitledId>
  status: PlayStatus
  title: string
}
