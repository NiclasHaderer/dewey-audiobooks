import type { FileScanner } from "./file-scanner"
import type { MetadataLanguage } from "./metadata-language"
import type { MetadataRegion } from "./metadata-region"
import type { NamedMetadataAgent } from "./named-metadata-agent"
import type { UUID } from "./uuid"

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
