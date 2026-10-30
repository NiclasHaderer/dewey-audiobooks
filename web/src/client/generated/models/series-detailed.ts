import type { Book } from "./book"
import type { NamedId } from "./named-id"
import type { SeriesField } from "./series-field"
import type { UUID } from "./uuid"
import type { YearRange } from "./year-range"

export interface SeriesDetailed {
  authors: Array<NamedId>
  bookCoverIDs: Array<UUID>
  books: Array<Book>
  coverID: UUID | null
  description: string | null
  genres: Array<string>
  id: UUID
  libraryId: UUID
  locked: Array<SeriesField>
  narrators: Array<string>
  primaryWorks: number | null
  provider: string | null
  providerID: string | null
  title: string
  totalBooks: number | null
  yearRange: YearRange | null
}
