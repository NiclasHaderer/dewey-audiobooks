import type { LibraryPermissionLevel } from "./LibraryPermissionLevel"
import type { UUID } from "./UUID"

export interface AccessTokenLibrary {
  id: UUID
  permissions: LibraryPermissionLevel
}
