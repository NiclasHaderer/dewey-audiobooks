import type { AuthorField } from "./author-field"
import type { Book } from "./book"
import type { Series } from "./series"
import type { UUID } from "./uuid"

export interface AuthorDetailed {
  biography: string | null
  birthDate: string | null
  books: Array<Book>
  bornIn: string | null
  deathDate: string | null
  id: UUID
  imageID: UUID | null
  libraryId: UUID
  locked: Array<AuthorField>
  name: string
  provider: string | null
  providerID: string | null
  series: Array<Series>
  website: string | null
}
