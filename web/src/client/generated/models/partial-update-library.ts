import type { FileScanner } from "./file-scanner"
import type { MetadataLanguage } from "./metadata-language"
import type { MetadataRegion } from "./metadata-region"
import type { NamedMetadataAgent } from "./named-metadata-agent"

export interface PartialUpdateLibrary {
  combineFileScannerFields?: boolean
  combineMetadataAgentFields?: boolean
  fileScanners?: Array<FileScanner>
  folders?: Array<string>
  icon?: string | null
  language?: MetadataLanguage
  metadataAgents?: Array<NamedMetadataAgent>
  name?: string
  preferEmbeddedMetadata?: boolean
  region?: MetadataRegion
}
