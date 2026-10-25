import type { LibraryPermissionLevel } from "./LibraryPermissionLevel"
import type { UUID } from "./UUID"

export interface LibraryPermissions {
  id: UUID
  name: string
  permissions: LibraryPermissionLevel
}
