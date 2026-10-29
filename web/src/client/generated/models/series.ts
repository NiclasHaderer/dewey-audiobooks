import type { NamedId } from "./named-id"
import type { UUID } from "./uuid"

export interface Series {
  authors: Array<NamedId>
  bookCoverIDs: Array<UUID>
  coverID: UUID | null
  description: string | null
  genres: Array<string>
  id: UUID
  libraryId: UUID
  primaryWorks: number | null
  provider: string | null
  providerID: string | null
  title: string
  totalBooks: number | null
}
