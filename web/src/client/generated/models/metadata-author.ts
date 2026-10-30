import type { MetadataAgentID } from "./metadata-agent-id"

export interface MetadataAuthor {
  biography: string | null
  birthDate: string | null
  bornIn: string | null
  deathDate: string | null
  id: MetadataAgentID
  imageURL: string | null
  link: string
  name: string | null
  website: string | null
}
