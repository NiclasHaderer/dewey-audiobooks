import type { FileScanner } from "./FileScanner"
import type { MetadataLanguage } from "./MetadataLanguage"
import type { MetadataRegion } from "./MetadataRegion"
import type { NamedMetadataAgent } from "./NamedMetadataAgent"
import type { UUID } from "./UUID"

export interface Library {
  bookCount: number
  combineFileScannerFields: boolean
  combineMetadataAgentFields: boolean
  fileScanners: Array<FileScanner>
  folders: Array<string>
  icon: string | null
  id: UUID
  language: MetadataLanguage
  metadataAgents: Array<NamedMetadataAgent>
  name: string
  preferEmbeddedMetadata: boolean
  region: MetadataRegion
}
