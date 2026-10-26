import type { UpdateLibraryPermissions } from "./UpdateLibraryPermissions"

export interface UpdateUserPermissions {
  isAdmin: boolean
  libraries: Array<UpdateLibraryPermissions>
}
