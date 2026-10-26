import type { BookField } from "./BookField"
import type { Chapter } from "./Chapter"
import type { MetadataLanguage } from "./MetadataLanguage"
import type { NamedId } from "./NamedId"
import type { PlayStatus } from "./PlayStatus"
import type { TitledId } from "./TitledId"
import type { Track } from "./Track"
import type { UUID } from "./UUID"

export interface BookDetailed {
  authors: Array<NamedId>
  chapters: Array<Chapter>
  coverID: UUID | null
  description: string | null
  durationMs: number
  genres: Array<string>
  id: UUID
  isbn: string | null
  language: MetadataLanguage | null
  libraryId: UUID
  locked: Array<BookField>
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
  tracks: Array<Track>
}
