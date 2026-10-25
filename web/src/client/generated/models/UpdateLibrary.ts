import type { FileScanner } from "./FileScanner"
import type { MetadataLanguage } from "./MetadataLanguage"
import type { MetadataRegion } from "./MetadataRegion"
import type { NamedMetadataAgent } from "./NamedMetadataAgent"

export interface UpdateLibrary {
  combineFileScannerFields: boolean
  combineMetadataAgentFields: boolean
  fileScanners: Array<FileScanner>
  folders: Array<string>
  icon: string | null
  language: MetadataLanguage
  metadataAgents: Array<NamedMetadataAgent>
  name: string
  preferEmbeddedMetadata: boolean
  region: MetadataRegion
}
