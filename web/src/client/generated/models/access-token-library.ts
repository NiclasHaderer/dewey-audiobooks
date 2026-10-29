import type { LibraryPermissionLevel } from "./library-permission-level"
import type { UUID } from "./uuid"

export interface AccessTokenLibrary {
  id: UUID
  permissions: LibraryPermissionLevel
}
