import type { MetadataAgentID } from "./MetadataAgentID"

export interface MetadataBookSeries {
  id: MetadataAgentID
  index: number | null
  link: string
  title: string | null
}
