import type { MetadataAgentID } from "./metadata-agent-id"

export interface MetadataBookSeries {
  id: MetadataAgentID
  index: number | null
  link: string
  title: string | null
}
