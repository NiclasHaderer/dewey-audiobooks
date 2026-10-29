import type { UpdateLibraryPermissions } from "./update-library-permissions"

export interface UpdateUserPermissions {
  isAdmin: boolean
  libraries: Array<UpdateLibraryPermissions>
}
