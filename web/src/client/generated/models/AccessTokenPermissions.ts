import type { AccessTokenLibrary } from "./AccessTokenLibrary"

export interface AccessTokenPermissions {
  isAdmin: boolean
  libraries: Array<AccessTokenLibrary> | null
}
