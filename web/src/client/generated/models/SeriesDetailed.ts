import type { Book } from "./Book"
import type { NamedId } from "./NamedId"
import type { SeriesField } from "./SeriesField"
import type { UUID } from "./UUID"
import type { YearRange } from "./YearRange"

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
