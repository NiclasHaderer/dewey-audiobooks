import type { LibraryPermissionLevel } from "./LibraryPermissionLevel"
import type { UUID } from "./UUID"

export interface UpdateLibraryPermissions {
  id: UUID
  permissions: LibraryPermissionLevel
}
