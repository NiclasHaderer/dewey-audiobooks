import type { MetadataLanguage } from "./MetadataLanguage"
import type { NamedId } from "./NamedId"
import type { PlayStatus } from "./PlayStatus"
import type { TitledId } from "./TitledId"
import type { UUID } from "./UUID"

export interface Book {
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
