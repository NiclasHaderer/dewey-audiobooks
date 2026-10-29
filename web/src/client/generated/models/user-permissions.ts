import type { LibraryPermissions } from "./library-permissions"

export interface UserPermissions {
  isAdmin: boolean
  libraries: Array<LibraryPermissions>
}
