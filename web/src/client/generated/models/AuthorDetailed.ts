import type { AuthorField } from "./AuthorField"
import type { Book } from "./Book"
import type { Series } from "./Series"
import type { UUID } from "./UUID"

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
