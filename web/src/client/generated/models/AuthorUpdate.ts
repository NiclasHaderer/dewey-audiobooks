import type { AuthorField } from "./AuthorField"
import type { UUID } from "./UUID"

export interface AuthorUpdate {
  biography?: string | null
  birthDate?: string | null
  books?: Array<UUID>
  bornIn?: string | null
  deathDate?: string | null
  image?: string | null
  name?: string
  provider?: string | null
  providerID?: string | null
  unlock?: Array<AuthorField>
  website?: string | null
}
