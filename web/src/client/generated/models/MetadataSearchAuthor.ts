import type { MetadataAgentID } from "./MetadataAgentID"

export interface MetadataSearchAuthor {
  id: MetadataAgentID
  link: string
  name: string | null
}
