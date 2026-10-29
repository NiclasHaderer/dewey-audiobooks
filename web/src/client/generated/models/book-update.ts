import type { BookField } from "./book-field"
import type { ChapterMark } from "./chapter-mark"
import type { MetadataLanguage } from "./metadata-language"
import type { UUID } from "./uuid"

export interface BookUpdate {
  asin?: string | null
  authors?: Array<UUID>
  chapters?: Array<ChapterMark>
  cover?: string | null
  description?: string | null
  genres?: Array<string> | null
  isbn?: string | null
  language?: MetadataLanguage | null
  narrators?: Array<string> | null
  provider?: string | null
  providerID?: string | null
  providerRating?: number | null
  publisher?: string | null
  releaseDate?: string | null
  series?: Array<UUID> | null
  title?: string
  unlock?: Array<BookField>
}
