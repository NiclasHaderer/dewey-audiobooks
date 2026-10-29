import type { MetadataRegion } from "./metadata-region"

export interface MetadataAgentApiModel {
  name: string
  supportedRegions: Array<MetadataRegion>
}
