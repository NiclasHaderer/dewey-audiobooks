import type { LibraryPermissionLevel } from "./library-permission-level"
import type { UUID } from "./uuid"

export interface UpdateLibraryPermissions {
  id: UUID
  permissions: LibraryPermissionLevel
}
