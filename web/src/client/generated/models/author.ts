import type { UUID } from "./uuid"

export interface Author {
  biography: string | null
  birthDate: string | null
  bornIn: string | null
  deathDate: string | null
  id: UUID
  imageID: UUID | null
  libraryId: UUID
  name: string
  provider: string | null
  providerID: string | null
  website: string | null
}
