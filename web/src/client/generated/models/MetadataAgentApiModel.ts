import type { MetadataRegion } from "./MetadataRegion"

export interface MetadataAgentApiModel {
  name: string
  supportedRegions: Array<MetadataRegion>
}
