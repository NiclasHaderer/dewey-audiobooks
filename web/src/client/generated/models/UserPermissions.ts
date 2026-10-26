import type { LibraryPermissions } from "./LibraryPermissions"

export interface UserPermissions {
  isAdmin: boolean
  libraries: Array<LibraryPermissions>
}
