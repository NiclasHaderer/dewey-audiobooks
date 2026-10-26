import type { MetadataAgentID } from "./metadata-agent-id"

export interface MetadataSearchAuthor {
  id: MetadataAgentID
  link: string
  name: string | null
}
