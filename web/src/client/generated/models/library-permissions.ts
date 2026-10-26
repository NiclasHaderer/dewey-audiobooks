import type { LibraryPermissionLevel } from "./library-permission-level"
import type { UUID } from "./uuid"

export interface LibraryPermissions {
  id: UUID
  name: string
  permissions: LibraryPermissionLevel
}
